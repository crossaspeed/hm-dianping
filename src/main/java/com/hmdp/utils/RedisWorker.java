package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {
    private static final long BEGIN_TIMESTAMP=1704067200;
    private static final int COUNT_BITS=32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix) {
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowSecond-BEGIN_TIMESTAMP;
        //生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //接单并返回
        return timestamp <<COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }
}
