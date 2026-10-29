package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置，提供单例 {@link RedissonClient}
 * <p>
 * 连接信息复用 application.yaml 中的 spring.redis.host / spring.redis.port，
 * 与 StringRedisTemplate（Lettuce）指向同一个 Redis，避免两处维护地址。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 单机模式；集群/哨兵部署时改用 useClusterServers() / useSentinelServers()
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(0)
                // 适当调小连接池，避免本机核数较少时默认 24 个最小空闲连接的告警
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(10);
        return Redisson.create(config);
    }
}
