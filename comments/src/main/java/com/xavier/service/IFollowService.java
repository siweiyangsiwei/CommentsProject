package com.xavier.service;

import com.xavier.dto.Result;
import com.xavier.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IFollowService extends IService<Follow> {

    Result follow(Long id, Boolean isFollow);

    Result followOrNot(Long id);

    Result findCommon(Long id);
}
