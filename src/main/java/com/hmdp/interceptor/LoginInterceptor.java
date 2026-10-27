package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录拦截器，校验 session 中是否存在登录用户
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 请求携带 cookie，Spring 会自动解析出对应的 session
        HttpSession session = request.getSession();
        // 2. 从 session 中获取用户
        Object userDTO = session.getAttribute(SystemConstants.SESSION_USER_KEY);
        // 3. 用户不存在，拦截并返回 401
        if (userDTO == null) {
            log.debug("未登录用户访问拦截，请求路径：{}", request.getRequestURI());
            response.setStatus(401);
            return false;
        }
        // 4. 用户存在，保存到 ThreadLocal，便于同一线程内获取当前登录用户
        UserHolder.saveUser((UserDTO) userDTO);
        // 5. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 线程可能被复用，请求结束后必须移除，防止内存泄漏
        UserHolder.removeUser();
    }
}
