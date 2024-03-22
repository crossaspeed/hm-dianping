package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
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

       /*
        //从redis查缓存
        Map<Object, Object> shopCacheMap = stringRedisTemplate.opsForHash().entries(key);
        //判断缓存是否命中
        if(!shopCacheMap.isEmpty()){
            //命中，返回商铺信息
            Shop shop = BeanUtil.fillBeanWithMap(shopCacheMap, new Shop(), false);
            return Result.ok(shop);
        }
        //未命中，根据id查询数据库
        Shop shop = query().eq("id", id).one();
        //判断商铺是否存在
        if(shop==null){
            //不存咋，返回404
            return Result.fail("商铺信息不存在");
        }
        Map<String, Object> shopMap = BeanUtil.beanToMap(shop, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldKey, fieldValue) -> fieldValue.toString()));
        //存在，将商铺数据写入redis中
        stringRedisTemplate.opsForHash().putAll(key,shopMap);*/

        //解决缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存穿透
        //Shop shop = queryWithMutex(id);

        //逻辑过期来解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

   /* public Shop queryWithLogicalExpire(Long id) {
        //从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isBlank(shopJson)) {
            //未命中，返回空
            return null;
        }
        //命中，先把json反序列化对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject shopObject = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(shopObject, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，返回商铺信息
            return shop;
        }
        //过期，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            //获得互斥锁，开启独立线程

            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    this.saveShop2Redid(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    this.unLock(lockKey);
                }
            });
        }
        //未获得互斥锁，返回商铺信息
        return shop;
    }*/

/*    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            //命中，返回商铺信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //命中的是否为空值
        if (shopJson != null) {
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取成功
            if (!isLock) {
                //失败，则休眠重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            String reShopJson = stringRedisTemplate.opsForValue().get(key);
            //判断缓存是否命中
            if (StrUtil.isNotBlank(reShopJson)) {
                //命中，返回商铺信息
                shop = JSONUtil.toBean(reShopJson, Shop.class);
                return shop;
            }
            //命中的是否为空值
            if (reShopJson != null) {
                return null;
            }
            shop = getById(id);
            //模拟重建的延迟
            Thread.sleep(200);
            //不存在，根据id查询数据

            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_NULL_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);

        }
        //释放互斥锁
        //判断商铺

        return shop;
    }*/

   /* public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            //命中，返回商铺信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //命中的是否为空值
        if (shopJson != null) {
            return null;
        }
        //不存在，根据id查询数据
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_NULL_TTL, TimeUnit.MINUTES);
        //判断商铺

        return shop;
    }*/


    //查实获取锁
   /* private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }*/


    public void saveShop2Redid(Long id, Long expireSeconds) throws InterruptedException {
        //查询店铺信息
        Shop shop = getById(id);
        Thread.sleep(200);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
