package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallLack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String josn = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isNotBlank(josn)) {
            //命中，返回商铺信息
            return JSONUtil.toBean(josn, type);
        }
        //命中的是否为空值
        if (josn != null) {
            return null;
        }
        //不存在，根据id查询数据
        R r = dbFallLack.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(key, r, time, unit);
        //判断商铺
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <T, ID> T queryWithLogicalExpire(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback,
                                            Long time, TimeUnit unit) {
        //从redis查询商铺缓存
        String key = keyPrefix + id;
        String josn = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isBlank(josn)) {
            //未命中，返回空
            return null;
        }
        //命中，先把json反序列化对象
        RedisData redisData = JSONUtil.toBean(josn, RedisData.class);
        JSONObject shopObject = (JSONObject) redisData.getData();
        T t = JSONUtil.toBean(shopObject, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，返回商铺信息
            return t;
        }
        //过期，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            //获得互斥锁，开启独立线程

            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    T t1 = dbFallback.apply(id);
                    //写入Redis
                    this.setWithLogicalExpire(key, t1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    this.unLock(lockKey);
                }
            });
        }
        //未获得互斥锁，返回商铺信息
        return t;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
