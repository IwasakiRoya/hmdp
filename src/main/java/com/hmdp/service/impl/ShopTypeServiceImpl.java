package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY_ID;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getTypeList() {
        // 查询商铺类型
        /*
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
*/      // 阅读源代码知道，这里我们需要返回的是一个List
        // 查阅数据库发现有一个shopType表，相当于就是要把这个表中的数据查询出来
        // 首先得看Redis里有没有这个数据
        // Redis中会以什么形式存储呢？List？
        // 如果是List，表示每一个在list里面的value都表示一个商铺类型对象的key
        // 这个商铺类型对象（再作为Key）可以再用Hash存储（对象）也可以使用String存储（Json）
        // 这里就先使用List存key1、2、3，然后key1、2、3分别存储商铺类型的hash
        // 所以先取出List，再根据这个List取Hash
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        log.debug("Redis中商铺类型的key：{}", shopTypeList);
        // 如果list为空，直接去查MySQL
        // 如果不为空，根据List中的key查找商户对类型对象
        boolean error = false;
        if (shopTypeList != null && !shopTypeList.isEmpty()) {
            List<ShopType> typeList = new ArrayList<>();
            for (int i = 0; i < shopTypeList.size(); i++) {
                Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(shopTypeList.get(i));
                // 如果出现空的对象，说明当前对象存储有问题，需要重新存储
                // 跳出循环
                if (map.isEmpty()) {
                    error = true;
                    break;
                }
                ShopType shopType = BeanUtil.fillBeanWithMap(map, new ShopType(), false);
                typeList.add(shopType);
            }
            if (!error) {
                // 如果没有错误，直接返回
                log.debug("在Redis中找到了商铺类型列表：{}", typeList);
                return Result.ok(typeList);
            }
        }
        // 如果typeList为空，或者有错误，直接去查MySQL
        // 这里使用父类的查询方法
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 如果typeList为空，直接返回错误
        if (typeList == null) {
            return Result.fail("商铺类型不存在");
        }
        // 如果typeList不为空，直接存入Redis
        // 首先将List转为Map,然后打入redis，键就是
        for (ShopType shopType : typeList) {
            // 这里使用String存储
            String key = CACHE_SHOP_TYPE_KEY_ID + shopType.getId();
            Map<String, Object> map = BeanUtil.beanToMap(shopType, new HashMap<>(), CopyOptions.create()
                    .setIgnoreNullValue(true)
                    .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
            // 这里使用Hash存储
            stringRedisTemplate.opsForHash().putAll(key, map);
            // 将key存入List
            stringRedisTemplate.opsForList().rightPush(CACHE_SHOP_TYPE_KEY, key);
        }
        // 测试看看拿到了什么
        log.debug("商铺类型列表：{}", typeList);
        // 返回数据
        return Result.ok(typeList);
    }
}
