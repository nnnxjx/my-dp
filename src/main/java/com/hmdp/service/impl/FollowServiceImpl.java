package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

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

    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        if (BooleanUtil.isTrue(isFollow)) {
            // 2. 关注：数据库新增一条关注记录
            boolean saved = save(new Follow().setUserId(userId).setFollowUserId(id));
            if (!saved) {
                return Result.fail("关注失败！");
            }
        } else {
            // 3. 取关：按 userId + followUserId 删除数据库记录
            remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, id));
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        boolean isFollow = query().eq("user_id", userId).eq("follow_user_id", id).count() > 0;
        return Result.ok(isFollow);
    }
}
