package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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

    @Resource
    private CacheClient cacheClient;


    @Override
    public Result queryById(Long id) {
        // 方案一：互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        // 方案二：逻辑过期解决缓存击穿（缓存需要预热）
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        // 6. 返回商铺信息
        return Result.ok(shop);
    }

    //逻辑过期解决缓存击穿：委托 CacheClient，缓存本身不过期，逻辑过期后由抢到互斥锁的线程异步重建
    public Shop queryWithLogicalExpire(Long id) {
        return cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    /**
     * 缓存预热 / 手动重建：将商铺数据写入 redis，并设置逻辑过期时间
     *
     * @param expireSeconds 逻辑过期时长（秒）
     */
    public void saveShop2Redis(Long id, long expireSeconds) {
        Shop shop = getById(id);
        if (shop == null) {
            return;
        }
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + id, shop, expireSeconds, TimeUnit.SECONDS);
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
        String lockKey = LOCK_SHOP_KEY + id;
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

    //缓存穿透：委托 CacheClient，用缓存空值解决
    public Shop queryWithPassThrough(Long id) {
        return cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    //尝试获取锁
    private boolean tryGetLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void releaseLock(String key) {
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
