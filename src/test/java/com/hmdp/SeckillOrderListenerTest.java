package com.hmdp;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.SeckillOrderListener;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

class SeckillOrderListenerTest {
    @Test
    void recordsUserAfterOrderIsCreated() {
        IVoucherOrderService service = mock(IVoucherOrderService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(sets);
        SeckillOrderListener listener = new SeckillOrderListener();
        ReflectionTestUtils.setField(listener, "voucherOrderService", service);
        ReflectionTestUtils.setField(listener, "stringRedisTemplate", redis);
        VoucherOrder order = new VoucherOrder().setId(1L).setVoucherId(2L).setUserId(3L);

        listener.receive(order);

        org.mockito.InOrder inOrder = inOrder(service, sets);
        inOrder.verify(service).createVoucherOrder(order);
        inOrder.verify(sets).add("seckill:order:2", "3");
    }

    @Test
    void doesNotRecordUserWhenOrderCreationFails() {
        IVoucherOrderService service = mock(IVoucherOrderService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SeckillOrderListener listener = new SeckillOrderListener();
        ReflectionTestUtils.setField(listener, "voucherOrderService", service);
        ReflectionTestUtils.setField(listener, "stringRedisTemplate", redis);
        VoucherOrder order = new VoucherOrder().setId(1L).setVoucherId(2L).setUserId(3L);
        doThrow(new IllegalStateException("database failed")).when(service).createVoucherOrder(order);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> listener.receive(order));
        verifyNoInteractions(redis);
    }
}
