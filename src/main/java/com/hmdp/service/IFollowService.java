package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注/取关用户：数据库增删 + Redis SET（follows:{userId}）同步维护
     *
     * @param id        要关注/取关的用户id
     * @param isFollow  true-关注，false-取关
     * @return 操作结果
     */
    Result follow(Long id, Boolean isFollow);

    /**
     * 判断当前登录用户是否已关注指定用户
     *
     * @param id 被关注的用户id
     * @return isFollow：true-已关注，false-未关注
     */
    Result isFollow(Long id);
}
