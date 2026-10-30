package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOWS_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOWS_KEY + userId;
        if (BooleanUtil.isTrue(isFollow)) {
            // 2. 关注：数据库新增一条关注记录
            boolean saved = save(new Follow().setUserId(userId).setFollowUserId(id));
            if (!saved) {
                return Result.fail("关注失败！");
            }

            stringRedisTemplate.opsForSet().add(key, id.toString());
        } else {
            // 3. 取关：按 userId + followUserId 删除数据库记录
            boolean isRemoved = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, id));
            if(!isRemoved){
                return Result.fail("取关失败！");
            }
            stringRedisTemplate.opsForSet().remove(key, id.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        boolean isFollow = stringRedisTemplate.opsForSet().isMember(FOLLOWS_KEY + userId, id.toString());
        return Result.ok(isFollow);
    }

    @Override
    public Result queryCommonFollow(Long id) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 求两个用户关注 SET 的交集，得到共同关注的用户id集合
        Set<String> intersect = stringRedisTemplate.opsForSet()
                .intersect(FOLLOWS_KEY + userId, FOLLOWS_KEY + id);
        if (intersect == null || intersect.isEmpty()) {
            // 无共同关注，返回空列表
            return Result.ok(Collections.emptyList());
        }
        // 3. 解析出用户id，批量查出用户信息并转为 UserDTO
        List<Long> userIds = intersect.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
        List<User> users = userService.listByIds(userIds);
        List<UserDTO> userDTOs = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOs);
    }
}
