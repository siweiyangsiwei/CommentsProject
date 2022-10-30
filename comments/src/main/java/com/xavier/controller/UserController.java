package com.xavier.controller;


import com.xavier.dto.LoginFormDTO;
import com.xavier.dto.Result;
import com.xavier.dto.UserDTO;
import com.xavier.entity.User;
import com.xavier.entity.UserInfo;
import com.xavier.service.IUserInfoService;
import com.xavier.service.IUserService;
import com.xavier.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 点击发送验证码的mapping
     * @param phone 发送验证码的手机号
     * @param session 当前用户的session
     * @return  返回一个Result状态
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone,session);
    }

    /**
     * 点击登录按钮的mapping
     * @param loginForm 包含登录的手机号和验证码
     * @param session 当前用户的session
     * @return 返回一个Result状态
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    /**
     * 点击登录按钮后同样触发的mapping,只有返回包含User的Result才能登录成功
     * @return 返回一个包含User的Result状态
     */
    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        // 直接从ThreadLocal中获取保存过的user
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

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

    @GetMapping("/{id}")
    public Result getUserById(@PathVariable("id") Long id){
        return Result.ok(userService.getUserById(id));
    }

    @PostMapping("/sign")
    public Result userSign(){
        return userService.userSign();
    }

    @GetMapping("/sign/count")
    public Result userSignCount(){
        return userService.userSignCount();
    }
}
