package com.xavier.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xavier.dto.Result;
import com.xavier.dto.ScrollResult;
import com.xavier.dto.UserDTO;
import com.xavier.entity.Blog;
import com.xavier.entity.User;
import com.xavier.mapper.BlogMapper;
import com.xavier.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xavier.service.IFollowService;
import com.xavier.service.IUserService;
import com.xavier.utils.CacheClient;
import com.xavier.utils.SystemConstants;
import com.xavier.utils.UserHolder;
import io.lettuce.core.RedisClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.xavier.utils.RedisConstants.*;
import static com.xavier.utils.SystemConstants.MAX_FOLLOW_PAGE_SIZE;
import static com.xavier.utils.SystemConstants.MAX_PAGE_SIZE;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private BlogMapper blogMapper;

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 将上传的评论文件保存到数据库中
     * @param blog 评论
     * @return 评论的id
     */
    @Override
    public Result uploadBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogMapper.saveBlog(blog);
        // 将blog推送到所有粉丝的redis收件箱中
        // 查询粉丝
        List<Long> fansId = followService.getUserIdByFollowUserId(blog.getUserId());
        for (Long id : fansId){
            stringRedisTemplate.opsForZSet().add(FANS_FEED_KEY + id,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询该用户发送过的评论
     * @param current 当前显示的页数
     * @return 返回评论集合
     */
    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        // 根据用户查询
        List<Blog> records = blogMapper.getBlogsByUserId(userId,(current - 1)* MAX_PAGE_SIZE, MAX_PAGE_SIZE);
        return Result.ok(records);
    }

    /**
     * 查询点赞数最多的评论
     * @param current 当前显示的页数
     * @return 返回评论集合
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        List<Blog> records = blogMapper.queryBolgOrderByHot((current - 1)*MAX_PAGE_SIZE,MAX_PAGE_SIZE);

        // 查询用户
        records.forEach(blog -> {
            this.queryUserByBlog((Blog) blog);
            this.isBlogLiked((Blog) blog);
        });
        return Result.ok(records);
    }

    /**
     * 根据评论id查询评论
     * @param id 评论的id
     * @return 返回该评论
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = blogMapper.getBlogById(id);
        if (blog == null) return Result.fail("该评论不存在!!");
        queryUserByBlog(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 判断当前blog有没有被当前用户点赞
     * @param blog  当前blog
     */
    private void isBlogLiked(Blog blog) {
        if (UserHolder.getUser() == null) return ;
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), UserHolder.getUser().getId().toString());
        blog.setIsLike(score != null);
    }

    /**
     * 当前用户对当前blog点赞
     * @param id blogId
     * @return 返回是否点赞
     */
    @Override
    public Result likeBlog(Long id) {
        // 获取当前用户id
        UserDTO user = UserHolder.getUser();

        // 判断是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, user.getId().toString());
        // 没点赞过
        if (score == null){
            // 数据库点赞数+1
            blogMapper.updateLiked(id,1);
            // 将点赞信息保存在redis中
            stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id,user.getId().toString(), System.currentTimeMillis());
        }else {
            // 点赞过,取消
            blogMapper.updateLiked(id,-1);
            stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, user.getId().toString());
        }

        return Result.ok();
    }

    /**
     * 查询该blog点赞的人数以及排名前五的信息
     * @param id blog的id
     * @return 返回
     */
    @Override
    public Result queryBlogLikes(Long id) {
        Set<String> top5UserId = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if (top5UserId == null ||top5UserId.isEmpty()){
            return Result.ok();
        }
        List<UserDTO> users  = new LinkedList<>();
        for (Long userId : top5UserId.stream().map(Long::valueOf).collect(Collectors.toList())) {
            UserDTO userDTO = new UserDTO();
            User user = userService.getUserById(userId);
            BeanUtil.copyProperties(user,userDTO);
            users.add(userDTO);
        }
        return Result.ok(users);
    }

    @Override
    public Result queryBlogByUserId(Long userId, Integer current) {
        List<Blog> blogs = blogMapper.getBlogsByUserId(userId, (current - 1) * MAX_PAGE_SIZE, MAX_PAGE_SIZE);
        return Result.ok(blogs);
    }

    /**
     * 到当前用户的收件箱中获取已经关注的博主发过的blog
     * @param lastId 使用zset保存的score值,用来做滚动分页
     * @param offset 偏移量,一页显示的blog个数
     * @return 返回blog的集合
     */
    @Override
    public Result queryBlogOfFollow(Long lastId, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = FANS_FEED_KEY + userId;
        // 获取收件箱中的数据
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, lastId, offset, MAX_FOLLOW_PAGE_SIZE);
        // 非空判断
        if (typedTuples == null ||typedTuples.isEmpty()) return Result.ok(Collections.emptyList());
        // 创建一个集合用于保存所有的blog的id
        List<Long> ids = new ArrayList<>(typedTuples.size());
        // 用户保存最后一个数据的分数
        long minTime = 0;
        // 记录最后一个元素有多少个是同一个分数的,用户以后的offset赋值
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            long time = typedTuple.getScore().longValue();
            if (time == minTime){
                os++;
            }else{
                // 获取分数
                minTime = time;
                os = 1;
            }
        }
        // 用于保存收件箱中的所有blog
        List<Blog> blogs = new ArrayList<>(ids.size());

        // 根据id查询blog并保存中blogs中
        for (Long id : ids) {
            Blog blog = blogMapper.getBlogById(id);
            queryUserByBlog(blog);
            isBlogLiked(blog);
            blogs.add(blog);
        }
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(os);
        return Result.ok(result);
    }

    /**
     * 通过blog查询该blog的作者并将作者信息添加到blog中
     * @param blog blog
     */
    private void queryUserByBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getUserById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }
}
