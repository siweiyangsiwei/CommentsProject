package com.xavier.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.xavier.dto.Result;
import com.xavier.service.IUploadService;
import com.xavier.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {
    @Resource
    private IUploadService uploadService;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        return uploadService.uploadImage(image);
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        return uploadService.deleteImage(filename);
    }
}
