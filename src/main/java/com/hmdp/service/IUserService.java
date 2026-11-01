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

    /**
     * 根据用户id查询用户信息（脱敏，不返回密码）
     *
     * @param id 用户id
     * @return 用户信息；用户不存在返回失败
     */
    Result queryUserById(Long id);

    /**
     * 每日签到：用 Redis bitmap 存储，key 为 sign:{userId}:{yyyy-MM}，
     * 当月第几天即对应 bit 位（从 0 开始）
     *
     * @return 签到结果；当天已签到返回失败
     */
    Result sign();
}
