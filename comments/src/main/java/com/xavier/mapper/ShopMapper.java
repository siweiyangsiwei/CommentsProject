package com.xavier.mapper;

import com.xavier.entity.Shop;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface ShopMapper extends BaseMapper<Shop> {

    Shop getShopById(Long id);

    void updateShopById(Shop shop);
}
