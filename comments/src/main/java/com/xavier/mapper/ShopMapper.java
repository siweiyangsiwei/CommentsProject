package com.xavier.mapper;

import com.xavier.entity.Shop;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

public interface ShopMapper extends BaseMapper<Shop> {

    Shop getShopById(Long id);

    void updateShopById(Shop shop);

    List<Shop> getShopByTypeId(Integer typeId,int startIndex,int pageSize);
}
