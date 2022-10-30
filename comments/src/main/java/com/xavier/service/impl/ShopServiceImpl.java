package com.xavier.service.impl;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xavier.dto.Result;
import com.xavier.entity.Shop;
import com.xavier.mapper.ShopMapper;
import com.xavier.service.IShopService;
import com.xavier.utils.CacheClient;
import com.xavier.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.xavier.utils.RedisConstants.*;
import static com.xavier.utils.SystemConstants.MAX_GEO_DISTANCE;
import static com.xavier.utils.SystemConstants.MAX_PAGE_SIZE;

@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopMapper shopMapper;

    /**
     * 根据用户发送过来的店铺id查询店铺的消息信息
     * 其中使用到了redis缓存机制
     *
     * @param id 查询消息信息的店铺的id
     * @return 返回一个Result中保存店铺的消息信息, 查询错误则返回错误信息
     */
    @Override
    public Result queryById(Long id) {
/*        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, id2 -> shopMapper.getShopById(id2),
                CACHE_SHOP_TTL, TimeUnit.MINUTES);
*/
        Shop shop = cacheClient.queryWithMutex(
                CACHE_SHOP_KEY, id, Shop.class, id2 -> shopMapper.getShopById(id2),
                CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) return Result.fail("店铺不存在");
        //返回店铺数据
        return Result.ok(shop);
    }

    /**
     * 使用逻辑过期的方式解决缓存击穿问题获取店铺信息的备份
     * @param id 需要获取详细信息的店铺的id
     * @return 返回店铺的详细信息或者null值
     */
    private Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        // 请求发送过来,需要获取店铺信息,我们先去查询redis缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        // redis中不存在,直接返回null值,不需要去数据库查询的,
        // 因为一般使用这种方式的都是提前预热将数据加载到redis中的,如果没有查到说明不在本活动中
        if (StrUtil.isBlank(shopCache)) {
            return null;
        }
        // redis中存在,将redis中json转成实体对象
        RedisData redisData = JSONUtil.toBean(shopCache, RedisData.class);
        // 查看过期时间
        LocalDateTime cacheExpireTime = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        // 判断是否过期
        if (LocalDateTime.now().isBefore(cacheExpireTime)) {
            // 未过期,直接返回店铺信息
            return shop;
        }
        // 过期,需要缓存重建
        if (tryLock(lockKey)) {
            try {
                // 获取锁之后进行一个DoubleCheck
                shopCache = stringRedisTemplate.opsForValue().get(key);
                redisData = JSONUtil.toBean(shopCache, RedisData.class);
                if (LocalDateTime.now().isBefore(redisData.getExpireTime())) {
                    data = (JSONObject) redisData.getData();
                    shop = JSONUtil.toBean(data, Shop.class);
                    return shop;
                }
                // doubleCheck失败,redis中确实过期了,开启独立线程,需要这个这进行缓存重建
                ThreadUtil.execAsync(() -> {
                    this.saveShop2Redis(1L,20L);
                });
            } finally {
                // 释放锁
                unlock(lockKey);
            }
        }
        //没获取到锁,直接返回旧的店铺信息
        return shop;
    }


    /**
     * 使用互斥锁的方式解决了缓存击穿和缓存穿透问题的获取店铺信息的方法的备份
     *
     * @param id 获取店铺的id
     * @return 返回店铺的详细信息
     */
    private Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lock = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            // 请求发送过来,需要获取店铺信息,我们先去查询redis缓存
            String shopCache = stringRedisTemplate.opsForValue().get(key);
            //redis中存在,直接返回
            if (StrUtil.isNotBlank(shopCache)) {
                // 存在后将json数据转成shop对象
                shop = JSONUtil.toBean(shopCache, Shop.class);
                return shop;
            }
            // 判断命中的是否为空值,空值则直接返回fail了(即为空值缓存)
            if (shopCache != null) {
                return null;
            }
            // redis中不存在
            // 获取锁
            // 获取不到锁,则递归获取数据,在这里想当与等待
            if (!tryLock(lock)) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 获取到锁
            // 进行一个DoubleCheck,以防其他的线程修改完了我们再获取
            shopCache = stringRedisTemplate.opsForValue().get(key);
            //redis中存在,直接返回
            if (StrUtil.isNotBlank(shopCache)) {
                // 存在后将json数据转成shop对象
                shop = JSONUtil.toBean(shopCache, Shop.class);
                return shop;
            }
            // 判断命中的是否为空值,空值则直接返回fail了(即为空值缓存)
            if (shopCache != null) {
                return null;
            }
            // redis中的确不存在该数据,则查询数据库
            log.info("到数据库中查询");
            shop = shopMapper.getShopById(id);
            //数据库中不存在,返回错误店铺不存在
            if (shop == null) {
                // 解决缓存穿透问题,向redis添加空值信息
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //数据库中存在

            //店铺添加到redis中
            String shopJson = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lock);
        }
        return shop;
    }


    /**
     * 解决了缓存穿透问题的查询店铺详细信息的方法的备份
     *
     * @param id 查询店铺的id
     * @return 返回店铺的详细信息
     */
    private Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 请求发送过来,需要获取店铺信息,我们先去查询redis缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        //redis中存在,直接返回
        if (StrUtil.isNotBlank(shopCache)) {
            // 存在后将json数据转成shop对象
            Shop shop = JSONUtil.toBean(shopCache, Shop.class);
            return shop;
        }
        // 判断命中的是否为空值,空值则直接返回fail了(即为空值缓存)
        if (shopCache != null) {
            return null;
        }
        //redis中不存在,去查询数据库
        Shop shop = shopMapper.getShopById(id);
        //数据库中不存在,返回错误店铺不存在
        if (shop == null) {
            // 解决缓存穿透问题,向redis添加空值信息
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //数据库中存在

        //店铺添加到redis中
        String shopJson = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 后台管理更新店铺信息的请求
     * 其中需要更新redis以及数据库数据,考虑到了数据一直新的问题
     *
     * @param shop 更新后的店铺详细信息
     * @return 返回更新状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺信息更新失败,没有店铺ID!!");
        }
        // 更新数据库中的数据
        // (这里我自己实现了update方法,但是不太完善,只能传入整个需要的信息才能更新,不然其他的值都为null了)
        // 所以这里直接使用一个mybatis-plus中的update方法
