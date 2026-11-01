package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Tag(name = "01-用户管理", description = "用户登录、注册、个人信息相关接口")
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @Operation(summary = "发送手机验证码")
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @Operation(summary = "用户登录（手机号+验证码或手机号+密码）")
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm){
        return userService.login(loginForm);
    }

    /**
     * 登出功能
     * @return 无
     */
    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        UserDTO user=UserHolder.getUser();
        return Result.ok(user);
    }

    /**
     * 每日签到，签到数据用 Redis bitmap 存储
     */
    @Operation(summary = "每日签到")
    @PostMapping("/sign")
    public Result sign() {
        // 业务逻辑在 Service：SETBIT 将 sign:{userId}:{yyyy-MM} 对应日期位置 1
        return userService.sign();
    }

    /**
     * 统计当前用户截止当前时间在本月的连续签到天数
     */
    @Operation(summary = "统计本月连续签到天数")
    @GetMapping("/sign/count")
    public Result signCount() {
        // 业务逻辑在 Service：BITFIELD 取本月已签到 bit 后逐位与 1 运算统计
        return userService.signCount();
    }

    @Operation(summary = "根据id查询用户信息")
    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long id) {
        // 业务逻辑在 Service：查用户并脱敏，不返回密码
        return userService.queryUserById(id);
    }

    @Operation(summary = "根据id查询用户详情")
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
