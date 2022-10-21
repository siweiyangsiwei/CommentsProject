package com.xavier;

import com.xavier.dto.Result;
import com.xavier.service.impl.ShopServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
@Slf4j
public class ShopServiceTest {
    @Resource
    private ShopServiceImpl shopService;

    @Test
    public void addShop2Redis(){
        shopService.saveShop2Redis(1L,30L);
    }

    @Test
    public void testQueryWithLogicalExpire(){
        Result result = shopService.queryById(1L);
        log.debug(result.toString());
    }
}
