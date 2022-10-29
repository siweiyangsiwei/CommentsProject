package com.xavier.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.xavier.dto.Result;
import com.xavier.dto.UserDTO;
import com.xavier.entity.Follow;
import com.xavier.entity.User;
import com.xavier.mapper.FollowMapper;
import com.xavier.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xavier.service.IUserService;
import com.xavier.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.*;
import java.util.stream.Collectors;

import static com.xavier.utils.RedisConstants.FOLLOWS_KEY;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private FollowMapper followMapper;

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 关注某个博主
     * @param id 博主的userId
     * @param isFollow 关注还是取关
     * @return 返回关注或取关成功结果
     */
    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 与博主id建立一个follow对象
        Follow follow = new Follow();
        follow.setFollowUserId(id);
        follow.setUserId(userId);
        // 判断是关注还是取关
        if (BooleanUtil.isTrue(isFollow)){
            // 关注关系保存到数据库中
            followMapper.addFollow(follow);
            // 关注关系保存到redis中,方便以后做共同关注的查询
            stringRedisTemplate.opsForSet().add(FOLLOWS_KEY + userId,id.toString());
        }else{
            // 数据库中取消关注关系
            followMapper.removeFollow(follow);
            // redis中取消关旭关系
            stringRedisTemplate.opsForSet().remove(FOLLOWS_KEY + userId,id.toString());
        }
        return Result.ok();
    }

    /**
     * 判断该用户有没有关注该博主
     * @param id 博主的UserId
     * @return 返回是否关注
     */
    @Override
    public Result followOrNot(Long id) {
        // 获取当前登录用户Id
        Long userId = UserHolder.getUser().getId();
        // 判断有无关注
        Follow follow = followMapper.getFollow(userId, id);
        return Result.ok(BeanUtil.isNotEmpty(follow));
    }

    /**
     * 找出当前用户与访问用户的共同关注
     * @param id 访问用户的id
     * @return 返回共同关注的user列表
     */
    @Override
    public Result findCommon(Long id) {
        // 获取当前登录的用户id
        Long userId = UserHolder.getUser().getId();
        String key1 = FOLLOWS_KEY + userId;
        String key2 = FOLLOWS_KEY + id;

        // redis中查找两个用户的关注的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1,key2);
        // 没有共同关注返回空列表
        if (intersect == null || intersect.isEmpty()) return Result.ok(Collections.emptyList());
        // 获取共同关注的userId列表
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 查询共同关注用户信息
        List<UserDTO> users = new LinkedList<>();
        for (Long id2: ids){
            UserDTO userDTO = new UserDTO();
            User user = userService.getUserById(id2);
            BeanUtil.copyProperties(user,userDTO);
            users.add(userDTO);
        }
        return Result.ok(users);
    }
}
