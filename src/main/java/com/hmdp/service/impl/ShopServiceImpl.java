package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从Redis中查数据（缓存）
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.查询是否存在，且不为null
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.如果存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 如果为空值（非null）
        if (shopJson != null) {
            // 直接返回错误
            return Result.fail("店铺不存在");
        }
        // 如果是null，说明Redis里没有数据，需要去数据库查
        // 4.如果不存在，根据id查数据库
        // 该方法由于父类ServiceImpl实现
        Shop shop = getById(id);
        // 5.数据库也没有，返回错误
        // 缓存穿透优化，如果不存在，需要将空值写入缓存，然后返回false
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, java.util.concurrent.TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }

        // 6.如果数据库有，先写入Redis再返回
        // 6.1 将Shop对象转换为JSON
        shopJson = JSONUtil.toJsonStr(shop);
        // 6.2 将Map写入Redis
        // 添加过期时间，用于超时剔除
        stringRedisTemplate.opsForValue().set(key, shopJson, CACHE_SHOP_TTL, java.util.concurrent.TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    @Override
    @Transactional // 保证更新和删除操作的原子性
    public Result update(Shop shop) {
        Long id = shop.getId();
        // 判断id是否为空
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
