package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号（为啥你前端不管这个事？占用服务器资源就为了给你验个正则？）
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4.保存验证码到session
        session.setAttribute("code", code);
        // 5.发送验证码
        log.debug("验证码发送成功，验证码为：{}", code);
        // 6.返回成功信息
        return Result.ok("验证码发送成功, code:" + code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            // 如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 2.校验验证码(先从session中获取验证码)
        String cacheCode = (String) session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !code.equals(cacheCode)) {
            // 3.验证码不一致，报错
            return Result.fail("验证码错误");
        }

        // 4.一致，根据手机号查询用户是否存在
        User user = query().eq("phone", loginForm.getPhone()).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户
            user = creatUserWithPhone(loginForm.getPhone());
        }

        // 7.保存用户信息到session
        // 注意这个地方，你定义的属性名是user
        // 以后获得的时候session.setAttribute("user", user)就可以了
        // 转换实体为DTO后再存储
        UserDTO userDTO = convertToDTO(user);
        session.setAttribute("user", userDTO);
         return Result.ok();
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setNickName(user.getNickName());
        dto.setIcon(user.getIcon());
        return dto;
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 7.保存用户信息到数据库
        save(user);
        // 8.返回用户信息
        return user;
    }
}
