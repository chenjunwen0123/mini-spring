# SpringIoC迷你手写版

## 启动流程

- Spring启动流程

    ```java
    // 解析配置类
    WhApplicationContext context = new WhApplicationContext(MyConfig.class)
    
    // 获取Bean
    Object object = context.getBean("beanName");
    ```

- 创建容器类，根据启动流程完成基本模板

    ```java
    public class WhApplicationContext {
        private Class<?> configClazz;
    		
        public WhApplicationContext(Class<?> clz) {
            this.configClazz = clz;
        }
    
    		public Object getBean(String beanName){
            return null;
        }
    }
    ```


## 解析配置类

- 拿到配置类的信息
    - 自定义以下两个注解：
        - **`@Component`**：标记为组件，注入到IoC容器
        - **`@ComponentScan`**：扫描以 **`@Component`** 标注的类
        
        ```java
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        public @interface Component {
            String value() default "";
        }
        
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        public @interface ComponentScan {
            String basePackages() default "";
        }
        ```
    
- 扫描 **`@ComponentScan`** 指定的basePackages下所有被 **`@Component`** 注解的类
    - 通过 **`configClazz.getAnnotation(ComponentScan.class)`** 获得 configClazz 类上的ComponentScan注解对象，从而获取该对象的 **`basePackages`** 属性值（类似”com.example.spring“这样的全限定包名字符串）
    - 将其转换成一个文件路径的格式（”com/example/spring”)
      
        ```java
        if(configClazz.isAnnotationPresent(ComponentScan.class)){
        		ComponentScan componentScan = configClazz.getAnnotation(ComponentScan.class);
        		String basePackages = componentScan.basePackages();
        		basePackages = basePackages.replace(".","/");
        		...
        }
        ```
        
    - 扫描这个路径下的所有被 **`@Component`** 注解的类
        - **<u>两个问题</u>**：
            
            1. **如何通过包的相对路径扫描文件？**
            2. **如何将文件（File）扫描为类对象（Class）？**
        - **<u>解决问题1</u>**：需要提到JVM中类加载器的概念
            
            - JVM中的类加载器及其加载的路径如下
              
              
                | ClassLoader | Path | Description |
                | --- | --- | --- |
                | BootstrapClassLoader | /jre/lib | 启动类加载器 |
                | ExtClassLoader | /jre/ext/lib | 扩展类加载器 |
                | AppClassLoader | classpath | 应用类加载器，classpath需要运行时指定 |
            - 我们可以通过AppClassLoader，来加载classpath下的资源（Resource）
                - 在IDEA中，默认将应用目录下的 /target/classes作为classpath（可在运行参数中修改）
                - 通过该类加载器对象，可以直接通过包的相对路径（相对classpath）获取该路径下的文件资源
                
                ```java
                // 获取app类加载器
                ClassLoader classLoader = WhApplicationContext.class.getClassLoader();
                // 通过相对路径（相对classpath）获取指定的资源
                URL resource = classLoader.getResource(basePackages);
                ```
                
            - 遍历这些文件资源，首先将所有类文件收集起来
                - 这里通过 dfs 算法，将file下的所有非文件夹文件收集到 **`fileResult`** 这个集合中
                
                ```java
                assert resource != null;
                File file = new File(resource.getFile());
                
                List<File> fileResult = new ArrayList<>();
                dfs(file, fileResult);   // 遍历这个文件目录
                ```
                
                ```java
                // dfs
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
                ```
            
        - **<u>解决问题2</u>**：将文件（File）扫描为类对象（Class）
            
            - 思路：我们可以通过类的文件路径，获取类的全限定类名（如com.example.spring)，然后基于反射，通过类加载器将这个 .class 文件加载为 **`Class<?>`** 对象
            - 遍历 **`fileResult`** 集合，对于其每个File元素 **`f`** ，先获取其绝对路径 **`f.getAbsolutePath()`** ，再通过字符串分割，将其包路径相对于classpath的路径截取下来（如 com\example\spring）
            - 再将该包路径转换为全限定类名的字符串格式（如 com.example.spring)
            
            ```java
            for(File f: fileResult){
            		String fileName = f.getAbsolutePath();
            		if(fileName.endsWith(".class")){
            	      String className = fileName.substring(fileName.indexOf("com"),fileName.indexOf(".class"));
            	      className = className.replace("\\" , ".");
            				...
            		}
            }
            ```
            
            - 获取到该文件 **`f`** 对应的类的全限定类名 **`className`** 后，通过 **`classLoader.loadClass(className)`** 加载该类，并返回类对象 **`Class<?> clz`**
            - 获取到该类的信息后，回归扫描的主题：扫描带有 **`@Component`** 注解的类
                - 通过 **`clz.getAnnotation(Component.class)`** 获取该类上的 **`@Component`** 注解对象，获取其value属性值得到指定的beanName
            
            ```java
            if(clz.isAnnotationPresent(Component.class)){
            		Component component = clz.getAnnotation(Component.class);
                String beanName = component.value();
            }
            ```
            
        - **<u>思考问题</u>**：长远得来看，该组件可能还会有其他注解来约束该Bean（如Scope能约束该Bean是单例还是原型）。则，**如何存储这些扫描到的组件信息和其所拥有的其他注解带来的定义？**
            
            - 结论：封装一个类 **`BeanDefinition`** ，用于存储Bean的定义信息
                - 粗浅地，先考虑存储Bean的类对象以及其Scope
                - 这里Scope使用一个创建一个枚举类来表示（默认为单例，即SINGLETON，可指定原型PROTOTYPE）
                
                ```java
                @Data
                public class BeanDefinition {
                    private Class<?> clazz;
                    private ScopeType scope;
                }
                
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Scope {
                    ScopeType value() default ScopeType.SINGLETON;
                }
                
                public enum ScopeType {
                    SINGLETON(1),
                    PROTOTYPE(2)
                    ;
                    final int key;
                    ScopeType(int key){
                        this.key = key;
                    }
                }
                ```
                
            - 则，在IoC容器中添加一个成员容器，用于存储需要注入到容器的Bean的BeanDefinition对象
              
                ```java
                private Map<String,BeanDefinition> beanDefinitionMap = new HashMap<>();
                ```
                
            - 将当前类对象的信息，封装为 **`BeanDefinition`** 对象，并放入 **`beanDefinitionMap`** 中 ，key为 **`BeanName`**
                - 其中，判断Scope注解的内容时，默认为SINGLETON
                
                ```java
                BeanDefinition beanDefinition = new BeanDefinition();
                beanDefinition.setClazz(clz);
                if(clz.isAnnotationPresent(Scope.class)){
                    Scope scope = clz.getAnnotation(Scope.class);
                    beanDefinition.setScope(scope.value());
                }else{
                    beanDefinition.setScope(ScopeType.SINGLETON);
                }
                beanDefinitionMap.put(beanName, beanDefinition);
                ```
        
    - 完整的扫描代码，整合成函数scan如下
      
        ```java
        private void scan(Class<?> configClazz) {
        		// 如果配置类有@ComponentScan注解
            if(configClazz.isAnnotationPresent(ComponentScan.class)) {
                // 解析配置类
                ComponentScan componentScan = configClazz.getAnnotation(ComponentScan.class);
                String basePackages = componentScan.basePackages();
                basePackages = basePackages.replace(".","/");
        
                // 扫描该basePackages下所有被Component注解的类
                // 获取app类加载器
                ClassLoader classLoader = WhApplicationContext.class.getClassLoader();
                // 通过相对路径（相对classpath）获取指定的资源
                URL resource = classLoader.getResource(basePackages);
        
                assert resource != null;
                File file = new File(resource.getFile());
                
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
                                    beanDefinition.setScope(ScopeType.SINGLETON);
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
        ```
        

