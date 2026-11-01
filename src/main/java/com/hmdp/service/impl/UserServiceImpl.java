package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
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
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2. 生成6位随机验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 以手机号为 key 将验证码存入 redis，并设置有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4. 模拟发送短信验证码，实际只打印日志
        log.debug("模拟发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1. 校验手机号格式
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2. 根据手机号从 redis 取出验证码比对，不一致则返回错误
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
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
        // 5. 生成随机 token，以 token 为 key 将用户信息存入 redis
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> {
                    return fieldValue.toString();
                }));

        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, map);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 6. 将 token 返回给客户端，后续请求携带该 token
        return Result.ok(token);
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

    @Override
    public Result queryUserById(Long id) {
        // 1. 根据id查询用户
        User user = getById(id);
        if (user == null) {
            return Result.fail("用户不存在！");
        }
        // 2. 脱敏：拷贝为 UserDTO，不包含密码字段
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @Override
    public Result sign() {
        // 1. 获取当前登录用户（/user/sign 受登录拦截器保护，UserHolder 必有值）
        Long userId = UserHolder.getUser().getId();
        // 2. 获取当前日期，拼接 key：prefix + userId + 年月，如 sign:{userId}:2026-11
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        // 3. 当月第几天即对应 bit 位，从 0 开始保证每月 1 号落在第 0 位
        int offset = now.getDayOfMonth() - 1;
        // 4. SETBIT 签到，返回的是旧 bit 值，为 true 说明当天已签过，不重复计
        Boolean signed = stringRedisTemplate.opsForValue().setBit(key, offset, true);
        if (BooleanUtil.isTrue(signed)) {
            return Result.fail("您今天已经签到过了");
        }
        return Result.ok("签到成功");
    }
}
