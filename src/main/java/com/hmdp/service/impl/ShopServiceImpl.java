package com.hmdp.service.impl;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 用于异步重建缓存的线程池
    private static final ExecutorService CACHE_REBUILD_POOL = new ThreadPoolExecutor(
            2, 5, 60L, TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(128),
            ThreadFactoryBuilder.create().setNamePrefix("cache-rebuild-").build(),
            new ThreadPoolExecutor.CallerRunsPolicy());


    @Override
    public Result queryById(Long id) {
        //Shop shop = queryWithMutex(id);
        // 逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        // 6. 返回商铺信息
        return Result.ok(shop);
    }

    /**
     * 逻辑过期解决缓存击穿：缓存永不真正过期，数据里携带逻辑过期时间，
     * 过期后由拿到互斥锁的线程开启独立线程异步重建缓存，其余线程直接返回旧数据
     */
    public Shop queryWithLogicalExpire(Long id) {
        // 1. 从 redis 查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2. 未命中，直接返回空（不会去查数据库，避免缓存击穿）
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 3. 命中，需要判断是否逻辑过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean(redisData.getData().toString(), Shop.class);
        LocalDateTime expire = redisData.getExpire();
        // 4. 未过期，直接返回
        if (expire.isAfter(LocalDateTime.now())) {
            return shop;
        }
        // 5. 已过期，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;

            if (tryGetLock(lockKey)) {
                // 5.1 获取锁成功，开启独立线程异步重建缓存
                CACHE_REBUILD_POOL.submit(() -> {
                    try {
                        // 重建后的缓存逻辑过期时间仍为 CACHE_SHOP_TTL 分钟
                        saveShop2Redis(id, TimeUnit.MINUTES.toSeconds(CACHE_SHOP_TTL));
                    } catch (Exception e){
                        throw new RuntimeException(e);
                    }
                    finally{
                        releaseLock(lockKey);
                    }
                });
            }
        return shop;
    }

    /**
     * 将商铺数据写入 redis，并设置逻辑过期时间
     *
     * @param expireSeconds 逻辑过期时长（秒）
     */
    public void saveShop2Redis(Long id, long expireSeconds) {
        Shop shop = getById(id);
        if (shop == null) {
            return;
        }
        RedisData redisData = new RedisData();
        redisData.setData(JSONUtil.toJsonStr(shop));
        redisData.setExpire(LocalDateTime.now().plusSeconds(expireSeconds));
        // 逻辑过期方案：redis 不设置TTL（缓存永不过期），由 expire 字段判断是否逻辑过期
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    //互斥锁解决缓存击穿问题方案
    public Shop queryWithMutex(Long id) {
        // 1. 从 redis 查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2. 缓存命中，反序列化后直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop cacheShop = JSONUtil.toBean(shopJson, Shop.class);
            return cacheShop;
        }
        if(shopJson != null){
            return null;
        }

        //开始redis缓存的重建
        String lockKey = "lock:shop:" + id;
        try {
            //获取互斥锁
            boolean isLocked = tryGetLock(lockKey);
            if (!isLocked) {
                //  获取锁失败
                Thread.sleep(50);
                return queryWithMutex(id);
            }

                //获取互斥锁成功,要重新检查redis是否有这个数据，双重检查
                // 1. 从 redis 查询商铺缓存
                String sjson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
                // 2. 缓存命中，反序列化后直接返回
                if (StrUtil.isNotBlank(sjson)) {
                    Shop cacheShop = JSONUtil.toBean(sjson, Shop.class);
                    return cacheShop;
                }
                if(shopJson != null){
                    return null;
                }
                // 3. 缓存未命中，根据 id 查询数据库
                Shop shop = getById(id);
                // 4. 数据库中不存在，返回错误
                if (shop == null) {
                    //将空值写入redis
                    stringRedisTemplate.opsForValue()
                            .set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                // 5. 数据库中存在，将商铺数据写入 redis 并设置有效期
                stringRedisTemplate.opsForValue()
                        .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return shop;

        }catch (Exception e){
            throw new RuntimeException(e);
        }finally{
            releaseLock(lockKey);
        }
    }

    //缓存击穿
    public Shop queryWithPassThrough(Long id) {
        // 1. 从 redis 查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2. 缓存命中，反序列化后直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop cacheShop = JSONUtil.toBean(shopJson, Shop.class);
            return cacheShop;
        }
        if(shopJson != null){
            return null;
        }
        // 3. 缓存未命中，根据 id 查询数据库
        Shop shop = getById(id);
        // 4. 数据库中不存在，返回错误
        if (shop == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue()
                    .set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 5. 数据库中存在，将商铺数据写入 redis 并设置有效期
        stringRedisTemplate.opsForValue()
                .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 6. 返回商铺信息
        return shop;
    }
    //尝试获取锁
    public boolean tryGetLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    public void releaseLock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1. 先更新数据库
        updateById(shop);
        // 2. 删除 redis 缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
