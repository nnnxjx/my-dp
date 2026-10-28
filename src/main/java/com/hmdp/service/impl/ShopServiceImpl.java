package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

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


    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        // 6. 返回商铺信息
        return Result.ok(shop);
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
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
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
