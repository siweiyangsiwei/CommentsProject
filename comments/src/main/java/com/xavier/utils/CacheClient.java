package com.xavier.utils;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xavier.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.xavier.utils.RedisConstants.*;
import static com.xavier.utils.RedisConstants.CACHE_SHOP_TTL;

@Component
@Slf4j
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将数据设置到redis缓存中
     * @param key 设置到redis中的key
     * @param value 设置到redis中的value
     * @param time  设置到redis中的过期时间
     * @param unit  过期时间的单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将数据以逻辑过期的方式设置到redis缓存中
     * @param key 设置到redis中的key
     * @param value 设置到redis中的value
     * @param time  设置到redis中的逻辑过期时间
     * @param unit  过期时间的单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData data = new RedisData();
        data.setData(value);
        data.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));
    }

    /**
     * 解决了缓存穿透的redis查询
     * @param keyPrefix 查询的key的前缀
     * @param id 查询的key的id
     * @param type  查询到的数据的类型
     * @param dbFallback redis中查询不到数据到数据库中进行查询的方法
     * @param time  过期数据重新写入redis的过期时间
     * @param unit 过期时间的单位
     * @param <R> 返回值的类型
     * @param <ID> ID的类型
     * @return 返回数据
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 请求发送过来,需要获取店铺信息,我们先去查询redis缓存
        String valueCache = stringRedisTemplate.opsForValue().get(key);
        //redis中存在,直接返回
        if (StrUtil.isNotBlank(valueCache)) {
            // 存在后将json数据转成shop对象
            return JSONUtil.toBean(valueCache, type);
        }
        // 判断命中的是否为空值,空值则直接返回fail了(即为空值缓存)
        if (valueCache != null) {
            return null;
        }
        //redis中不存在,去查询数据库
        R r = dbFallback.apply(id);
        //数据库中不存在,返回错误店铺不存在
        if (r == null) {
            // 解决缓存穿透问题,向redis添加空值信息
            this.set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //数据库中存在
        //店铺添加到redis中
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 使用互斥锁的方式解决缓存击穿和缓存穿透的redis查询
     * @param keyPrefix 查询的key的前缀
     * @param id 查询的key的id
     * @param type  查询到的数据的类型
     * @param dbFallback redis中查询不到数据到数据库中进行查询的方法
     * @param time  过期数据重新写入redis的过期时间
     * @param unit 过期时间的单位
     * @param <R> 返回值的类型
     * @param <ID> ID的类型
     * @return 返回数据
     */
    public <R,ID> R queryWithMutex(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        String lock = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            // 请求发送过来,需要获取店铺信息,我们先去查询redis缓存
            String valueCache = stringRedisTemplate.opsForValue().get(key);
            //redis中存在,直接返回
            if (StrUtil.isNotBlank(valueCache)) {
                // 存在后将json数据转成shop对象
                r = JSONUtil.toBean(valueCache,type);
                return r;
            }
            // 判断命中的是否为空值,空值则直接返回fail了(即为空值缓存)
            if (valueCache != null) {
                return null;
            }
            // redis中不存在
            // 获取锁
            // 获取不到锁,则递归获取数据,在这里想当与等待
            if (!tryLock(lock)) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit);
            }
            // 获取到锁
            // 进行一个DoubleCheck,以防其他的线程修改完了我们再获取
            valueCache = stringRedisTemplate.opsForValue().get(key);
            //redis中存在,直接返回
            if (StrUtil.isNotBlank(valueCache)) {
                // 存在后将json数据转成shop对象
                r = JSONUtil.toBean(valueCache, type);
                return r;
            }
            // 判断命中的是否为空值,空值则直接返回fail了(即为空值缓存)
            if (valueCache != null) {
                return null;
            }
            // redis中的确不存在该数据,则查询数据库
            R value = dbFallback.apply(id);
            //数据库中不存在,返回错误店铺不存在
            if (value == null) {
                // 解决缓存穿透问题,向redis添加空值信息
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //数据库中存在

            //店铺添加到redis中
            this.set(key,value,time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lock);
        }
        return r;
    }

    /**
     * 使用逻辑过期的方式解决缓存击穿的redis查询
     * @param keyPrefix 查询的key的前缀
     * @param id 查询的key的ID
     * @param type 查询数据的类型
     * @param dbFallback 过期数据到数据库中进行查询的方法
     * @param time  新数据的过期时间
     * @param unit 过期时间的单位
     * @param <R> 返回值类型
     * @param <ID> ID的类型
     * @return 返回数据
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String lockKey = LOCK_SHOP_KEY + id;
        // 请求发送过来,需要获取店铺信息,我们先去查询redis缓存
        String valueCache = stringRedisTemplate.opsForValue().get(key);
        // redis中不存在,直接返回null值,不需要去数据库查询的,
        // 因为一般使用这种方式的都是提前预热将数据加载到redis中的,如果没有查到说明不在本活动中
        if (StrUtil.isBlank(valueCache)) {
            return null;
        }
        // redis中存在,将redis中json转成实体对象
        RedisData redisData = JSONUtil.toBean(valueCache, RedisData.class);
        // 查看过期时间
        LocalDateTime cacheExpireTime = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        // 判断是否过期
        if (LocalDateTime.now().isBefore(cacheExpireTime)) {
            // 未过期,直接返回店铺信息
            return r;
        }
        // 过期,需要缓存重建
        if (tryLock(lockKey)) {
            try {
                // 获取锁之后进行一个DoubleCheck
                valueCache = stringRedisTemplate.opsForValue().get(key);
                redisData = JSONUtil.toBean(valueCache, RedisData.class);
                if (LocalDateTime.now().isBefore(redisData.getExpireTime())) {
                    data = (JSONObject) redisData.getData();
                    r = JSONUtil.toBean(data, type);
                    return r;
                }
                // doubleCheck失败,redis中确实过期了,开启独立线程,需要这个这进行缓存重建
                ThreadUtil.execAsync(() -> {
                    R value = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,value, time, unit);
                });
            } finally {
                // 释放锁
                unlock(lockKey);
            }
        }
        //没获取到锁,直接返回旧的店铺信息
        return r;
    }

    /**
     * 获取锁,使用redis中的setnx功能,如果redis中已经存在该键则返回false,不存在则创建并返回true
     *
     * @param key 保存在redis中的key
     * @return 返回boolean表示是否获取到了锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key 释放的锁的key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
