package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取Session 对于每一个request，都会携带一个Session id
        // 如果没有携带，服务器会为他创建一个新的Session对象
        // 通过这个id可以获取到对应的Session对象，从而获取到Session中存储的用户信息
        HttpSession session = request.getSession();
        // 获取用户登录信息
        // 此处的user是登录时存储在Session中的用户信息
        Object user = session.getAttribute("user");
        // 判断用户是否存在
        if (user == null) {
            // 不存在，返回401状态码
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        // 存在，保存信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        // 返回true，放行请求
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 从TL中删除当前用户的信息
        UserHolder.removeUser();
    }
}
