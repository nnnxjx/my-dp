package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
   @Resource
    private StringRedisTemplate stringRedisTemplate;
    public Result getList() {
        // 1. 从 redis 查询商铺缓存
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY );
        // 2. 缓存命中，反序列化后直接返回
        if (StrUtil.isNotBlank(shopTypeListJson)) {
            List<ShopType> cacheShopTypeList = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return Result.ok(cacheShopTypeList);
        }
        // 3. 缓存未命中，查询数据库
        List<ShopType> shopTypeList = list();
        // 4. 数据库中不存在，返回错误
        if (shopTypeList == null) {
            return Result.fail("商铺类型列表不存在");
        }
        // 5. 数据库中存在，将商铺数据写入 redis 并设置有效期
        stringRedisTemplate.opsForValue()
                .set(CACHE_SHOP_TYPE_KEY , JSONUtil.toJsonStr(shopTypeList));
        // 6. 返回商铺信息
        return Result.ok(shopTypeList);
    }
}
