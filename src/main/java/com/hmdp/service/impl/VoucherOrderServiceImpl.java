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
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.LOCK_ORDER_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private GlobalIdGenerator globalIdGenerator;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_REDIS_SCRIPT;

    /**
     * 优惠券订单阻塞队列
     */
    private static final BlockingQueue<VoucherOrder> VOUCHER_ORDER_QUEUE=new ArrayBlockingQueue<>(1024*1024);
    /**
     * 线程池
     *
     */
    private static final ExecutorService es= Executors.newSingleThreadExecutor();
    /**
     * 任务
     */
    private class Task implements Runnable{
        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取阻塞队列中的订单信息
                    VoucherOrder voucherOrder = VOUCHER_ORDER_QUEUE.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("新增订单异常", e);
                }
            }
        }
    }

    private IVoucherOrderService proxy;

    @PostConstruct
    public void init() {
        es.submit(new Task());
    }


    static {
        SECKILL_REDIS_SCRIPT = new DefaultRedisScript<>();
        SECKILL_REDIS_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_REDIS_SCRIPT.setResultType(Long.class);
    }
    /**
     * 优惠券秒杀下单
     * 流程：查询优惠券 -> 判断秒杀是否开始/结束 -> 判断库存是否充足 -> 扣减库存 -> 创建订单 -> 返回订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.执行lua脚本确定是否有下单资格
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(SECKILL_REDIS_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        int res = result.intValue();
        //2.判断结果是否为0
        if(res!=0){
            return res==1?Result.fail("库存不足"):Result.fail("不可以重复下单");
        }

        //3.将voucherid,userId,orderId塞入阻塞队列BlockQueue中
        VoucherOrder order = new VoucherOrder();
        order.setId(globalIdGenerator.generateId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        //4.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        VOUCHER_ORDER_QUEUE.add(order);
        //5.返回订单id
        return Result.ok(order.getId());
    }
/**
    @Override
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


        // 5. 获取分布式互斥锁：锁名 order:{userId}{voucherId}，保证同一用户抢同一张秒杀券时才互斥
        String lockName = "order:" + userId + voucherId;
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, lockName);
        if (!lock.tryLock(LOCK_ORDER_TTL)) {
            // 获取锁失败，说明同一用户正在下单，直接返回，不再重试
            return Result.fail("请勿重复下单，请稍后再试");
        }
        try {
            // 获取当前类的代理对象，通过代理调用 createVoucherOrder，使 @Transactional 生效，
            // 保证“一人一单”的查询与写入在同一事务内，且在锁释放前提交
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId);
        } finally {
            lock.unlock();
        }
    }
*/



    @Transactional
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        try {
            proxy.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("处理订单异常", e);
        }

    }
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();

        // 5. 扣减库存：带 stock > 0 条件更新，防止并发超卖；更新影响 0 行说明已被抢完
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            //当前查询的数据已经被别的线程所修改，并不安全，重试
            log.error("库存不足，无法下单");
        }

        // 6. 创建订单：使用全局 ID 生成器生成订单主键
        LocalDateTime now = LocalDateTime.now();
        voucherOrder.setPayType(1);   // 支付方式：1-余额支付
        voucherOrder.setStatus(1);    // 订单状态：1-未支付
        voucherOrder.setCreateTime(now);

        voucherOrder.setUpdateTime(now);
        save(voucherOrder);
    }


    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId,Long userId) {

        //判断是否一人一单
        Long count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已购买过该优惠券");
        }

        // 5. 扣减库存：带 stock > 0 条件更新，防止并发超卖；更新影响 0 行说明已被抢完
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            //当前查询的数据已经被别的线程所修改，并不安全，重试
            return Result.fail("库存不足，无法下单");
        }

        // 6. 创建订单：使用全局 ID 生成器生成订单主键
        VoucherOrder order = new VoucherOrder();
        LocalDateTime now = LocalDateTime.now();
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
