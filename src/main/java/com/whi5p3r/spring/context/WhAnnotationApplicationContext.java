package com.whi5p3r.spring.context;

import com.whi5p3r.spring.annotations.Autowired;
import com.whi5p3r.spring.beans.config.BeanDefinition;
import com.whi5p3r.spring.beans.config.InitializingBean;
import com.whi5p3r.spring.beans.config.ScopeType;
import com.whi5p3r.spring.beans.support.BeanDefinitionReader;
import com.whi5p3r.spring.beans.support.BeanWrapper;
import com.whi5p3r.spring.beans.support.DefaultListableBeanFactory;
import com.whi5p3r.spring.core.BeanFactory;
import com.whi5p3r.spring.utils.StringUtil;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月12日 17:30
 */
public class WhAnnotationApplicationContext implements BeanFactory {
    private final DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
    private BeanDefinitionReader reader;
    /**
     * singletonCurrentlyInCreation：存储正在创建的Bean的BeanName
     */
    private Set<String> singletonCurrentlyInCreation = new HashSet<>();

    /**
     * singletonObjects：一级缓存，存储创建完毕的Bean（实例化->依赖注入->初始化）
     */
    private Map<String, Object> singletonObjects = new HashMap<>();

    /**
     * earlySingletonObjects：二级缓存，存储实例化后、但未依赖注入的纯净的Bean
     */
    private Map<String, Object> earlySingletonObjects = new HashMap<>();

    /**
     * factoryBeanObjectCache：三级缓存，实际上存储的是BeanDefinition
     */
    private Map<String, Object> factoryBeanObjectCache = new HashMap<>();

    public WhAnnotationApplicationContext(Class<?> configClazz) {
        // 1. 读取配置文件
        this.reader = new BeanDefinitionReader(configClazz);

        // 2. 解析配置文件
        List<BeanDefinition> beanDefinitionList = reader.loadBeanDefinition();

        // 3. 注册BeanDefinition
        this.registry.registerBeanDefinition(beanDefinitionList);

        // 4. 预加载所有非懒加载的Bean
        preInstantiateSingletons();

    }

    /**
     * 预加载非懒加载的Bean
     */
    private void preInstantiateSingletons() {
        for(Map.Entry<String, BeanDefinition> entry : this.registry.beanDefinitionMap.entrySet()){
            String beanName = entry.getKey();
            BeanDefinition beanDefinition = entry.getValue();
            // 判断是否懒加载
            if(beanDefinition.isLazyInit()) { continue; }
            // 判断是否为单例
            if(beanDefinition.getScope().equals(ScopeType.SINGLETON)){
                // 创建Bean
                getBean(beanName);
            }
        }
    }

    /**
     * 从IoC容器中获取Bean
     * @param beanName beanName
     * @return 所需的Bean对象
     */
    @Override
    public Object getBean(String beanName) {
        if(StringUtil.isEmpty(beanName)){
            throw new IllegalArgumentException("Empty beanName is not permitted.");
        }
        // 通过BeanName找到BeanDefinition
        if(!this.registry.beanDefinitionMap.containsKey(beanName)){
            throw new NullPointerException(beanName + "is not exist.");
        }
        BeanDefinition beanDefinition = this.registry.beanDefinitionMap.get(beanName);

        // 判断是否是单例
        if(beanDefinition.getScope().equals(ScopeType.SINGLETON)) {

            // 先从缓存中拿
            Object singleton = getSingleton(beanName, beanDefinition);
            if (singleton != null) {
                return singleton;
            }

            // 缓存中也没有，说明这个bean还没开始创建，添加创建标识
            if(!singletonCurrentlyInCreation.contains(beanName)){
                singletonCurrentlyInCreation.add(beanName);
            }

            // 创建这个bean（实例化、依赖注入、初始化）
            singleton = doCreateBean(beanName, beanDefinition);

            // 创建完成后删除 创建标识
            if(!singletonCurrentlyInCreation.contains(beanName)){
                singletonCurrentlyInCreation.remove(beanName);
            }

            // 将这个创建好的bean，放入一级缓存
            this.singletonObjects.put(beanName, singleton);

            return singleton;
        }

        // 如果是prototype，则直接创建一个新的实例
        return doCreateBean(beanName, beanDefinition);
    }

