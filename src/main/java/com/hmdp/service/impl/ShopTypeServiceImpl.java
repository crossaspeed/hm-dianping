package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE;
import static com.hmdp.utils.RedisConstants.CaCHE_TYPE_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        String key = CACHE_SHOP_TYPE;
        //在缓存中查询数据
        List<String> stringList = stringRedisTemplate.opsForList().range(key, 0, -1);
        //判断是否查询到数据
        if (!stringList.isEmpty()) {
            //查询到，返回结果
            List<ShopType> shopTypes = stringList.stream().map(s -> {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                return shopType;
            }).collect(Collectors.toList());
            return shopTypes;
        }
        //没有查询到，查询数据库
        List<ShopType> typeList = query().list();
        //判断数据库的结果如何
        if (typeList.isEmpty()) {
            //是空，返回错误
            return null;
        }
        //不为空，添加缓存数据
        List<String> jsonStringList = typeList.stream()
                .map(shopType -> JSONUtil.toJsonStr(shopType))
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key, jsonStringList);
        stringRedisTemplate.expire(key, CaCHE_TYPE_TTL, TimeUnit.MINUTES);
        return typeList;
    }
}
