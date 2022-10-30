package com.xavier;

import com.xavier.entity.Shop;
import com.xavier.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xavier.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
public class RedisPreCache {
    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void preCacheShopGEO() {
        List<Shop> shopList = shopService.list();
        // 将list转化为以type为key分类的map集合,方便后面对不同type的店铺设置不同的redis key
        Map<Long, List<Shop>> collect = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 遍历map,将同一个type的店铺添加到同一个geo key中
        for (Map.Entry<Long, List<Shop>> entry : collect.entrySet()) {
            String key = SHOP_GEO_KEY + entry.getKey();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            for (Shop shop : entry.getValue()){
                RedisGeoCommands.GeoLocation<String> stringGeoLocation =
                        new RedisGeoCommands.GeoLocation<>(
                                shop.getId().toString(),
                                new Point(shop.getX(), shop.getY())
                        );
                locations.add(stringGeoLocation);
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }
}
