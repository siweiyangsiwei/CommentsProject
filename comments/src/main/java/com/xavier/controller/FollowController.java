package com.xavier.controller;


import com.xavier.dto.Result;
import com.xavier.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    @PutMapping("{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,@PathVariable("isFollow") Boolean isFollow){
        return followService.follow(id,isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result followOrNot(@PathVariable("id") Long id){
        return followService.followOrNot(id);
    }

    @GetMapping("common/{id}")
    public Result findCommon(@PathVariable("id") Long id){
        return followService.findCommon(id);
    }
}
