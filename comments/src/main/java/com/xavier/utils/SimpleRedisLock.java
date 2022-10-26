package com.xavier.utils;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import javax.swing.plaf.TreeUI;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String VALUE_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> unlockScript;

    static {
        unlockScript = new DefaultRedisScript<>();
        unlockScript.setLocation(new ClassPathResource("unlock"));
        unlockScript.setResultType(Long.class);
    }

    public SimpleRedisLock() {
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        long threadId = Thread.currentThread().getId();
        String value = VALUE_PREFIX + threadId;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, value, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        // 本线程的key
        String key = KEY_PREFIX + name;
        // 本线程的value
        String value = VALUE_PREFIX + Thread.currentThread().getId();
        // 与当前线程的进行比较,一致则删除key,释放锁
        stringRedisTemplate.execute(unlockScript, Collections.singletonList(key),value);
    }


    /*@Override
    public void unlock() {
        // 获取线程标识
        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 判断与当前线程是否一致,防止误删
        if (value != null && value.equals(VALUE_PREFIX + Thread.currentThread().getId())) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
