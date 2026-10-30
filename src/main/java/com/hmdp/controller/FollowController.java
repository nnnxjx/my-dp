package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Tag(name = "02-关注管理", description = "用户关注、取关相关接口")
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @Operation(summary = "关注或取关用户")
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,
                         @PathVariable("isFollow") Boolean isFollow) {
        // 业务逻辑在 Service：数据库增删 + Redis SET 同步维护
        return followService.follow(id, isFollow);
    }

    @Operation(summary = "判断当前登录用户是否关注了指定用户")
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        // 业务逻辑在 Service：查 Redis SET 是否包含该用户
        return followService.isFollow(id);
    }
}
