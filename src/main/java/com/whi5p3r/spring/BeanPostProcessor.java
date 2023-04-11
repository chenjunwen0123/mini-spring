package com.whi5p3r.spring;

public interface BeanPostProcessor {
    Object postProcessBeforeInitialization(Object bean, String beanName);
    Object postProcessAfterInitialization(Object  bean, String beanName);
}
