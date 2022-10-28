package com.xavier.service;

import com.xavier.entity.SeckillVoucher;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xavier.entity.Voucher;

public interface ISeckillVoucherService extends IService<SeckillVoucher> {
    SeckillVoucher getSeckillVoucherById(Long voucherId);

    boolean updateStock(Long voucherId);

}
