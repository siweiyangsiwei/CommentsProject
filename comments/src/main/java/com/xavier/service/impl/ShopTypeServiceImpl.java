package com.xavier.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xavier.dto.Result;
import com.xavier.entity.ShopType;
import com.xavier.mapper.ShopTypeMapper;
import com.xavier.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.xavier.utils.RedisConstants.CACHE_SHOP_LIST_KEY;
import static com.xavier.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopTypeMapper shopTypeMapper;

    @Override
    public Result getTypeList() {
        // 查询店铺的类似,首先到redis中查询
        String shopListCache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_LIST_KEY);
        // redis中查询到了,直接返回
        if (StrUtil.isNotBlank(shopListCache)) {
            // 类型转换
            List<ShopType> shopTypeList = JSONUtil.toList(shopListCache, ShopType.class);
            return Result.ok(shopTypeList);
        }
        // redis中没有查询到,去查询数据库
        List<ShopType> shopTypeList = shopTypeMapper.getShopList();
        // 数据库没有查到,报错
        if (shopTypeList == null || shopTypeList.isEmpty()){
            return Result.fail("数据查询失败");
        }
        // 数据库查到了
        // 保存进redis
        String shopTypeListJson = JSONUtil.toJsonStr(shopTypeList);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_LIST_KEY,shopTypeListJson,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return Result.ok(shopTypeList);
    }
}
