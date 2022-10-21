package com.xavier.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xavier.dto.Result;
import com.xavier.entity.Shop;
import com.xavier.mapper.ShopMapper;
import com.xavier.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.xavier.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
        Shop shop = queryWithMutex(id);
        if (shop == null) return Result.fail("店铺不存在");
        //返回店铺数据
        return Result.ok(shop);
    }

    /**
     * 解决了缓存击穿和缓存穿透问题的获取店铺信息的方法的备份
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
}
