package com.whi5p3r.spring.context;

import com.whi5p3r.spring.beans.config.BeanDefinition;
import com.whi5p3r.spring.beans.config.ScopeType;
import com.whi5p3r.spring.beans.support.BeanDefinitionReader;
import com.whi5p3r.spring.beans.support.DefaultListableBeanFactory;
import com.whi5p3r.spring.core.BeanFactory;

import java.util.List;
import java.util.Map;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月12日 17:30
 */
public class WhAnnotationApplicationContext implements BeanFactory {
    private final DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
    private BeanDefinitionReader reader;
    public WhAnnotationApplicationContext(Class<?> configClazz) {
        // 1. 读取配置文件
        this.reader = new BeanDefinitionReader(configClazz);

        // 2. 解析配置文件
        List<BeanDefinition> beanDefinitionList = reader.loadBeanDefinition();

        // 3. 注册BeanDefinition
        this.registry.registerBeanDefinition(beanDefinitionList);

        // 4. preInstantiateSingletons
        preInstantiateSingletons();

    }

    private void preInstantiateSingletons() {
        for(Map.Entry<String, BeanDefinition> entry : this.registry.beanDefinitionMap.entrySet()){
            String beanName = entry.getKey();
            BeanDefinition beanDefinition = entry.getValue();
            if(beanDefinition.isLazyInit()) { continue; }
            if(beanDefinition.getScope().equals(ScopeType.SINGLETON)){
                // 创建Bean
                getBean(beanName);
            }
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
