-- 优惠券秒杀下单判断脚本（整个流程由 Redis 单线程执行，保证原子性）
-- 参数约定：
--   ARGV[1]：优惠券 id
--   ARGV[2]：当前用户 id
--   库存 key 与已下单用户集合 key 在脚本内用 id 拼接得到
-- 返回值：
--   0 - 下单成功（已扣库存并记录用户）
--   1 - 库存不足
--   2 - 该用户已下过单

-- 拼接 key
local voucherId = ARGV[1]
local userId = ARGV[2]
local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId

-- 1. 判断库存是否充足
local stock = tonumber(redis.call('get', stockKey))
if (stock == nil or stock <= 0) then
    -- 库存不足（或库存 key 不存在），返回 1
    return 1
end

-- 2. 判断用户是否已下单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 已下过单，返回 2
    return 2
end

-- 3. 扣减库存
redis.call('incrby', stockKey, -1)
-- 4. 将 userId 存入当前优惠券的 set 集合，作为已下单标记
redis.call('sadd', orderKey, userId)

-- 下单成功，返回 0
return 0
