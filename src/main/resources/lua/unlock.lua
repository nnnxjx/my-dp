-- 释放锁：比较锁中的线程标示与当前线程标示（ARGV[1]）是否一致
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 一致，则删除锁
    return redis.call('del', KEYS[1])
end
-- 不一致，则什么都不做
return 0
