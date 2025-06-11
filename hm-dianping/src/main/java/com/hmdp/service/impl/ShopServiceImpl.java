package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        // cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    // 创建用于缓存重建的线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 基于逻辑过期方式解决缓存击穿问题
    /*public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 从redis查询商户缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 如果没命中则直接返回null
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 如果命中则需要判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        // 如果过期 缓存重建
        if (expireTime.isBefore(now)) {
            // 尝试获取互斥锁
            String lockKey = LOCK_SHOP_KEY + id;
            boolean flag = tryLock(lockKey);
            // 如果获取成功 开启独立线程执行缓存重建 主线程返回旧的redis数据
            if (flag){
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        saveShop2Redis(id,20L);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        // 释放锁
                        unlock(lockKey);
                    }
                });
            }
        }
        // 返回旧的redis数据
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        return shop;
    }*/

    // 互斥锁解决缓存击穿
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商户缓存 如果命中直接返回结果
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中的是否是空值 如果是空值 返回错误信息
        if (shopJson != null) {
            return null;
        }

        // 开始实现缓存重建
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 判断获取互斥锁是否成功
            if (!isLock) {
                // 失败->则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id); // 递归重试
            }
            // 成功->根据id查询数据库 将数据写入redis 释放锁
            shop = getById(id);
            Thread.sleep(200); // 模拟重建延时
            if (shop == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 如果有商户信息 则写入缓存 为缓存添加超时时间 并返回结果
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey); // 释放锁
        }
        return shop;
    }

    // 缓存穿透的代码
    /*public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商户缓存 如果命中直接返回结果
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中的是否是空值 如果是空值 返回错误信息
        if (shopJson != null) {
            return null;
        }
        // 2.如果没命中则去数据库进行查询
        Shop shop = getById(id);
        // 3.数据库如果没查询到则返回异常信息
        if (shop == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4.如果有商户信息 则写入缓存 为缓存添加超时时间 并返回结果
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }*/

    // 获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }


    // 缓存预热业务代码
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(20L);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入店铺数据到redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
}
