package com.xavier.service;

import com.xavier.dto.Result;
import com.xavier.entity.SeckillVoucher;
import com.xavier.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

}
