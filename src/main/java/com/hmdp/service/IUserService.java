package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送短信验证码，验证码以手机号为 key 存入 Redis
     * @param phone 手机号
     * @return 处理结果
     */
    Result sendCode(String phone);

    /**
     * 短信验证码登录、注册，登录用户以 token 为 key 存入 Redis
     * @param loginForm 登录参数，包含手机号、验证码
     * @return 成功时 data 为 token
     */
    Result login(LoginFormDTO loginForm);
}
