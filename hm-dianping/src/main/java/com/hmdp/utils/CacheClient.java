package com.hmdp.utils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 自定义key和对象 设置过期时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 自定义key和对象 设置逻辑过期时间
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 获取redis的数据(缓存穿透的解决方案)
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbCallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商户缓存 如果命中直接返回结果
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值 如果是空值 返回错误信息
        if (json != null) {
            return null;
        }
        // 2.如果没命中则去数据库进行查询
        R r = dbCallBack.apply(id);
        // 3.数据库如果没查询到则返回异常信息
        if (r == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4.如果有商户信息 则写入缓存 为缓存添加超时时间 并返回结果
        this.set(key, JSONUtil.toJsonStr(r),time, unit);
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 获取redis的数据(缓存穿透的解决方案)
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,Function<ID,R> dbCallBack
            , Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 从redis查询商户缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 如果没命中则直接返回null
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 如果命中则需要判断是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
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
                        // 重建缓存：先查数据库 再写入redis
                        R db_r = dbCallBack.apply(id);
                        this.setWithLogicExpire(key,db_r,time,unit);
                    } finally {
                        // 释放锁
                        unlock(lockKey);
                    }
                });
            }
        }
        // 返回旧的redis数据
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        return r;
    }

    // 获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}
