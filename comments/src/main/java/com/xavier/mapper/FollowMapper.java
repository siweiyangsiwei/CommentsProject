package com.xavier.mapper;

import com.xavier.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

public interface FollowMapper extends BaseMapper<Follow> {

    void addFollow(Follow follow);

    void removeFollow(Follow follow);

    Follow getFollow(Long userId, Long id);

    List<Long> getUserIdByFollowUserId(Long followUserId);
}
