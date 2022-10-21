package com.xavier.interceptor;

import com.xavier.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 做用户登录校验的拦截器
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 前置拦截器,对用户的登录状态进行校验
     * @param request
     * @param response
     * @param handler
     * @return 校验成功与否的布尔值
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 派单人是否需要拦截(ThreadLocal中是否有用户)
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
