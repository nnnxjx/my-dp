package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeckillRabbitConfig {
    public static final String EXCHANGE = "seckill.order.exchange";
    public static final String QUEUE = "seckill.order.queue";
    public static final String ROUTING_KEY = "seckill.order";
    public static final String DEAD_EXCHANGE = "seckill.order.dead.exchange";
    public static final String DEAD_QUEUE = "seckill.order.dead.queue";
    public static final String DEAD_ROUTING_KEY = "seckill.order.dead";

    @Bean
    public DirectExchange seckillExchange() { return new DirectExchange(EXCHANGE, true, false); }

    @Bean
    public DirectExchange seckillDeadExchange() { return new DirectExchange(DEAD_EXCHANGE, true, false); }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(DEAD_ROUTING_KEY).build();
    }

    @Bean
    public Queue seckillDeadQueue() { return new Queue(DEAD_QUEUE, true); }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding seckillDeadBinding() {
        return BindingBuilder.bind(seckillDeadQueue()).to(seckillDeadExchange()).with(DEAD_ROUTING_KEY);
    }
}
