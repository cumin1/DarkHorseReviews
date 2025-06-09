package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
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
    public Result queryList() {
        // 1.先查询redis中有没有店铺类型数据
        // 由于返回的是list所以数据类型选择list
        ListOperations<String, String> listOperations = stringRedisTemplate.opsForList();
        List<String> redisShopType = listOperations.range("cache:shopType", 0, -1);
        // 2.如果有则返回数据
        if (redisShopType != null && !redisShopType.isEmpty()) {
            List<ShopType> shopTypeList = redisShopType.stream()
                    .map(str -> JSONUtil.toBean(str, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(shopTypeList);
        }
        // 3.如果没有则查询数据库 查询店铺类型数据
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        // 4.如果没有查询结果 则返回异常信息
        if (shopTypeList == null || shopTypeList.size() == 0) {
            return Result.fail("没有店铺信息");
        }
        // 5.如果有则向redis写入店铺类型数据并返回
        List<String> shopTypeListString = shopTypeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        listOperations.rightPushAll("cache:shopType",shopTypeListString);
        return Result.ok(shopTypeList);
    }
}
