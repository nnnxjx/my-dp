package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据 id 查询商铺，优先读取 Redis 缓存，未命中时回源数据库并写缓存
     * @param id 商铺 id
     * @return 成功时 data 为商铺信息，商铺不存在时返回失败
     */
    Result queryById(Long id);

    /**
     * 更新商铺信息，先更新数据库，再原子删除商铺缓存
     * @param shop 商铺数据，必须包含 id
     * @return 处理结果
     */
    Result updateShop(Shop shop);
}
