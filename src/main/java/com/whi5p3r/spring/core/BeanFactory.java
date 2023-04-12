package com.whi5p3r.spring.core;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月12日 17:23
 */
public interface BeanFactory {
    Object getBean(String beanName);
    Object getBean(Class<?> beanName);
}
