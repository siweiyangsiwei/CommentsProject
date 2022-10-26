package com.xavier.service.impl;

import cn.hutool.core.thread.ThreadUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xavier.dto.Result;
import com.xavier.entity.VoucherOrder;
import com.xavier.mapper.VoucherOrderMapper;
import com.xavier.service.ISeckillVoucherService;
import com.xavier.service.IVoucherOrderService;
import com.xavier.utils.RedisIWorker;
import com.xavier.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import static com.xavier.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.xavier.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIWorker redisIWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> seckillScript;

    // 静态代码块初始化lua脚本
    static {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setLocation(new ClassPathResource("seckill.lua"));
        seckillScript.setResultType(Long.class);
    }

    // 阻塞队列的特点:当队列中没有元素的时候,如果一个线程想去队列中获取元素,则线程会在这里阻塞直到有元素为止
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 创建只有一个线程的线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = ThreadUtil.newExecutor(1);

    /**
     * 实现Runnable的异步类
     * run方法中循环执行阻塞队列中的数据添加到数据库中
     */
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            try {
                while (true){
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 更新数据库数据
                    updateDataBase(voucherOrder);
                }
            } catch (InterruptedException e) {
                log.error("处理订单异常!!",e);
            }
        }
    }

    /**
     * 使用@PostConstruct注解,在类加载的时候便会执行这个函数
     * 函数中使用线程池来去提交我们的任务
     */
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    /**
     * 秒杀优惠券优化后的业务
     *
     * @param voucherId 优惠券的id
     * @return 返回购买结果
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 执行脚本
        Long result = stringRedisTemplate.execute(
                seckillScript,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), SECKILL_STOCK_KEY, SECKILL_ORDER_KEY
        );
        int r = result.intValue();
        // 购买失败
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足,请下次再来!" : "你已经购买过了!!");
        }
        // 购买成功
        long orderId = redisIWorker.nextID("order");
        // 新建订单对象
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        // 使用阻塞队列进行数据库下单
        orderTasks.add(voucherOrder);
        return Result.ok(orderId);
    }

    /**
     * 更新数据库的操作
     * 包括扣减库存以及插入订单
     * @param voucherOrder 订单信息
     */
    private void updateDataBase(VoucherOrder voucherOrder){
        // 扣减库存
        seckillVoucherService.updateStock(voucherOrder.getVoucherId());
        // 这里直接将voucherOrder写入数据库了
        // 其他也可以像我们前面做的一样加一个分布式锁来实现,
        // 但是已经没必要了,因为我们已经的redis将重复下单的用户过滤掉了,
        // 理论上不会出现同一个用户进入这个函数的情况,但是自己也可以实现做一个兜底
        voucherOrderMapper.addVoucherOrder(voucherOrder);
    }
}
