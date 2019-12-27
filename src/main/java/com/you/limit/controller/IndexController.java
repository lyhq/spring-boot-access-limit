package com.you.limit.controller;

import com.you.limit.annotation.AccessLimit;
import com.you.limit.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/index")
@AccessLimit(maxCount = 5, second = 1)
public class IndexController {

    /**
     * @return
     * @AccessLimit 修饰在方法上，优先使用其参数
     */
    @GetMapping("/test1")
    @AccessLimit
    public Result test() {
        return Result.ok();
    }

    /**
     * @return
     * @AccessLimit 修饰在类上，用的是类的参数
     */
    @GetMapping("/test2")
    public Result test2() {
        return Result.ok();
    }
}
