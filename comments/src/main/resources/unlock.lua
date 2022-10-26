-- 获取当前线程的key
local key = KEYS[1]
-- 获取当前线程的value
local value = ARGV[1]

-- 获取redis中的当前线程的value
local now = redis.call("get",key)

-- 如果两者相等的话,则释放锁
if now == value then
    return redis.call("delete",key)
end
return 0