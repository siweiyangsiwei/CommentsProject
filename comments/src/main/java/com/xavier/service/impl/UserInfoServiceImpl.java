package com.xavier.service.impl;

import com.xavier.entity.UserInfo;
import com.xavier.mapper.UserInfoMapper;
import com.xavier.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
