package com.xavier.utils;

public interface ILock {

    /**
     * 尝试获取redis锁
     * @param timeoutSec 锁的自动过期时间,获取后自动释放
     * @return 返回获取锁是否成功
     */
    boolean tryLock(Long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
