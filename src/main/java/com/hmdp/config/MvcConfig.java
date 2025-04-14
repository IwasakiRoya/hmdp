package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
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
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code", // 登录时发送验证码
                        "/user/login", // 登录
                        "/blog/hot", // 热门博客
                        "/shop/**", // 店铺相关
                        "/shop-type/**", // 店铺类型
                        "/upload/**", // 上传图片
                        "/voucher/**" // 优惠券
                ).order(1); // 设置拦截器的顺序，数字越小，优先级越高
        // 添加刷新token的拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
