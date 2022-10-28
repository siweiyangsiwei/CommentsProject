package com.xavier.service.impl;

import com.xavier.entity.BlogComments;
import com.xavier.mapper.BlogCommentsMapper;
import com.xavier.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
