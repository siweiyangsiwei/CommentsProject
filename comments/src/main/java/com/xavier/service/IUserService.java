package com.xavier.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xavier.dto.LoginFormDTO;
import com.xavier.dto.Result;
import com.xavier.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    User getUserById(Long userId);
}
