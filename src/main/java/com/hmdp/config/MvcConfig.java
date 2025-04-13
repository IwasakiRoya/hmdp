package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
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
                );
    }
}