## 生成Bean对象

- **<u>问题</u>**：**`getBean()`** 如何根据beanName来获取对应的类
    
    - 在扫描时，存的是 **`BeanDefinition`** 对象，**`beanDefinitionMap`** 中key为beanName，而value为对应的 **`BeanDefinition`** 对象，而 **`BeanDefinition`** 对象中又存有该类的类对象，有该类对象可以通过反射来实例化
- 生成Bean时，单例Bean和多例Bean不能一视同仁
    - 根据单例模式，单例Bean可以从Map里面拿（单例池）
    - **<u>问题</u>**：单例池什么时候初始化？
        
        - 扫描完后，遍历 **`beanDefinitionMap`** 中的所有 **`BeanDefinition`** 对象
        - 只要Scope注解指定为原型的（默认为Singleton），都将其实例化后放入单例池
        - 因此，IoC容器新增一个成员容器 **`singletonObjects`** ，用作单例池，存储单例Bean
          
            ```java
            private Map<String,Object> singletonObjects = new HashMap<>();
            ```
            
        - 容器的构造函数改动如下
          
            ```java
            public WhApplicationContext(Class<?> clz) {
                this.configClazz = clz;
                scan(clz);   // 解析配置类
                preInstantiateSingletons();   // 实例化单例对象
            }
            ```
            
        - 而实例化单例对象的过程封装为函数 **`preInstantiateSingletons()`**
          
            ```java
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
            ```
        
    - 其中实例化Bean的过程单独封装为函数 **`createBean(beanName, beanDefinition)`**
        - 即通过指定的BeanName和Bean定义来实例化Bean
        
        ```java
        private Object createBean(String beanName, BeanDefinition beanDefinition){
            Class<?> clz = beanDefinition.getClazz();
            Object instance = null;
            try {
                // 实例化
                instance = clz.newInstance();
                // 依赖注入
                // 初始化
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
         
            return instance;
        }
        ```
        
    - 则，getBean时，根据指定的Bean是否单例，获取的方式也不同
        - 单例，则从 **`singletonObjects`** 中取
        - 多例，则直接创建一个新的Bean实例
        
        ```java
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
        ```
        

