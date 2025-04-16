package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 实现方法1：将任意java对象序列化为json并存储在String类型的key中，并且可以设置TTL过期时间
    // 参数1表示希望用来存取对象的key，参数2表示希望存储的对象
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 实现方法2：将任意java对象序列化为json并存储在String类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 1.将对象序列化为json
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // 2.设置逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 3.存储到Redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 实现方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存控制的方式解决缓存穿透问题
    public <T, ID> T queryWithPassTrough(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从Redis中查数据（缓存）
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.查询是否存在，且不为null
        if (StrUtil.isNotBlank(json)) {
            // 3.如果存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 如果为空值（非null）说明是缓存穿透的空值缓解
        if (json != null) {
            // 直接返回错误
            log.debug("店铺不存在");
            return null;
        }
        // 如果是null，说明Redis里没有数据，需要去数据库查
        // 4.4 成功，根据id查数据库
        // 该方法由于父类ServiceImpl实现
        T t = dbFallback.apply(id);
        // 5.数据库也没有，返回错误
        // 缓存穿透优化，如果不存在，需要将空值写入缓存，然后返回false
        if (t == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("店铺不存在SQL");
            return null;
        }
        // 6.如果数据库有，先写入Redis再返回
        // 6.1 将Shop对象转换为JSON
        json = JSONUtil.toJsonStr(t);
        // 6.2 将Map写入Redis
        // 添加过期时间，用于超时剔除
        this.set(key, json, time, unit);
        // 7 返回
        return t;
    }

    // 实现方法4：根据指定的key查询缓存，并反序列化为为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <T, ID> T queryWithLogicalExpire(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit, String keyLockPrefix) {
        String key = keyPrefix + id;
        // 1.从Redis中查数据（缓存）
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.查询是否存在，如果命中了是空的，那就直接返回，因为预热机制不允许存在空健
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3. 判断是否过期
        // 先把Json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        T t = JSONUtil.toBean(data, type);
        // 3.1 获取过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 3.2 未过期，直接返回
            return t;
        }
        // 已过期：缓存重建
        // 获取互斥锁
        String lockKey = keyLockPrefix + id;
        boolean isLock = tryLock(lockKey);
        // 获取失败返回
        if (!isLock) {
            return t;
        }
        // 获取成功，开启独立线程实现缓存重建
        // 给线程池发布任务
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try {
                // 查数据库
                T t2 = dbFallback.apply(id);
                // 写Redis
                this.setWithLogicalExpire(key, t2, time, unit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁
                unlock(lockKey);
            }
        });
        // 7 返回
        return t;
    }

    // 创建一个10线程的线程池玩玩
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private boolean tryLock(String key) {
        // 1.获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_TTL, TimeUnit.MINUTES);
        // 2.判断是否获取成功
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        // 1.删除锁
        stringRedisTemplate.delete(key);
    }

}
