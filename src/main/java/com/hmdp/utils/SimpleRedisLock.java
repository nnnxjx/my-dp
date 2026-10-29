package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 简单的分布式互斥锁工具类（基于 Redis）
 * <p>
 * 加锁：setnx（SETNX key value + EXPIRE），使用 setIfAbsent 携带过期时间，原子完成，避免死锁
 * 解锁：delete（DEL key）
 * <p>
 * 实例通过构造函数传入业务名称 name，锁的真实 key 为 {@code lock:{name}}，
 * 不同的 name 之间互不影响，可用于隔离不同业务或不同资源的互斥。
 * <p>
 * 注意：本类为最简实现，解锁时不校验锁的持有者，极端情况下（业务执行时间超过过期时间导致锁被他人获取）
 * 可能误删他人的锁。若需严格安全，应改用“value 存唯一标识 + Lua 脚本判断后再删”的方案。
 */
public class SimpleRedisLock {

    /**
     * 锁 key 的统一前缀，便于在 Redis 中区分锁与普通缓存
     */
    private static final String KEY_PREFIX = "lock:";

    private final String ID_PREFIX = UUID.randomUUID().toString(true);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 业务名称，代表要互斥的资源，如 "order:10012"
     */
    private final String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 尝试获取锁
     *
     * @param expireSeconds 锁的过期时间（秒），到期自动释放，防止持有者宕机造成死锁
     * @return 获取成功返回 true，锁已被占用返回 false
     */
    public boolean tryLock(long expireSeconds) {
        //集群模式下也可以保证独特的线程标识
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, expireSeconds, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    /**
     * 释放锁
     */
    public void unlock() {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String currentValue = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(currentValue)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
