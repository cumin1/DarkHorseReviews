package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString()+"-";

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String keyName) {
        this.name = keyName;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程表示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 获取并判断线程标识
        String threadId =  ID_PREFIX + Thread.currentThread().getId();
        String redisId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(threadId.equals(redisId)) {
            // 如果线程标识和锁里的标识一样 则释放锁
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }
    }
}
