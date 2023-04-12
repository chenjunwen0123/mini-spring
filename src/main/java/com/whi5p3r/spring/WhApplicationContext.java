package com.whi5p3r.spring;

import com.whi5p3r.spring.annotations.Autowired;
import com.whi5p3r.spring.annotations.Component;
import com.whi5p3r.spring.annotations.ComponentScan;
import com.whi5p3r.spring.annotations.Scope;
import com.whi5p3r.spring.beans.config.BeanDefinition;
import com.whi5p3r.spring.beans.config.BeanPostProcessor;
import com.whi5p3r.spring.beans.config.InitializingBean;
import com.whi5p3r.spring.beans.config.ScopeType;
import com.whi5p3r.spring.utils.StringUtil;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @description: IoC容器
 * @author: whi5p3r
 * @date: 2023年04月11日 14:01
 */
public class WhApplicationContext {
    private final ClassLoader classLoader;
    private Class<?> configClazz;
    /**
     * registryBeanClasses，存储所有扫描到的类的全类名
     */
    private List<String> registryBeanClasses = new ArrayList<>();

    /**
     * BeanDefinitionMap, 存储所有Bean定义
     */
    private Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();

    /**
     * singletonObjects, 存储所有单例的Bean
     */
    private ConcurrentHashMap<String,Object> singletonObjects = new ConcurrentHashMap<>();

    /**
     * beanPostProcessor, 存储容器所有的BeanPostProcessor
     */
    private List<BeanPostProcessor> beanPostProcessorList = new ArrayList<>();
    public WhApplicationContext(Class<?> clz) {
        this.configClazz = clz;
        this.classLoader = WhApplicationContext.class.getClassLoader();

        scan(clz);   // 解析配置类

        preInstantiateSingletons();   // 实例化单例对象
    }

    /**
     * 初始化所有单例Bean
     */
    private void preInstantiateSingletons(){
        for(Map.Entry<String,BeanDefinition> entry :beanDefinitionMap.entrySet()) {
            String beanName = entry.getKey();
            BeanDefinition beanDefinition = entry.getValue();

            if(beanDefinition.getScope().equals(ScopeType.SINGLETON)){
                // 创建Bean
                Object object = createBean(beanName,beanDefinition);
                singletonObjects.put(beanName,object);
            }
        }
    }

    /**
     * 搜索指定路径下所有的文件
     * @param file 根文件/根目录
     * @param result 存储所有文件的集合
     */
    private void dfs(File file, List<File> result){
        if(file.isFile()){
            result.add(file);
            return;
        }

        File[] files = file.listFiles();
        for(File f:files){
            dfs(f,result);
        }
    }

    /**
     * 扫描指定包下所有的文件，转存到registryBeanClasses中
     * @param basePackage 指定的包路径
     */
    private void scanPackage(String basePackage){
        // 通过相对路径（相对app类加载器）获取指定的资源
        URL resource = classLoader.getResource(basePackage);

        assert resource != null;
        File file = new File(resource.getFile());
        System.out.println(file);
        File[] files = file.listFiles();

        assert files != null;
        List<File> fileResult = new ArrayList<>();
        dfs(file,fileResult);

        for(File f: fileResult){
            //        -> 根据File对象获取其全限定类名
            String fileName = f.getAbsolutePath();
            if(fileName.endsWith(".class")) {
                String className = fileName.substring(fileName.indexOf("com"), fileName.indexOf(".class"));
                className = className.replace("\\", ".");

                this.registryBeanClasses.add(className);
            }
        }
    }

