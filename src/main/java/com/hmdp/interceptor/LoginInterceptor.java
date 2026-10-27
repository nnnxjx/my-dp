package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 登录拦截器，校验请求携带的 token 在 Redis 中是否存在对应的登录用户
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取请求头中携带的 token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            log.debug("无token，拦截请求：{}", request.getRequestURI());
            response.setStatus(401);
            return false;
        }
        // 2. 以 token 为 key 从 Redis 获取用户
        String key=LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 3. 用户不存在，拦截并返回 401
        if (userMap.isEmpty()) {
            log.debug("token异常：{}", request.getRequestURI());
            response.setStatus(401);
            return false;
        }
        // 4. 用户存在，保存到 ThreadLocal，便于同一线程内获取当前登录用户
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 5. 刷新 token 有效期，保证持续活跃的用户不会被强制重新登录
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 6. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 线程可能被复用，请求结束后必须移除，防止内存泄漏
        UserHolder.removeUser();
    }
}
