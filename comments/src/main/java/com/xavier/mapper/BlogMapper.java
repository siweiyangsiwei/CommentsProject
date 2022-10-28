package com.xavier.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xavier.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

public interface BlogMapper extends BaseMapper<Blog> {

    void saveBlog(Blog blog);

    List<Blog> getBlogsByUserId(Long userId, Integer limitIndex, int pageSize);

    List<Blog> queryBolgOrderByHot(Integer limitIndex, int pageSize);

    Blog getBlogById(Long id);

    void updateLiked(Long id, int i);
}