## 依赖注入

- 依赖注入的概念
    - 有 **`@Autowired`** 注解的 组件会被自动装配（属性赋值）
- Autowired注解
    - 标注在字段上 （Field）
    - required属性
        - 若required=false，则在依赖注入时，找不到该组件也不关紧要
    
    ```java
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Autowired {
        boolean required() default true;
    }
    ```
    
- 依赖注入（属性填充）发生在实例化后、初始化前
    - **区别实例化与初始化**
        - 实例化：创建一个对象（通常通过无参构造器创建一个空对象，分配对象内存）
        - 初始化：对对象的属性赋值，在这里的属性特指没有自动装配的属性
    - 在实例化后，在当前类对象的所有字段中，筛选出有 **`@Autowired`** 注解的字段
    - 通过反射，利用该被 **`@Autowired`** 注解的字段的 **`Field`** 对象 **`field`** 对实例的对应字段赋值
        - 注意访问私有字段需要强吻 **`field.setAccessible(true)`**
    
    ```java
    private Object createBean(String beanName, BeanDefinition beanDefinition){
        Class<?> clz = beanDefinition.getClazz();
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
                        throw new NullPointerException("The bean \"" + fieldName + "\" is not found!");
                    }
                    // 注入
                    field.setAccessible(true);
                    field.set(instance, fieldBean);
                }
            }
     
            // 初始化
    
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return instance;
    }
    ```
    

## Bean的初始化

- 后置处理器
    - **<u>问题</u>**：部分属性需要外部得到（比如说从数据库中得到）
    - **<u>需求</u>**：这些需要外部得到的属性，需要在被注入时同时从外部获取值
- **<u>解决方案</u>**：**初始化**，Spring提供了多种初始化方式（后置处理器）
    - 方式1：**`initializingBean`** 接口（init-method）、**`disposableBean`** 接口(destroy-method)
        - 自定义 **`initializingBean`** 接口
          
            ```java
            public interface InitializingBean {
                void afterPropertiesSet();
            }
            ```
            
        - 组件实现该接口进行初始化
          
            ```java
            @Component("userService")
            public class UserService implements InitializingBean {
                @Autowired
                private OrderService orderService;
                
                private String userName;
            
                @Override
                public void afterPropertiesSet() {
                    this.userName = "hello";   
                }
            }
            ```
            
        - 判断当前实例化的类是否实现了 **`InitializingBean`** 接口
            - 是：强制转化成 **`InitializingBean`** 实现类，调用该类定义的 **`afterPropertiesSet`** 方法
            
            ```java
            // 初始化
            if(instance instanceof InitializingBean){
                ((InitializingBean) instance).afterPropertiesSet();
            }
            ```
            

## AOP

- **`BeanPostProcessor`** 接口：后置处理器（后置相对的是实例化，即实例化之后）
    
    - 可以自定义初始化前后的操作
    
    ```java
    public interface BeanPostProcessor {
        Object postProcessBeforeInitialization(Object bean, String beanName);
        Object postProcessAfterInitialization(Object bean, String beanName);
    }
    ```
    
- IoC容器中新增一个成员容器，用于存储IoC上下文所有的 **`BeanPostProcessor`** 组件
  
    ```java
    private List<BeanPostProcessor> beanPostProcessorList = new ArrayList<>();
    ```
    
- 扫描Component时，若遇到实现了 **`BeanPostProcessor`** 接口的类（通过 **`isAssignableFrom()`** 方法），则直接实例化并添加到 **`BeanPostProcessorList`** 中
  
    ```java
    try{
        Class<?> clz = classLoader.loadClass(className);
        if(clz.isAnnotationPresent(Component.class)){
            // 如果当前Component是BeanPostProcessor接口的实现类则直接添加到容器的后处理器集合中
            if(BeanPostProcessor.class.isAssignableFrom(clz)){
                BeanPostProcessor beanPostProcessor = (BeanPostProcessor) clz.newInstance();
                beanPostProcessorList.add(beanPostProcessor);
            }
    				// 存BeanDefinition
            Component component = clz.getAnnotation(Component.class);
            String beanName = component.value();
            BeanDefinition beanDefinition = new BeanDefinition();
            beanDefinition.setClazz(clz);
            if(clz.isAnnotationPresent(Scope.class)){
                Scope scope = clz.getAnnotation(Scope.class);
                beanDefinition.setScope(scope.value());
            }else{
                beanDefinition.setScope(ScopeType.SINGLETON);
            }
            beanDefinitionMap.put(beanName, beanDefinition);
        }
    }catch (ClassNotFoundException e){
        throw new RuntimeException(e);
    } catch (InstantiationException e) {
        throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
    }
    ```
    
- 初始化前后行为
    - 初始化前后，调用容器中所有的 **`BeanPostProcessor`** 实现类的初始化前后的方法
    
    ```java
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
    ```
    
- 附：Spring中还有一个子接口 **`InstantiationAwareBeanPostProcessor`**，定义实例化前后的操作