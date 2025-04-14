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
import javax.servlet.http.HttpSession;
import java.util.Map;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //  判断当前的TreadLocal中有没有当前访问的用户
        if (UserHolder.getUser() == null) {
            // 没有，需要拦截，返回401
            log.debug("请求未携带token，返回401状态码，此错误由LoginInterceptor拦截器抛出");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        // 有用户，放行
        return true;
    }
}
