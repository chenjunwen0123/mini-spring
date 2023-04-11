package com.whi5p3r.sample;

import com.whi5p3r.spring.WhApplicationContext;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月11日 14:02
 */
public class Main {
    public static void main(String[] args){
        WhApplicationContext context = new WhApplicationContext(MyConfig.class);
        System.out.println(context.getBean("userService"));
        System.out.println(context.getBean("userService"));
        System.out.println(context.getBean("userService"));
        System.out.println(context.getBean("userService"));
    }
}
