package com.whi5p3r.spring.beans.support;

import com.whi5p3r.spring.beans.config.BeanDefinition;
import com.whi5p3r.spring.core.BeanFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月12日 17:23
 */
public class DefaultListableBeanFactory implements BeanFactory {
    public final Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();

    public void registerBeanDefinition(List<BeanDefinition> beanDefinitionList){
        for(BeanDefinition beanDefinition: beanDefinitionList){
            String beanName = beanDefinition.getBeanName();
            if(this.beanDefinitionMap.containsKey(beanName)){
                throw new RuntimeException(beanName  + " is already exists!");
            }
            this.beanDefinitionMap.put(beanName, beanDefinition);
        }
    }
    @Override
    public Object getBean(String beanName) {
        return null;
    }

    @Override
    public Object getBean(Class<?> beanName) {
        return null;
    }
}
