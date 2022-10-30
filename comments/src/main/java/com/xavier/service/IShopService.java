package com.xavier.service;

import com.xavier.dto.Result;
import com.xavier.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result updateShop(Shop shop);

    Result getShopByTypeOrderByDist(Integer typeId, Integer current, Double x, Double y);
}
