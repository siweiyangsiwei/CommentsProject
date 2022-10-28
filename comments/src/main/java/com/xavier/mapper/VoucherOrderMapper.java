package com.xavier.mapper;

import com.xavier.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    void addVoucherOrder(VoucherOrder voucherOrder);

    int countOrderByUserIdAndVoucherId(Long userId, Long voucherId);
}
