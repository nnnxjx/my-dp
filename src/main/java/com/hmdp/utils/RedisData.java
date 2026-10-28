package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    // 数据的逻辑过期时间
    private LocalDateTime expire;
    // 缓存的业务数据
    private Object data;
}
