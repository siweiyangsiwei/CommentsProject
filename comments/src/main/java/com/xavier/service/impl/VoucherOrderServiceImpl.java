package com.xavier.service.impl;

import com.xavier.dto.Result;
import com.xavier.entity.SeckillVoucher;
import com.xavier.entity.VoucherOrder;
import com.xavier.mapper.SeckillVoucherMapper;
import com.xavier.mapper.VoucherOrderMapper;
import com.xavier.service.ISeckillVoucherService;
import com.xavier.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xavier.utils.RedisIWorker;
import com.xavier.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIWorker redisIWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getSeckillVoucherById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("查询不到此优惠券!!!");
        }
        // 2.判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀活动还未开始!");
        }
        // 3.判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已经结束!");
        }
        // 4.判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足!!!");
        }
        synchronized (userId.toString().intern()) {
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 7.优惠券下单
            return proxy.createVoucherOrder(seckillVoucher);
        }
    }

    @Transactional
    public Result createVoucherOrder(SeckillVoucher seckillVoucher) {
        Long userId = UserHolder.getUser().getId();
        Long voucherId = seckillVoucher.getVoucherId();

        // 一人一单功能实现,查看该用户是否已经购买过该优惠券了
        int count = voucherOrderMapper.countOrderByUserIdAndVoucherId(userId, voucherId);
        if (count > 0) {
            return Result.fail("该用户已经拥有该优惠券了!不能再购买");
        }
        // 5.扣减库存
        boolean success = seckillVoucherService.updateStock(seckillVoucher);
        if (!success) {
            return Result.fail("更新失败!!");
        }
        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id,通过之前自己编写的全局唯一ID获取
        voucherOrder.setId(redisIWorker.nextID("order"));
        // 用户id,通过保存在ThreadLocal中的User获取
        voucherOrder.setUserId(userId);
        // 优惠券的id
        voucherOrder.setVoucherId(voucherId);
        // 订单信息写入数据库
        voucherOrderMapper.addVoucherOrder(voucherOrder);
        return Result.ok(voucherOrder.getId());

    }
}
