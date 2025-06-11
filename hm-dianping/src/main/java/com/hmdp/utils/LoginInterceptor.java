package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从threadLocal获取用户信息
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
