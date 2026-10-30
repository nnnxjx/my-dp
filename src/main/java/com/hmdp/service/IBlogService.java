package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 根据id查询博客详情，附带作者用户信息和当前登录用户是否点赞
     *
     * @param id 博客id
     * @return 博客详情；博客不存在返回失败
     */
    Result queryById(Long id);

    /**
     * 分页查询热门探店笔记（按点赞数降序），附带作者昵称、头像
     *
     * @param current 当前页码，从 1 开始
     * @return 当前页的笔记列表
     */
    Result queryHotBlog(Integer current);

    /**
     * 点赞/取消点赞：同一用户对同一笔记只能点赞一次，再次点击则取消点赞
     *
     * @param id 笔记id
     * @return 操作结果
     */
    Result likeBlog(Long id);

    /**
     * 查询笔记的点赞排行榜：最早点赞的前 5 名用户
     *
     * @param id 笔记id
     * @return 前五名最早点赞的 UserDTO 列表，按点赞时间升序
     */
    Result queryBlogLikes(Long id);

    /**
     * 分页查询指定用户发布的探店笔记
     *
     * @param userId  用户id
     * @param current 当前页码，从 1 开始
     * @return 当前页的笔记列表
     */
    Result queryBlogOfUser(Long userId, Integer current);

    /**
     * 保存探店笔记
     *
     * @param blog 探店笔记
     * @return 操作结果
     */
    Result saveBlog(Blog blog);

    /**
     * 分页查询个人关注的博主发布的探店笔记
     *
     * @param lastId    上次查询的笔记最小的时间戳
     * @param offset    偏移量
     * @return 当前页的笔记列表
     */
    Result queryBlogOfFollow(Long lastId, Integer offset);
}
