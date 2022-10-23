package com.xavier.mapper;

import com.xavier.entity.SeckillVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xavier.entity.Voucher;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    SeckillVoucher getSeckillVoucherById(Long voucherId);

    boolean updateStock(SeckillVoucher seckillVoucher);
}