//        shopMapper.updateShopById(shop);
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok("更新店铺数据成功!!");
    }

    /**
     * 获取店铺信息,有可能需要将店铺根据距离排序
     * @param typeId 店铺类型
     * @param current 当前页码
     * @param x 用户所处经度
     * @param y 用户所处纬度
     * @return 店铺列表
     */
    @Override
    public Result getShopByTypeOrderByDist(Integer typeId, Integer current, Double x, Double y) {
        // 计算分页查询的开始坐标
        int startIndex = (current - 1) * MAX_PAGE_SIZE;
        // 不需要根据经纬度排序
        if(x == null || y == null){
            List<Shop> shops =  shopMapper.getShopByTypeId(typeId,startIndex,MAX_PAGE_SIZE);
            return Result.ok(shops);
        }
        // 计算分页查询结束坐标
        int endIndex = current * MAX_PAGE_SIZE;
        // redis中进行查询的key
        String key = SHOP_GEO_KEY + typeId;
        // 查询方圆5公里的店铺信息以及对应的距离distance
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(MAX_GEO_DISTANCE),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(endIndex));
        // 空值判断
        if (results == null) return Result.ok(Collections.emptyList());
        // 获取信息内容
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        // 用于保存店铺信息
        List<Shop> shops = new ArrayList<>();
        // 遍历根据获取到的店铺id查询店铺信息,以及将distance填入shop中
        content.stream().skip(startIndex).forEach(result -> {
            Long id = Long.valueOf(result.getContent().getName());
            Double distance = result.getDistance().getValue();
            Shop shop = shopMapper.getShopById(id);
            shop.setDistance(distance);
            shops.add(shop);
        });
        // 返回
        return Result.ok(shops);
    }

    /**
     * 获取锁,使用redis中的setnx功能,如果redis中已经存在该键则返回false,不存在则创建并返回true
     *
     * @param key 保存在redis中的key
     * @return 返回boolean表示是否获取到了锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key 释放的锁的key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 设置逻辑过期方法存储店铺的信息到redis中
     *
     * @param id            去数据库中查询的店铺的id
     * @param expireSeconds 过期时间(s)
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = shopMapper.getShopById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        String redisDateJson = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, redisDateJson);
    }
}