    /**
     * 创建Bean
     * @param beanName
     * @param beanDefinition
     * @return
     */
    private Object doCreateBean(String beanName, BeanDefinition beanDefinition){

        // 实例化对象
        Object instance = doInstantiate(beanName, beanDefinition);

        // 依赖注入
        populateBean(instance, beanDefinition);

        // 初始化对象（初始化 + 放入Wrapper）
        BeanWrapper beanWrapper = initializeBean(instance, beanDefinition);

        return beanWrapper.getWrappedInstance();
    }

    /**
     * 获取单例Bean
     * @param beanName
     * @param beanDefinition
     * @return
     */
    private Object getSingleton(String beanName, BeanDefinition beanDefinition) {
        // 先从一级缓存中取
        Object bean = singletonObjects.get(beanName);

        // 如果一级缓存中没有，但是有创建标识，说明存在循环依赖
        if(bean == null && singletonCurrentlyInCreation.contains(beanName)){
            // 在二级缓存中找
            bean = earlySingletonObjects.get(beanName);
            // 如果二级缓存也没有,在三级缓存中获取
            if(bean == null) {
                bean = doInstantiate(beanName, beanDefinition);
            }
        }

        return bean;
    }

    /**
     * 初始化Bean
     * @param instance
     * @param beanDefinition
     * @return
     */
    private BeanWrapper initializeBean(Object instance, BeanDefinition beanDefinition) {
        BeanWrapper beanWrapper = null;
        try {
            Class<?> clazz = Class.forName(beanDefinition.getBeanClassName());
            if(clazz.isAssignableFrom(InitializingBean.class)){
                ((InitializingBean)instance).afterPropertiesSet();
            }
            beanWrapper = new BeanWrapper(instance, clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return beanWrapper;
    }

    /**
     * 依赖注入
     * @param instance
     * @param beanDefinition
     */
    private void populateBean(Object instance, BeanDefinition beanDefinition) {
        try {
            Class<?> clazz = Class.forName(beanDefinition.getBeanClassName());

            Field[] fields = clazz.getDeclaredFields();
            for(Field field: fields){
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }

                Autowired autowired = field.getAnnotation(Autowired.class);
                String dependency = StringUtil.toLowerFirstCase(field.getType().getSimpleName());
                // String dependency = StringUtil.toLowerFirstCase(field.getType().getName());
                if(!"".equals(autowired.value())) {
                    dependency = autowired.value().trim();
                }


                field.setAccessible(true);
                field.set(instance, getBean(dependency));
            }

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 实例化Bean
     * @param beanDefinition
     * @return
     */
    private Object doInstantiate(String beanName, BeanDefinition beanDefinition) {
        if(factoryBeanObjectCache.containsKey(beanName)){
            return factoryBeanObjectCache.get(beanName);
        }

        String beanClassName = beanDefinition.getBeanClassName();
        Object instance = null;
        try {
            // 原生对象
            Class<?> clazz = Class.forName(beanClassName);
            instance = clazz.newInstance();

            factoryBeanObjectCache.put(beanDefinition.getBeanName(), instance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return instance;
    }

    /**
     * 通过类型获取Bean
     * @param beanName
     * @return
     */
    @Override
    public Object getBean(Class<?> beanName) {
        return null;
    }

    /**
     * 返回已注册的Bean定义的数量（委派IoC管理的Bean的总量）
     * @return
     */
    public int beanDefinitionCount(){
        return this.registry.beanDefinitionMap.size();
    }

    /**
     * 返回已注册的所有BeanName
     * @return
     */
    public String[] getBeanDefinitionNames(){
        return this.registry.beanDefinitionMap.keySet().toArray(new String[0]);
    }

}
