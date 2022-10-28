package com.xavier.service;

import com.xavier.dto.Result;
import com.xavier.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopTypeService extends IService<ShopType> {
    Result getTypeList();
}
