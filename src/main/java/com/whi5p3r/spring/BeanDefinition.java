package com.whi5p3r.spring;

import com.whi5p3r.spring.annotations.Scope;
import lombok.Data;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月11日 14:22
 */
@Data
public class BeanDefinition {
    private Class<?> clazz;
    private ScopeType scope;
}
