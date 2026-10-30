package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Tag(name = "03-探店博文", description = "探店笔记发布、点赞、查询相关接口")
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @Operation(summary = "发布探店笔记")
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    @Operation(summary = "给探店笔记点赞（再次点击取消点赞）")
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 业务逻辑在 Service：Redis set 判断是否已点赞，未点赞过+1，已点赞过-1
        return blogService.likeBlog(id);
    }

    @Operation(summary = "根据id查询探店笔记详情（含作者用户信息）")
    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long id) {
        // 业务逻辑在 Service：查博客 + 填充作者昵称头像 + 当前用户是否点赞
        return blogService.queryById(id);
    }

    @Operation(summary = "查询笔记点赞排行榜（前五名最早点赞的用户）")
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        // 业务逻辑在 Service：按 ZSET 中点赞时间戳升序取前 5 名
        return blogService.queryBlogLikes(id);
    }

    @Operation(summary = "分页查询我的探店笔记")
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Operation(summary = "分页查询指定用户的探店笔记")
    @GetMapping("/of/user")
    public Result queryBlogOfUser(@RequestParam("id") Long id,
                                  @RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 业务逻辑在 Service：按 user_id 分页查询 + 填充作者信息和点赞状态
        return blogService.queryBlogOfUser(id, current);
    }

    @Operation(summary = "分页查询热门探店笔记")
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 业务逻辑在 Service：按点赞数降序分页 + 批量填充作者昵称头像
        return blogService.queryHotBlog(current);
    }
}
