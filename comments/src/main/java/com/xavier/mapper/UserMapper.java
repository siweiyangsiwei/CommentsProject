package com.xavier.mapper;

import com.xavier.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface UserMapper extends BaseMapper<User> {

    User getUserByPhone(String phone);

    void addNewUser(User newUser);

    User getUserByUserId(Long userId);
}
