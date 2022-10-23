package com.xavier;

import cn.hutool.core.thread.ThreadUtil;
import com.xavier.utils.RedisIWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

@SpringBootTest
public class RedisWorkerTest {
    @Resource
    private RedisIWorker redisWorker;
    // 使用ThreadUtil来创建一个线程池,里面包含300个线程
    private ExecutorService executorService = ThreadUtil.newExecutor(300);
    // 使用ThreadUtil创建一个CountDownLatch,这个是用于在异步的时候进行计时的,
    // 即生成一个300位的计数器,每一个线程完成任务之后主动对这个计数器进行减操作,在计数器为0时
    // 这个计数器的await方法才会被通行,否则一直等待到0才向下执行.
    private CountDownLatch countDownLatch = ThreadUtil.newCountDownLatch(300);

    @Test
    public void testNextID() throws InterruptedException {
        long start = System.currentTimeMillis();
        // 一个任务,对应一个线程,生成100个唯一ID
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisWorker.nextID("order");
                System.out.println(id);
            }
            // 每次任务完成countDown一下,300个任务countDown300下
            countDownLatch.countDown();
        };
        // 启动300个线程
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
        // 等待直到计数器为0
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println(end - start);

    }
}
