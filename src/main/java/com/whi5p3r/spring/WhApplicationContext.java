package com.whi5p3r.spring;

import com.whi5p3r.spring.annotations.Autowired;
import com.whi5p3r.spring.annotations.Component;
import com.whi5p3r.spring.annotations.ComponentScan;
import com.whi5p3r.spring.annotations.Scope;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description: IoC容器
 * @author: whi5p3r
 * @date: 2023年04月11日 14:01
 */
public class WhApplicationContext {
    private Class<?> configClazz;
    /**
     * BeanDefinitionMap, 存储所有Bean定义
     */
    private Map<String,BeanDefinition> beanDefinitionMap = new HashMap<>();
    /**
     * singletonObjects, 存储所有单例的Bean
     */
    private Map<String,Object> singletonObjects = new HashMap<>();
    public WhApplicationContext(Class<?> clz) {
        this.configClazz = clz;

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

            if(beanDefinition.getScope().equals("singleton")){
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
     * 解析配置类
     * @param configClazz 配置类对象
     */
    private void scan(Class<?> configClazz) {
        if(configClazz.isAnnotationPresent(ComponentScan.class)) {
            // 解析配置类
            ComponentScan componentScan = configClazz.getAnnotation(ComponentScan.class);
            String basePackages = componentScan.basePackages();
            basePackages = basePackages.replace(".","/");

            // 扫描该basePackages下所有被Component注解的类
            // 获取app类加载器
            ClassLoader classLoader = WhApplicationContext.class.getClassLoader();
            // 通过相对路径（相对app类加载器）获取指定的资源
            URL resource = classLoader.getResource(basePackages);

            assert resource != null;
            File file = new File(resource.getFile());
            System.out.println(file);
            File[] files = file.listFiles();

            assert files != null;
            List<File> fileResult = new ArrayList<>();
            dfs(file,fileResult);
            for(File f: fileResult){
                // 类加载 -> 根据当前file转化为class对象
                //        -> 根据File对象获取其全限定类名
                String fileName = f.getAbsolutePath();
                if(fileName.endsWith(".class")){
                    String className = fileName.substring(fileName.indexOf("com"),fileName.indexOf(".class"));
                    className = className.replace("\\" , ".");

                    try{
                        Class<?> clz = classLoader.loadClass(className);
                        if(clz.isAnnotationPresent(Component.class)){
                            Component component = clz.getAnnotation(Component.class);
                            String beanName = component.value();
                            BeanDefinition beanDefinition = new BeanDefinition();
                            beanDefinition.setClazz(clz);
                            if(clz.isAnnotationPresent(Scope.class)){
                                Scope scope = clz.getAnnotation(Scope.class);
                                beanDefinition.setScope(scope.value());
                            }else{
                                beanDefinition.setScope("singleton");
                            }
                            beanDefinitionMap.put(beanName, beanDefinition);
                        }
                    }catch (ClassNotFoundException e){
                        throw new RuntimeException(e);
                    }
                }
            }
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
        Class<?> clz = beanDefinition.getClazz();
        Object instance = null;
        try {
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
                    field.set(instance, fieldBean);
                }
            }

        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return instance;
    }

}
