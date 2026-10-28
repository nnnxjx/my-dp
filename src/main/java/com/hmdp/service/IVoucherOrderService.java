package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券下单
     *
     * @param voucherId 优惠券id
     * @return 成功返回订单id，失败返回异常原因
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建秒杀订单（需在事务中执行），必须通过代理对象调用才能让 @Transactional 生效
     *
     * @param voucherId 优惠券id
     * @param userId    下单用户id
     * @return 成功返回订单id，失败返回异常原因
     */
    Result createVoucherOrder(Long voucherId, Long userId);
}
