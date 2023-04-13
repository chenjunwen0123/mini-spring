package com.whi5p3r.spring.beans.support;

import com.whi5p3r.spring.annotations.*;
import com.whi5p3r.spring.beans.config.BeanDefinition;
import com.whi5p3r.spring.beans.config.ScopeType;
import com.whi5p3r.spring.utils.StringUtil;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月12日 10:54
 */
public class BeanDefinitionReader {
    private final List<String> registryBeanClasses = new ArrayList<>();
    public BeanDefinitionReader(Class<?> configClass){
        // 读取配置类中ComponentScan设定的basePackage
        if (!configClass.isAnnotationPresent(ComponentScan.class)) {
            return;
        }

        ComponentScan componentScan = configClass.getAnnotation(ComponentScan.class);
        String basePackage = componentScan.basePackages();
        // 扫描baskPackage中的所有.class文件，存入registryBeanClasses中
        doScanner(basePackage);

    }

    /**
     * 扫描basePackage下所有.class的全限定类名，存入registryBeanClasses
     * @param basePackage
     */
    private void doScanner(String basePackage) {
        URL resource = this.getClass().getClassLoader().getResource(basePackage.replace(".","/"));
        assert resource != null;
        File base = new File(resource.getFile());

        for(File file: base.listFiles()){
            if(file.isDirectory()){
                doScanner(basePackage + "." + file.getName());
                continue;
            }
            if(!file.getName().endsWith(".class")) { continue; }
            String className = (basePackage + "." + file.getName().replace(".class", ""));
            this.registryBeanClasses.add(className);
        }
    }

    /**
     * 筛选出属于组件的类，存入List<BeanDefinition>中返回
     * @return
     */
    public List<BeanDefinition> loadBeanDefinition(){
        List<BeanDefinition> beanDefinitionList = new ArrayList<>();

        try {
            for (String beanClassName : registryBeanClasses) {
                Class<?> beanClass = Class.forName(beanClassName);
                // 如果不是组件，则不创建BeanDefinition
                if(! (beanClass.isAnnotationPresent(Controller.class) ||
                        beanClass.isAnnotationPresent(Service.class) ||
                        beanClass.isAnnotationPresent(Component.class))){
                    continue;
                }

                // 如果是接口类型，则跳过
                if(beanClass.isInterface()){
                    continue;
                }

                // 是一个普通的类
                String beanName = getBeanName(beanClass);
                BeanDefinition beanDefinition = doCreateBeanDefinition(beanName,beanClassName,beanClass);
                beanDefinitionList.add(beanDefinition);

                // 假如该类实现了接口, 则BeanDefinition中的beanName为接口名，beanClassName为实现类的全类名
                for(Class<?> i: beanClass.getInterfaces()){
                    String interfaceName = getBeanName(i);
                    doCreateBeanDefinition(interfaceName, beanClassName, beanClass);
                }

            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }

        return beanDefinitionList;
    }

    /**
     * 根据bean的类和其注解上的value获取beanName（优先注解上的value）
     * @param beanClass
     * @return
     */

    private String getBeanName(Class<?> beanClass){
        String beanName = StringUtil.toLowerFirstCase(beanClass.getSimpleName());
        if(beanClass.isAnnotationPresent(Component.class)){
            beanName = beanClass.getAnnotation(Component.class).value();
        }
        if(beanClass.isAnnotationPresent(Service.class)){
            beanName = beanClass.getAnnotation(Service.class).value();
        }
        if(beanClass.isAnnotationPresent(Controller.class)){
            beanName = beanClass.getAnnotation(Controller.class).value();
        }
        return beanName;
    }

    private BeanDefinition doCreateBeanDefinition(String beanName,String beanClassName,Class<?> beanClass){
        BeanDefinition beanDefinition = new BeanDefinition();
        beanDefinition.setBeanClassName(beanClassName);
        beanDefinition.setBeanName(beanName);
        beanDefinition.setScope(ScopeType.SINGLETON);
        if(beanClass.isAnnotationPresent(Scope.class)){
            beanDefinition.setScope(beanClass.getAnnotation(Scope.class).value());
        }
        beanDefinition.setBeanClass(beanClass);
        return beanDefinition;
    }
}
