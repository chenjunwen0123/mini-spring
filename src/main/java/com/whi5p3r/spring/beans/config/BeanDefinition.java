package com.whi5p3r.spring.beans.config;

import lombok.Data;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月11日 14:22
 */
@Data
public class BeanDefinition {
    private boolean isLazyInit = false;
    private boolean isFactoryBean = false;
    private String beanName;
    private String beanClassName;
    private ScopeType scope;
    private Class<?> beanClass;
}
