package com.whi5p3r.spring.beans.support;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月13日 9:12
 */
@Data
@AllArgsConstructor
public class BeanWrapper {
    private Object wrappedInstance;
    private Class<?> wrappedClass;
}
