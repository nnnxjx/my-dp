package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

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
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 方案一：互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        // 方案二：逻辑过期解决缓存击穿（缓存需要预热）
//        Shop shop = queryWithLogicalExpire(id);
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. x、y 任意一个为空，无法按距离搜索，直接按类型走数据库分页
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 2. x、y 都存在，计算当前页需要跳过的条数 from 与需取到的末尾条数 to
        int pageSize = SystemConstants.DEFAULT_PAGE_SIZE;
        int from = (current - 1) * pageSize;
        int to = current * pageSize;

        // 3. GEORADIUS：按 typeId 对应的 key，以 (x,y) 为圆心、按距离升序返回前 to 条 member(shopId) + distance
        //    用 GEORADIUS 而非 GEOSEARCH，兼容 Redis 6.2 以下版本（GEOSEARCH 需 6.2+）
        //    GEORADIUS 的 LIMIT 只支持取前 N 条（无偏移），故取前 to 条后在内存中跳过前 from 条实现分页
        //    半径取 5000 公里（远超实际业务范围），相当于返回该类型下全部商铺并按距离排序
        String key = SHOP_GEO_KEY + typeId;
        Circle circle = new Circle(new Point(x, y), new Distance(5000000));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(key, circle,
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .sortAscending()
                                .limit(to));

        // 4. 解析出当前页的 shopId 列表（保持 Redis 返回的距离升序）及各 shopId 对应的距离
        if (results == null || results.getContent().size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> pageList =
                results.getContent().subList(from, Math.min(results.getContent().size(), to));
        List<Long> ids = new ArrayList<>(pageList.size());
        Map<String, Distance> distanceMap = new HashMap<>(pageList.size());
        pageList.forEach(g -> {
            String shopId = g.getContent().getName();
            ids.add(Long.valueOf(shopId));
            distanceMap.put(shopId, g.getDistance());
        });

        // 5. 按 shopId 回查商铺，并依照现有 shopId 顺序重排，逐个填充 distance
        Map<Long, Shop> shopMap = listByIds(ids).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        List<Shop> shops = ids.stream()
                .map(shopMap::get)
                .filter(Objects::nonNull)
                .peek(shop -> shop.setDistance(distanceMap.get(shop.getId().toString()).getValue()))
                .collect(Collectors.toList());

        // 6. 返回当前页商铺列表
        return Result.ok(shops);
    }
}
