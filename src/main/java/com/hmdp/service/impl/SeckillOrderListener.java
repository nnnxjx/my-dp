package com.hmdp.service.impl;

import com.hmdp.config.SeckillRabbitConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;

@Component
public class SeckillOrderListener {
    @Resource private IVoucherOrderService voucherOrderService;
    @Resource private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = SeckillRabbitConfig.QUEUE)
    public void receive(VoucherOrder order) {
        voucherOrderService.createVoucherOrder(order);
        // 数据库事务已提交；SADD 幂等，重复投递也能补齐 Redis 的一人一单标记。
        stringRedisTemplate.opsForSet().add(
                SECKILL_ORDER_KEY + order.getVoucherId(), order.getUserId().toString());
    }
}
