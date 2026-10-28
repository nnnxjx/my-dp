package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.GlobalIdGenerator;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private GlobalIdGenerator globalIdGenerator;

    /**
     * 优惠券秒杀下单
     * 流程：查询优惠券 -> 判断秒杀是否开始/结束 -> 判断库存是否充足 -> 扣减库存 -> 创建订单 -> 返回订单id
     */
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 1. 获取当前登录用户（下单前必须登录）
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        // 2. 查询优惠券（秒杀）信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }

        // 3. 判断秒杀是否开始或结束：未开始或已结束都无法下单
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀已经结束了");
        }

        // 4. 判断库存是否充足
        if (voucher.getStock() <= 0) {
            return Result.fail("库存不足，无法下单");
        }

        // 5. 扣减库存：带 stock > 0 条件更新，防止并发超卖；更新影响 0 行说明已被抢完
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .update();
        if (!success) {
            return Result.fail("库存不足，无法下单");
        }

        // 6. 创建订单：使用全局 ID 生成器生成订单主键
        VoucherOrder order = new VoucherOrder();
        order.setId(globalIdGenerator.generateId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayType(1);   // 支付方式：1-余额支付
        order.setStatus(1);    // 订单状态：1-未支付
        order.setCreateTime(now);
        order.setUpdateTime(now);
        save(order);

        // 7. 返回订单id
        return Result.ok(order.getId());
    }
}
