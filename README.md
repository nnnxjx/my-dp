# 代码使用说明(本项目来自b站[黑马程序员](https://space.bilibili.com/37974444)[redis教程](https://www.bilibili.com/video/BV1cr4y1671t)，仅供参考)

## RabbitMQ 异步秒杀改造

启动前需要 MySQL、Redis 和 RabbitMQ。RabbitMQ 默认连接 `127.0.0.1:5672`，账号密码为 `guest/guest`；可通过 `RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD` 环境变量覆盖。新导入的 `hmdp.sql` 已包含订单表的 `(user_id, voucher_id)` 唯一索引；已有数据库先检查重复订单，再执行 `migration_seckill_order.sql`。

下单请求经 Redis Lua 预扣库存和校验一人一单后，发送持久化消息到 `seckill.order.queue`。接口返回订单号表示客户端发送调用已完成，不代表 RabbitMQ 确认收到消息，也不代表订单已经写库。消费者最多尝试 3 次；失败消息进入 `seckill.order.dead.queue`，需要排查原因后再人工处理。不要直接删除死信消息或重置 Redis 预扣数据。

当前未开启生产者确认和 Return。发送调用异常或消息未路由时，系统无法确认消息是否到达队列，因此保留 `seckill:reservation:{orderId}` 记录供排查。上线前应增加自动对账与补偿流程，核对 Redis 预扣、RabbitMQ 消息和 MySQL 订单。

项目代码包含2个分支：
- master : 主分支，包含完整版代码，作为大家的编码参考使用
- init : 初始化分支，实战篇的初始代码，建议大家以这个分支作为自己开发的基础代码
- 前端资源在src/main/resources/nginx-1.18.0下

视频地址:
- [黑马程序员Redis入门到实战教程，深度透析redis底层原理+redis分布式锁+企业解决方案+redis实战](https://www.bilibili.com/video/BV1cr4y1671t)
- [https://www.bilibili.com/video/BV1cr4y1671t](https://www.bilibili.com/video/BV1cr4y1671t)
  - P24起 实战篇

## 1.下载
克隆完整项目
```git
git clone https://github.com/cs001020/hmdp.git
```
切换分支
```git
git checkout init
```

## 2.常见问题
部分同学直接使用了master分支项目来启动，控制台会一直报错:
```
NOGROUP No such key 'stream.orders' or consumer group 'g1' in XREADGROUP with GROUP option
```
这是因为我们完整版代码会尝试访问Redis，连接Redis的Stream。建议同学切换到init分支来开发，如果一定要运行master分支，请先在Redis运行一下命令：
```text
XGROUP CREATE stream.orders g1 $ MKSTREAM
```
