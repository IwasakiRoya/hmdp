package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Slf4j
public class RefreshTokenInterceptor  implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.debug("拦截到请求：{}", request.getRequestURI());
        // 获取请求头中的token
        String token = request.getHeader("authorization");
        log.debug("收到请求头 token：{}", token);
        if ("undefined".equalsIgnoreCase(token)) {
            log.debug("token为字符串 'undefined'");
        }
        if (StrUtil.isBlank(token)) {
            // 如果token为空，说明还没有登录
            // 这个拦截器的本意是刷新已有的token
            // 没有的话，放他去下一个拦截器，让下一个拦截器决定该怎么办
            return true;
        }
        // 如果有的话，查询Redis中的用户信息（Hash）
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        // 根据token判断用户是否存在
        if (userMap.isEmpty()) {
            // 不存在，说明redis中用户已经过期，或者说这个token本身就是错误的
            // 虽然不存在，但是这并不是刷新拦截器能处理的问题，刷新拦截器只管刷新
            // 更何况，token本身就是错误的，那么当前的TreadLocal肯定是没有用户的，让他去下一个拦截器，也会被拦截
            log.debug("token无效，转交给下一个拦截器处理");
            return true;
        }
        // 如果存在，将查询到的用户信息转换为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 保存信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 刷新token有效期，即Redis中的用户信息时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, java.util.concurrent.TimeUnit.SECONDS);
        // 返回true，放行请求
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
