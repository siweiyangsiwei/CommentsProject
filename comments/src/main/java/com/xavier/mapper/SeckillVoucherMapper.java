package com.xavier.mapper;

import com.xavier.entity.SeckillVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xavier.entity.Voucher;

public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    SeckillVoucher getSeckillVoucherById(Long voucherId);

    boolean updateStock(Long voucherId);
}
