package com.xavier;

import com.xavier.dto.Result;
import com.xavier.service.IVoucherOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class VoucherOrderServiceTest {
    @Resource
    private IVoucherOrderService voucherOrderService;

    @Test
    public void testSeckillVoucher(){
        Result result = voucherOrderService.seckillVoucher(2L);
    }
}
