package com.xavier.mapper;

import com.xavier.entity.ShopType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

public interface ShopTypeMapper extends BaseMapper<ShopType> {

    List<ShopType> getShopList();
}
