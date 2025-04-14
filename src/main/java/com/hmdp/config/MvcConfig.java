package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // registry是一个拦截器注册器
        // addInterceptor()方法用于添加拦截器
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .excludePathPatterns(
                        "/user/code", // 登录时发送验证码
                        "/user/login", // 登录
                        "/blog/hot", // 热门博客
                        "/shop/**", // 店铺相关
                        "/shop-type/**", // 店铺类型
                        "/upload/**", // 上传图片
                        "/voucher/**" // 优惠券
                );
    }
}
