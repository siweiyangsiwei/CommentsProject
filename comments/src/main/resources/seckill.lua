-- 获取优惠券id
local voucherId = ARGV[1]
-- 获取用户id
local userId = ARGV[2]
-- 秒杀库存的前缀
local SECKILL_STOCK_KEY = ARGV[3]
-- 秒杀订单的前缀
local SECKILL_ORDER_KEY = ARGV[4]
-- 库存的key
local stockKey = SECKILL_STOCK_KEY .. voucherId
-- 订单的key
local orderKey = SECKILL_ORDER_KEY .. voucherId
-- 判断库存是否足够
local stock = redis.call("get",stockKey)
if tonumber(stock) <= 0 then
    -- 库存不足
    return 1
end
-- 判断用户是否已经购买过该优惠券
local hadBuy = redis.call("sismember",orderKey,userId)
if hadBuy == 1 then
    -- 重复下单
    return 2
end
-- 扣减库存
redis.call("incrby",stockKey,-1)
-- 添加订单
redis.call("sadd",orderKey,userId)
-- 可以下单了
return 0
