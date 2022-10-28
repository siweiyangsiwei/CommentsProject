package com.xavier.service;

import com.xavier.dto.Result;
import org.springframework.web.multipart.MultipartFile;

public interface IUploadService {
    Result uploadImage(MultipartFile image);

    Result deleteImage(String filename);

}
