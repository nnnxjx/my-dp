package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器：只判断 ThreadLocal 中是否存在用户，不存在则拦截
 * 依赖 RefreshTokenInterceptor 提前把用户写入 ThreadLocal，因此必须排在其后执行
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 判断 ThreadLocal 中是否存在用户
        if (UserHolder.getUser() == null) {
            // 2. 不存在，说明未登录或 token 已失效，拦截并返回 401
            log.debug("uri={}, 原始错误请求={}, 来源IP={}, UA={}",
                    request.getRequestURI(),
                    request.getAttribute("javax.servlet.error.request_uri"),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
            response.setStatus(401);
            return false;
        }
        // 3. 存在，放行
        return true;
    }

    // 不实现 afterCompletion：被本拦截器拦截或未登录的请求不会走到清理逻辑，
    // ThreadLocal 统一由拦截全部路径的 RefreshTokenInterceptor 负责移除
}
