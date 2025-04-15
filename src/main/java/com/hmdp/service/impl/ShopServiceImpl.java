package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
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
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 单纯解决缓存穿透
        // Shop shop = queryWithPassTrough(id);

        // 解决缓存穿透的基础上利用互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);

        return Result.ok(shop);
    }

    // 将原本的查询封装
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            // 1.从Redis中查数据（缓存）
            String shopJson = stringRedisTemplate.opsForValue().get(key);
            // 2.查询是否存在，且不为null
            if (StrUtil.isNotBlank(shopJson)) {
                // 3.如果存在，直接返回
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            // 如果为空值（非null）说明是缓存穿透的空值缓解
            if (shopJson != null) {
                // 直接返回错误
                log.debug("店铺不存在");
                return null;
            }
            // 如果是null，说明Redis里没有数据，需要去数据库查
            // 4.如果不存在，根据id查数据库
            // 4.1 实现互斥锁
            boolean isLock = tryLock(lockKey);
            // 4.2 判断是否获取成功
            if (!isLock) {
                // 4.2.1 失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 4.4 成功，根据id查数据库
            // 该方法由于父类ServiceImpl实现
            shop = getById(id);
            // 模拟查询延时
            Thread.sleep(200);
            // 5.数据库也没有，返回错误
            // 缓存穿透优化，如果不存在，需要将空值写入缓存，然后返回false
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                log.debug("店铺不存在SQL");
                return null;
            }
            // 6.如果数据库有，先写入Redis再返回
            // 6.1 将Shop对象转换为JSON
            shopJson = JSONUtil.toJsonStr(shop);
            // 6.2 将Map写入Redis
            // 添加过期时间，用于超时剔除
            stringRedisTemplate.opsForValue().set(key, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7 释放互斥锁
            unlock(lockKey);
        }
        // 8 返回
        return shop;
    }

    public Shop queryWithPassTrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从Redis中查数据（缓存）
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.查询是否存在，且不为null
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.如果存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 如果为空值（非null）说明是缓存穿透的空值缓解
        if (shopJson != null) {
            // 直接返回错误
            log.debug("店铺不存在");
            return null;
        }
        // 如果是null，说明Redis里没有数据，需要去数据库查
        // 4.4 成功，根据id查数据库
        // 该方法由于父类ServiceImpl实现
        Shop shop = getById(id);
        // 5.数据库也没有，返回错误
        // 缓存穿透优化，如果不存在，需要将空值写入缓存，然后返回false
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("店铺不存在SQL");
            return null;
        }
        // 6.如果数据库有，先写入Redis再返回
        // 6.1 将Shop对象转换为JSON
        shopJson = JSONUtil.toJsonStr(shop);
        // 6.2 将Map写入Redis
        // 添加过期时间，用于超时剔除
        stringRedisTemplate.opsForValue().set(key, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7 返回
        return shop;
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

    private boolean tryLock(String key) {
        // 1.获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        // 2.判断是否获取成功
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        // 1.删除锁
        stringRedisTemplate.delete(key);
    }
}
