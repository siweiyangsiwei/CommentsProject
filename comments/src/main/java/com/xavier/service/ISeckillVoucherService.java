package com.xavier.service;

import com.xavier.entity.SeckillVoucher;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xavier.entity.Voucher;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface ISeckillVoucherService extends IService<SeckillVoucher> {
    SeckillVoucher getSeckillVoucherById(Long voucherId);

    boolean updateStock(Long voucherId);

}
