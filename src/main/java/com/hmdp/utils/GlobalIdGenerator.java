package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


/**
 * 基于 Redis 的全局 ID 生成器，生成 64 位 long 类型的有序 ID
 * <p>
 * ID 的组成（共 64 bit，符号位恒为 0，保证 ID 为正数）：
 * ┌────────┬──────────────────┬────────────────────────────┐
 * │ 符号位 │    时间戳 31bit   │         序列号 32bit        │
 * │  1bit  │  自定义纪元起的秒数 │  当天内的 Redis 自增值       │
 * └────────┴──────────────────┴────────────────────────────┘
 * 高 32 位 = (当前秒级时间戳 - 自定义纪元时间戳)，可使用约 68 年
 * 低 32 位 = Redis INCR 的序列号，支持每天产生 2^32 个不同 ID
 * <p>
 * 序列号的 Redis key 中拼接了当天日期（global:id:order:20261028），
 * 因此序列号每天都会从 1 重新开始自增，不会长期累积超过 32bit 上限，
 * 同时 key 当天的最终值即为当天的订单总数，可直接用于统计。
 */
@Component
public class GlobalIdGenerator {

    /**
     * 自定义纪元时间，时间戳不使用当前时间，而是与纪元时间的差值，以推迟 31bit 时间戳的耗尽时间
     */
    private static final long EPOCH_TIME = LocalDateTime.of(2024, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);

    /**
     * 序列号 key 中的日期格式：yyyyMMdd
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");



    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成全局唯一 ID
     *
     * @param keyPrefix 业务标识，用于隔离不同业务的序列号，如 "order"
     * @return 64 位的全局 ID
     */
    public Long generateId(String keyPrefix) {
        if (StrUtil.isBlank(keyPrefix)) {
            throw new IllegalArgumentException("业务标识不能为空");
        }
        // 1. 计算高 32 位：当前秒级时间戳与自定义纪元时间的差值
        long seconds = (LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - EPOCH_TIME);

        // 2. 计算低 32 位：Redis 按“业务 + 当天日期”自增，每天从 1 开始
        String today = LocalDate.now().format(DATE_FORMATTER);
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + today);
        if (count == null) {
            throw new IllegalStateException("生成订单 id 失败：Redis 自增无返回值");
        }

        // 3. 时间戳左移 32 位与序列号按位拼接
        return (seconds << 32) | count;
    }
}
