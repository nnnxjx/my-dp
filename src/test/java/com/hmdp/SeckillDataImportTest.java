package com.hmdp;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 手动执行一次：从 MySQL 重建秒杀库存与一人一单标记。
 * 执行期间必须停止所有下单服务，避免覆盖正在进行的 Redis 预扣。
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class SeckillDataImportTest {
    @Resource private ISeckillVoucherService seckillVoucherService;
    @Resource private IVoucherOrderService voucherOrderService;
    @Resource private StringRedisTemplate redis;

    @Test
    void loadSeckillData() {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        assertFalse(vouchers.isEmpty(), "MySQL 中没有秒杀券，未导入任何数据");

        // 先检查全部键；默认拒绝覆盖，防止在已有预扣记录时造成超卖。
        boolean overwrite = Boolean.getBoolean("seckill.import.overwrite");
        if (!overwrite) {
            for (SeckillVoucher voucher : vouchers) {
                Long id = voucher.getVoucherId();
                if (Boolean.TRUE.equals(redis.hasKey(SECKILL_STOCK_KEY + id))
                        || Boolean.TRUE.equals(redis.hasKey(SECKILL_ORDER_KEY + id))) {
                    throw new IllegalStateException("Redis 已有秒杀数据，拒绝覆盖。voucherId=" + id
                            + "；确认服务已停止且 MySQL 数据准确后，可设置 -Dseckill.import.overwrite=true");
                }
            }
        }

        for (SeckillVoucher voucher : vouchers) {
            Long id = voucher.getVoucherId();
            if (voucher.getStock() == null || voucher.getStock() < 0) {
                throw new IllegalStateException("MySQL 库存无效，voucherId=" + id);
            }
            List<VoucherOrder> orders = voucherOrderService.query()
                    .select("user_id").eq("voucher_id", id).list();
            Set<String> userIds = orders.stream()
                    .map(order -> order.getUserId().toString())
                    .collect(Collectors.toSet());

            String stockKey = SECKILL_STOCK_KEY + id;
            String orderKey = SECKILL_ORDER_KEY + id;
            redis.opsForValue().set(stockKey, voucher.getStock().toString());
            redis.delete(orderKey);
            if (!userIds.isEmpty()) {
                redis.opsForSet().add(orderKey, userIds.toArray(new String[0]));
            }

            assertEquals(voucher.getStock().toString(), redis.opsForValue().get(stockKey));
            assertEquals((long) userIds.size(), redis.opsForSet().size(orderKey));
            System.out.printf("导入秒杀券 %d：剩余库存=%d，已下单用户=%d%n",
                    id, voucher.getStock(), userIds.size());
        }
    }
}
