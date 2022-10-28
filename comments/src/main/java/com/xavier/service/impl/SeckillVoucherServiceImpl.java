package com.xavier.service.impl;

import com.xavier.entity.SeckillVoucher;
import com.xavier.entity.Voucher;
import com.xavier.mapper.SeckillVoucherMapper;
import com.xavier.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Override
    public SeckillVoucher getSeckillVoucherById(Long voucherId) {
        return seckillVoucherMapper.getSeckillVoucherById(voucherId);
    }

    @Override
    public boolean updateStock(Long voucherId) {
        return seckillVoucherMapper.updateStock(voucherId);
    }
}
