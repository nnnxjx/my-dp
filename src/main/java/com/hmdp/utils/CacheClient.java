package com.hmdp.utils;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * 基于 StringRedisTemplate 封装的缓存工具类
 * 1、set：将任意 Java 对象序列化为 json 存入 string 类型的 key，并设置 TTL 过期时间
 * 2、setWithLogicalExpire：将任意 Java 对象序列化为 json 存入 string 类型的 key，并设置逻辑过期时间，用于处理缓存击穿
 * 3、queryWithPassThrough：根据 key 查询缓存并反序列化为指定类型，利用缓存空值解决缓存穿透
 * 4、queryWithLogicalExpire：根据 key 查询缓存并反序列化为指定类型，利用逻辑过期解决缓存击穿
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    // 用于异步重建缓存的线程池
    private static final ExecutorService CACHE_REBUILD_POOL = new ThreadPoolExecutor(
            2, 5, 60L, TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(128),
            ThreadFactoryBuilder.create().setNamePrefix("cache-rebuild-").build(),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 方法1：将任意 Java 对象序列化为 json 并存储在 string 类型的 key 中，并且可以设置 TTL 过期时间
     *
     * @param key   缓存 key
     * @param value 缓存的 Java 对象
     * @param time  过期时长
     * @param unit  过期时长单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 方法2：将任意 Java 对象序列化为 json 并存储在 string 类型的 key 中，并且可以设置逻辑过期时间，用于处理缓存击穿问题。
     * 存入的数据结构为 {data:业务数据，expire:逻辑过期时间}，redis 本身不设置 TTL，避免热点 key 同时失效
     *
     * @param key   缓存 key
     * @param value 缓存的 Java 对象
     * @param time  逻辑过期时长
     * @param unit  逻辑过期时长单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpire(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 方法3：根据指定的 key 查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题。
     * 缓存未命中时查询数据库，数据库中不存在则向 redis 写入空值，避免同一不存在的 key 反复请求数据库
     *
     * @param keyPrefix   缓存 key 前缀
     * @param id          业务 id
     * @param type        缓存数据的类型
     * @param dbFallback  缓存未命中时查询数据库的函数
     * @param time        缓存的 TTL 过期时长
     * @param unit        过期时长单位
     * @return 缓存或数据库中的数据，不存在时返回 null
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从 redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 命中且非空，反序列化后直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 3. 命中但值为空字符串，说明是缓存的空值，直接返回 null
        if (json != null) {
            return null;
        }
        // 4. 未命中，查询数据库
        R r = dbFallback.apply(id);
        // 5. 数据库中不存在，将空值写入 redis 并设置较短的过期时间
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6. 数据库中存在，写入缓存并设置 TTL，返回结果
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 方法4：根据指定的 key 查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题。
     * 缓存一定不过期，发现逻辑过期后由拿到互斥锁的线程开启独立线程异步重建缓存，
     * 当前线程与其它线程都直接返回旧数据，保证用户不阻塞
     *
     * @param keyPrefix   缓存 key 前缀
     * @param id          业务 id
     * @param type        缓存数据的类型
     * @param dbFallback  异步重建缓存时查询数据库的函数
     * @param time        重建后缓存的逻辑过期时长
     * @param unit        逻辑过期时长单位
     * @return 缓存中的数据，未命中（缓存尚未预热）时返回 null
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从 redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 未命中，直接返回空，不请求数据库
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3. 命中，判断是否逻辑过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 兼容非逻辑过期格式的数据（如普通 shop json），视为需要重建的无效缓存
        if (redisData.getExpire() == null || redisData.getData() == null) {
            return null;
        }
        R r = JSONUtil.toBean(redisData.getData().toString(), type);
        LocalDateTime expire = redisData.getExpire();
        // 4. 未过期，直接返回
        if (expire.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 5. 已过期，尝试获取互斥锁（用完整 key 作为锁，避免不同业务相同 id 相互干扰）
        String lockKey = "lock:" + key;
        try {
            if (tryGetLock(lockKey)) {
                // 5.1 获取锁成功，开启独立线程异步重建缓存
                CACHE_REBUILD_POOL.submit(() -> {
                    try {
                        R newData = dbFallback.apply(id);
                        if (newData != null) {
                            this.setWithLogicalExpire(key, newData, time, unit);
                        }
                    } finally {
                        releaseLock(lockKey);
                    }
                });
            }
            // 6. 获取锁失败或刚提交完异步任务，都先返回旧数据
        } catch (Exception e) {
            // 提交异步任务失败时兜底：释放锁避免锁泄漏，仍然返回旧数据，不影响用户访问
            releaseLock(lockKey);
            log.error("异步重建缓存失败，key：{}", key, e);
        }
        return r;
    }

    // 尝试获取互斥锁
    private boolean tryGetLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放互斥锁
    private void releaseLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
