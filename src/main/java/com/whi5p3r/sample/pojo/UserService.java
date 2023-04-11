package com.whi5p3r.sample.pojo;

import com.whi5p3r.spring.annotations.Autowired;
import com.whi5p3r.spring.annotations.Component;
import com.whi5p3r.spring.annotations.Scope;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月11日 14:26
 */
@Component("userService")
public class UserService {
    @Autowired
    private OrderService orderService;
}