    /**
     * 根据在包中扫描所得的所有BeanDefinition中，扫描注解为组件的，放入beanDefinitionMap中
     */
    private void scanComponent(){
        List<BeanDefinition> beanDefinitionList = loadBeanDefinition();

        for(BeanDefinition beanDefinition : beanDefinitionList){
            try{
                Class<?> clz = classLoader.loadClass(beanDefinition.getBeanClassName());
                beanDefinition.setBeanClass(clz);
                if(clz.isAnnotationPresent(Component.class)){
                    // 如果当前Component是BeanPostProcessor接口的实现类则直接添加到容器的后处理器集合中
                    if(BeanPostProcessor.class.isAssignableFrom(clz)){
                        BeanPostProcessor beanPostProcessor = (BeanPostProcessor) clz.newInstance();
                        beanPostProcessorList.add(beanPostProcessor);
                    }

                    String beanName = beanDefinition.getBeanName();
                    Component component = clz.getAnnotation(Component.class);

                    // 如果指定了Component的值，则使用该值作为beanName
                    if(!component.value().isEmpty()) {
                        beanName = component.value();
                    }

                    // 扫描Scope注解，默认为单例
                    if(clz.isAnnotationPresent(Scope.class)){
                        Scope scope = clz.getAnnotation(Scope.class);
                        beanDefinition.setScope(scope.value());
                    }else{
                        beanDefinition.setScope(ScopeType.SINGLETON);
                    }

                    beanDefinitionMap.put(beanName, beanDefinition);
                }
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
    /**
     * 解析配置类
     * @param configClazz 配置类对象
     */
    private void scan(Class<?> configClazz) {
        if(configClazz.isAnnotationPresent(ComponentScan.class)) {
            // 解析配置类
            ComponentScan componentScan = configClazz.getAnnotation(ComponentScan.class);
            String basePackage = componentScan.basePackages();
            basePackage = basePackage.replace(".","/");

            scanPackage(basePackage);

            scanComponent();
        }
    }

    /**
     * 通过BeanName获取Bean
     * @param beanName
     * @return
     */
    public Object getBean(String beanName){
        Object instance = null;
        // 容器中是否有该Bean的定义
        if(beanDefinitionMap.containsKey(beanName)){
            // 该Bean是否单例
            if(singletonObjects.containsKey(beanName)){
                // 是单例则直接从singletonObjects中取
                instance = singletonObjects.get(beanName);
            }else{
                // 否则（多例）直接创建该Bean的实例返回
                instance = createBean(beanName,beanDefinitionMap.get(beanName));
            }
        }else{
            // 容器中没有该Bean的定义
            throw new NullPointerException("bean \"" + beanName + "\" not found");
        }
        return instance;
    }

    /**
     * 创建Bean对象
     * @param beanName
     * @param beanDefinition
     * @return
     */
    private Object createBean(String beanName, BeanDefinition beanDefinition){
        Class<?> clz = beanDefinition.getBeanClass();
        Object instance = null;
        try {
            // 实例化
            instance = clz.newInstance();

            // 依赖注入
            Field[] fields = clz.getDeclaredFields();
            for(Field field:fields){
                // 找到所有需要装配的属性
                if(field.isAnnotationPresent(Autowired.class)){
                    String fieldName = field.getName();
                    Object fieldBean = getBean(fieldName);


                    if(fieldBean == null && field.getAnnotation(Autowired.class).required()){
                        throw new NullPointerException("The bean \"" + fieldName +"\" is not found!");
                    }
                    // 注入
                    field.setAccessible(true);
                    field.set(instance, fieldBean);
                }
            }

            // 初始化前
            for(BeanPostProcessor postProcessor:beanPostProcessorList){
                instance = postProcessor.postProcessBeforeInitialization(instance,beanName);
            }
            // 初始化
            if(instance instanceof InitializingBean){
                ((InitializingBean) instance).afterPropertiesSet();
            }

            // 初始化后
            for(BeanPostProcessor postProcessor:beanPostProcessorList){
                instance = postProcessor.postProcessAfterInitialization(instance, beanName);
            }

        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return instance;
    }

    /**
     * 将扫描包所得的 registryBeanClasses 中的类的全限定类名转换为 BeanDefinition，并存入List
     * @return
     */
    public List<BeanDefinition> loadBeanDefinition(){
        List<BeanDefinition> result = new ArrayList<>();
        try {
            for (String classname : registryBeanClasses) {
                Class<?> beanClass = Class.forName(classname);

                // 如果beanClass是接口，不做处理
                if(beanClass.isInterface()) continue;

                // 默认类名的首字母小写
                result.add(createBeanDefinition(StringUtil.toLowerFirstCase(beanClass.getSimpleName()),beanClass.getName()));

                // 如果是某接口的实现类，则BeanDefinition中 beanName为接口类名，而存储的是其实现类的全限定类名
                for(Class<?> i: beanClass.getInterfaces()){
                    result.add(createBeanDefinition(StringUtil.toLowerFirstCase(i.getSimpleName()),beanClass.getName()));
                }
            }
        }catch(ClassNotFoundException e){
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * 创建BeanDefinition对象
     * @param beanName beanName
     * @param beanClassName bean的全限定类名
     * @return BeanDefinition对象
     */
    public BeanDefinition createBeanDefinition(String beanName, String beanClassName){
        BeanDefinition beanDefinition = new BeanDefinition();
        beanDefinition.setBeanName(beanName);
        beanDefinition.setBeanClassName(beanClassName);
        return beanDefinition;
    }



}
