package com.xavier.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xavier.dto.Result;
import com.xavier.dto.UserDTO;
import com.xavier.entity.Blog;
import com.xavier.entity.User;
import com.xavier.mapper.BlogMapper;
import com.xavier.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xavier.service.IUserService;
import com.xavier.utils.SystemConstants;
import com.xavier.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.xavier.utils.RedisConstants.BLOG_LIKED_KEY;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private BlogMapper blogMapper;

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
        List<Blog> records = blogMapper.getBlogsByUserId(userId,(current - 1)*SystemConstants.MAX_PAGE_SIZE,SystemConstants.MAX_PAGE_SIZE);
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
        List<Blog> records = blogMapper.queryBolgOrderByHot((current - 1)*SystemConstants.MAX_PAGE_SIZE,SystemConstants.MAX_PAGE_SIZE);
        // 查询用户
        records.forEach(blog -> {
            this.queryUserByBlog(blog);
            this.isBlogLiked(blog);
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
