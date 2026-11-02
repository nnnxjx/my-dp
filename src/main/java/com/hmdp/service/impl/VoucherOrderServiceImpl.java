package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.SeckillRabbitConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.GlobalIdGenerator;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = script("lua/seckill.lua");

    @Resource private ISeckillVoucherService seckillVoucherService;
    @Resource private GlobalIdGenerator globalIdGenerator;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private RabbitTemplate rabbitTemplate;

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) return Result.fail("优惠券不存在");
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) return Result.fail("秒杀尚未开始");
        if (now.isAfter(voucher.getEndTime())) return Result.fail("秒杀已经结束");

        Long userId = UserHolder.getUser().getId();
        Long orderId = globalIdGenerator.generateId("order");
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(), orderId.toString());
        if (result == null) return Result.fail("秒杀服务暂不可用");
        if (result == 1) return Result.fail("库存不足");
        if (result == 2) return Result.fail("不可重复下单");

        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        try {
            rabbitTemplate.convertAndSend(SeckillRabbitConfig.EXCHANGE,
                    SeckillRabbitConfig.ROUTING_KEY, order, message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    });
        } catch (Exception e) {
            // 无发布确认时无法判断消息是否到达 broker，保留预扣记录供对账。
            log.error("秒杀消息发送异常，需核对预扣记录，orderId={}", orderId, e);
            return Result.fail("订单处理中，请稍后查询，订单号：" + orderId);
        }
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder order) {
        // 同一消息重复投递时直接返回，数据库唯一索引兜底并发消费。
        if (getById(order.getId()) != null) return;
        if (query().eq("user_id", order.getUserId()).eq("voucher_id", order.getVoucherId()).count() > 0) {
            throw new IllegalStateException("用户已有该优惠券订单，orderId=" + order.getId());
        }
        boolean updated = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId()).gt("stock", 0).update();
        if (!updated) throw new IllegalStateException("数据库库存不足，orderId=" + order.getId());
        LocalDateTime now = LocalDateTime.now();
        order.setPayType(1);
        order.setStatus(1);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        if (!save(order)) throw new IllegalStateException("订单保存失败，orderId=" + order.getId());
    }

}
