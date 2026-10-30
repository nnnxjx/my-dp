package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.util.CollectionUtils;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private FollowServiceImpl followService;
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 1. 查询博客
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 2. 查询博客作者的用户信息，填充昵称、头像
        queryUserInfo(blog);
        // 3. 判断当前登录用户是否点赞过，赋值给 isLike
        isLike(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 利用 Redis 的 set 集合判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Boolean isMember = stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null;
        if (BooleanUtil.isTrue(isMember)) {
            // 3. 已点赞：取消点赞——点赞数-1，并从 zset 中移除
            update().setSql("liked = liked - 1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        } else {
            // 4. 未点赞：点赞数+1，并将用户id加入 zset 作为点赞标记
            update().setSql("liked = liked + 1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 分页参数从 0 开始，取前 5 个；score 是点赞时间戳，升序即最早点赞在前
        Set<String> top5 = stringRedisTemplate.opsForZSet()
                .range(BLOG_LIKED_KEY + id, 0, 4);
        if (top5==null || top5.isEmpty()) {
            // 2. 短路：当前无人点赞，返回空列表
            return Result.ok(Collections.emptyList());
        }
        // 3. 解析出其中的用户 id，批量查用户信息
        List<Long> userIds = top5.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
        //select from tb_user where id in (userIds) 不保证列表的顺序
        //List<User> users = userService.listByIds(userIds);

        //select from tb_user where id in (userIds) order by field(id,userIds)
        String userIdsStr = StrUtil.join(",", userIds);
        List<User> users = userService.query().in("id", userIds).last("ORDER BY FIELD(id," + userIdsStr + ")").list();

        // 4. 按点赞时间顺序转换为 UserDTO 返回（listByIds 不保证顺序，按 Redis 中的顺序重排）
        List<UserDTO> userDTOs = users
                .stream()
                .map((user) -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOs);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 按点赞数降序分页查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(records);
        }
        // 批量查询作者用户信息，避免逐条查库产生 N+1 次查询
        List<Long> userIds = records.stream()
                .map(Blog::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        records.forEach(blog -> {
            this.queryUserInfo(blog); // Fixed: Added missing parameter
            this.isLike(blog);
        });

        return Result.ok(records);
    }

    @Override
    public Result queryBlogOfUser(Long userId, Integer current) {
        // 根据 user_id 分页查询该用户的笔记，每页最多 MAX_PAGE_SIZE 条
        Page<Blog> page = query()
                .eq("user_id", userId)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 列表页需要展示作者昵称、头像与当前用户是否点赞过，与热门列表保持一致
        records.forEach(blog -> {
            this.queryUserInfo(blog);
            this.isLike(blog);
        });
        return Result.ok(records);
    }

    private void isLike(Blog blog){
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            blog.setIsLike(false);
        }
            Boolean isMember = stringRedisTemplate.opsForZSet()
                    .score(BLOG_LIKED_KEY + blog.getId(), userId.toString()) != null;
            blog.setIsLike(BooleanUtil.isTrue(isMember));
    }

    private void queryUserInfo(Blog blog) {
        User user = userService.getById(blog.getUserId());
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess=save(blog);
        if(!isSuccess){
            return Result.fail("保存失败！");
        }

        //获取关注的粉丝id
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow follow : follows){
            //feed:粉丝id 的收件箱
            String key = "feed:" + follow.getUserId();

            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }


    @Override
    public Result queryBlogOfFollow(Long lastId, Integer offset) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 根据最小时间戳和偏移量获取用户关注推送的笔记列表
        String key="feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedValues = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, lastId, offset, 5);

        // 3. 本页无数据，直接返回空结果
        if (typedValues == null || typedValues.isEmpty()) {
            return Result.ok();
        }
        //4.获取blogId,offset,minTime
        List<Long> blogIds = new ArrayList<>(typedValues.size());
        Long minTime = null;
        Integer newOffset = 1;
        for (ZSetOperations.TypedTuple<String> typedValue : typedValues){
            Long blogId = Long.valueOf(typedValue.getValue());
            blogIds.add(blogId);
            Long score = typedValue.getScore().longValue();
            if(score == minTime ){
                newOffset++;
            }
            else {
                newOffset = 1;
                minTime = score;
            }
        }


        //5.根据blogId获取笔记
        List<Blog> blogs = query().in("id", blogIds).last("ORDER BY FIELD(id," + blogIds + ")").list();
        blogs.forEach(blog -> {
            this.queryUserInfo(blog);
            this.isLike(blog);
        });

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(newOffset);
        return Result.ok(scrollResult);
    }
}
