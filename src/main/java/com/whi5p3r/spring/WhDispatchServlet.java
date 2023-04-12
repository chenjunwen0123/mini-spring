package com.whi5p3r.spring;

import com.whi5p3r.spring.annotations.*;
import com.whi5p3r.spring.utils.StringUtil;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月12日 11:59
 */
public class WhDispatchServlet extends HttpServlet {
    private final Properties contextConfig = new Properties();
    private final List<String> classNames = new ArrayList<>();
    private final Map<String, Object> ioC = new HashMap<>();
    private final Map<String, Method> mappingHandler = new HashMap<>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 6. 委派URL给具体的调用方法
        try {
            doDispatch(req,resp);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();             // 请求路径
        String contextPath = req.getContextPath();    // app的根路径
        Map<String, String[]> parameterMap = req.getParameterMap();

        // 统一url规则：去掉contextPath，将连续多个斜杠变成一个斜杠，与mappingHandler中存储的格式对应
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
        if(!mappingHandler.containsKey(url)) {
            resp.getWriter().write("404 not found");
            return;
        }

        Method method = this.mappingHandler.get(url);
        // 1. 建立形参的位置和参数的名字建立映射关系（RequestParam中的）
        Map<String, Integer> paramIndexMapping = new HashMap<>();

        // 一个方法上可以有多个参数(第一维），而一个参数上可能有多个注解（第二维）,因此是个二维数组
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Parameter[] parameters = method.getParameters();

        for(int i = 0;i < parameterAnnotations.length; ++ i){   // 第 i 个参数
            for(Annotation a:parameterAnnotations[i]){   // 第i个参数上的的注解
                if(a instanceof RequestParam){
                    String paramName = parameters[i].getName();
                    if(!"".equals(((RequestParam) a).value())){
                        paramName = ((RequestParam) a).value();
                    }

                    paramIndexMapping.put(paramName, i);
                }
            }
        }
        // 没有注解的参数
        Class<?>[] parameterTypes = method.getParameterTypes();
        for(int i = 0;i < parameterTypes.length; ++ i){
            Class<?> type = parameterTypes[i];
            if(type == HttpServletRequest.class || type == HttpServletResponse.class){
                paramIndexMapping.put(type.getName(), i);
            }
        }

        // 2. 根据参数位置匹配参数名，从url中取参数的值
        Object[] paramsValues = new Object[parameterTypes.length];

        for(Map.Entry<String, String[]> param : parameterMap.entrySet()){
            String value = Arrays.toString(param.getValue())
                    .replaceAll("\\[|\\]","")
                    .replaceAll("\\s","");

            if(!paramIndexMapping.containsKey(param.getKey())){ continue; }

            int index = paramIndexMapping.get(param.getKey());
            paramsValues[index] = value;
        }
        if(paramIndexMapping.containsKey(HttpServletRequest.class.getName())){
            int index = paramIndexMapping.get(HttpServletRequest.class.getName());
            paramsValues[index] = req;
        }
        if(paramIndexMapping.containsKey(HttpServletResponse.class.getName())){
            int index = paramIndexMapping.get(HttpServletResponse.class.getName());
            paramsValues[index] = resp;
        }


        // 3. 组成动态实际参数列表
        String beanName = StringUtil.toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioC.get(beanName), paramsValues);

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1. 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        // 2. 扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        // 3. 初始化IoC容器，实例化扫描到的Bean，缓存到IoC容器中
        doInstantiate();
        // 4. 依赖注入
        doDependencyInjection();
        // 5. 初始化MappingHandler
        doInitMappingHandler();
    }

    private void doInitMappingHandler() {
        if(ioC.isEmpty()) { return; }

        for(Map.Entry<String,Object> entry : ioC.entrySet()){
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(Controller.class)) { continue; }

            String baseUrl = "";
            if(clazz.isAnnotationPresent(RequestMapping.class)){
                baseUrl = clazz.getAnnotation(RequestMapping.class).value();
            }

            // 只迭代public方法
            for(Method method: clazz.getMethods()){
                if(!method.isAnnotationPresent(RequestMapping.class)) { continue; }

                String url = baseUrl + method.getAnnotation(RequestMapping.class).value();
                mappingHandler.put(url, method);
            }

        }
    }

    /**
     * 依赖注入
     */
    private void doDependencyInjection() {
        if(ioC.isEmpty()) { return; }

        for(Map.Entry<String,Object> entry : ioC.entrySet()){
            for(Field field : entry.getValue().getClass().getDeclaredFields()){
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }

                Autowired autowired = field.getAnnotation(Autowired.class);
                String beanName = field.getType().getName();
                if(!"".equals(autowired.value())){
                    beanName = autowired.value();
                }

                field.setAccessible(true);
                try {

                    field.set(entry.getValue(), ioC.get(beanName));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * 初始化IoC容器，实例化扫描到的Bean
     */
    private void doInstantiate() {
        if(classNames.isEmpty()) return;

        for(String clasName: classNames) {
            try {
                Class<?> clazz = Class.forName(clasName);
                if(!clazz.isAnnotationPresent(Component.class)){ continue; }
                Object instance = clazz.newInstance();
                Component component = clazz.getAnnotation(Component.class);
                String beanName = StringUtil.toLowerFirstCase(clazz.getSimpleName());

                if(!"".equals(component.value())){
                    beanName = component.value();

                }
                if(ioC.containsKey(beanName)){
                    throw new RuntimeException(beanName + " is already exist!");
                }
                ioC.put(beanName, instance);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 扫描包
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        URL resource = this.getClass().getClassLoader().getResource(scanPackage);
        File basePackage = new File(resource.getFile());

        for(File file: basePackage.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
                continue;
            }
            if(!file.getName().endsWith(".class")) { continue; }
            String className = (scanPackage + "." + file.getName().replace(".class", ""));
            this.classNames.add(className);
        }

    }

    /**
     * 根据contextConfigLocation指定的路径找到对应的文件
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream resource = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(resource);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if(null != resource){
                try {
                    resource.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
