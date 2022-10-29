package com.xavier.service;

import com.xavier.dto.Result;
import com.xavier.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogService extends IService<Blog> {

    Result uploadBlog(Blog blog);

    Result queryMyBlog(Integer current);

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result queryBlogByUserId(Long userId, Integer current);

    Result queryBlogOfFollow(Long lastId, Integer offset);
}
