package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimplyRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String key_prefix = "lock:";

    public SimplyRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tyeLock(Long timeoutSec) {
        String key = key_prefix + name;
        long threadId = Thread.currentThread().getId();
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);

    }

    @Override
    public void unLock() {
        stringRedisTemplate.delete( key_prefix + name);
    }
}
