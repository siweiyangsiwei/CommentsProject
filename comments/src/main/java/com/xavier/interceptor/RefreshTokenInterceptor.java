package com.xavier.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.xavier.dto.UserDTO;
import com.xavier.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.xavier.utils.RedisConstants.LOGIN_USER_KEY;
import static com.xavier.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor() {
    }

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


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

        // 获取请求头中的token
        String token = request.getHeader("token");
        // 没有token直接返回错误
        if (StrUtil.isBlankIfStr(token)) {
            return true;
        }
        // 从redis获取这个token对应的map
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        // 找不到或为空直接返回错误
        if (userMap.isEmpty()){
            return true;
        }
        // 将map转换为对象
        UserDTO user = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        // 刷新token过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 校验成功,将信息保存到ThreadLocal中方便后续的取出
        UserHolder.saveUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
