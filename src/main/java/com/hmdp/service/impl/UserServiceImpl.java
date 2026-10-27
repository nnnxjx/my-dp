package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.PasswordEncoder;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2. 生成6位随机验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 保存验证码到 session
        session.setAttribute(SystemConstants.SESSION_CODE_KEY, code);
        // 4. 模拟发送短信验证码，实际只打印日志
        log.debug("模拟发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号格式
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2. 校验验证码，与 session 中保存的不一致则返回错误
        String cacheCode = (String) session.getAttribute(SystemConstants.SESSION_CODE_KEY);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 3. 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 4. 用户不存在则创建新用户并保存到数据库
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 5. 保存用户信息到 session
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        session.setAttribute(SystemConstants.SESSION_USER_KEY, userDTO);
        return Result.ok();
    }

    /**
     * 根据手机号创建新用户
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 默认密码，加密存储
        user.setPassword(PasswordEncoder.encode("123456"));
        save(user);
        return user;
    }
}
