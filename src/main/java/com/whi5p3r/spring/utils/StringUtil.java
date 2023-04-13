package com.whi5p3r.spring.utils;

/**
 * @description: TODO
 * @author: whi5p3r
 * @date: 2023年04月12日 11:43
 */
public class StringUtil {
    /**
     * 首字母转小写
     * @param simpleName 类名
     * @return 结果
     */
    public static String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        chars[0] += 32;     //ASCII码中大写字母和小写相差32
        return String.valueOf(chars);
    }

    public static boolean isEmpty(String str){
        if(null == str || "".equals(str))
            return true;
        return false;
    }
}
