package com.xavier.service.impl;

import com.xavier.entity.Follow;
import com.xavier.mapper.FollowMapper;
import com.xavier.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

}
