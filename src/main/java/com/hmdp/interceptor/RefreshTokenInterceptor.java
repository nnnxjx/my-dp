package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
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
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * token 刷新拦截器：只负责解析登录态并写入 ThreadLocal，无论是否登录都放行
 * 必须拦截全部路径，这样用户访问免登录的公开接口时也能持续刷新 token 有效期
 */
@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取请求头中携带的 token
        String token = request.getHeader("authorization");
        // 2. 无 token，说明是未登录访问，直接放行
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 3. 以 token 为 key 从 Redis 获取用户
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 4. token 已失效或查不到用户，同样放行，是否拦截由 LoginInterceptor 决定
        if (userMap.isEmpty()) {
            log.debug("token已失效，请求路径：{}", request.getRequestURI());
            return true;
        }
        // 5. 将 hash 数据转成 UserDTO 并保存到 ThreadLocal，便于同一线程内获取当前登录用户
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 6. 刷新 token 有效期，保证持续活跃的用户不会被强制重新登录
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 7. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 本拦截器拦截全部路径，ThreadLocal 的清理必须放在这里，
        // 否则被 LoginInterceptor 排除的路径会留下脏数据；线程会被复用，不清理还会导致内存泄漏
        UserHolder.removeUser();
    }
}
