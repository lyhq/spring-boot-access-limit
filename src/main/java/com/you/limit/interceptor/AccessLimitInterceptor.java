package com.you.limit.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.you.limit.annotation.AccessLimit;
import com.you.limit.common.ApiResultEnum;
import com.you.limit.common.Result;
import com.you.limit.utils.AdrressIpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 请求拦截
 */
@Slf4j
@Component
public class AccessLimitInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /**
         * isAssignableFrom() 判定此 Class 对象所表示的类或接口与指定的 Class 参数所表示的类或接口是否相同，或是否是其超类或超接口
         * isAssignableFrom()方法是判断是否为某个类的父类
         * instanceof关键字是判断是否某个类的子类
         */
        if (handler.getClass().isAssignableFrom(HandlerMethod.class)) {
            //HandlerMethod 封装方法定义相关的信息,如类,方法,参数等
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();
            // 如果方法上有注解就优先选择方法上的参数，否则取类上的参数
            AccessLimit accessLimit = getTagAnnotation(method, AccessLimit.class);
            if (accessLimit != null) {
                //判断是否限制访问
                if (isLimit(request, accessLimit)) {
                    resonseOut(response, Result.error(ApiResultEnum.REQUST_LIMIT));
                    return false;
                }
            }
        }
        return true;
    }

    //判断请求是否受限
    public boolean isLimit(HttpServletRequest request, AccessLimit accessLimit) {
        //这里使用用户ip地址作为redis的key,访问次数作为value;也可以使用 用户ID 之类的唯一标识。
        String ipAdrress = AdrressIpUtils.getIpAdrress(request);
        String limitKey = request.getContextPath() + ":" + request.getServletPath() + ":" + ipAdrress ;
//        String limitKey = request.getServletPath() + request.getSession().getId();
        // 从缓存中获取，当前这个请求访问了几次
        Integer redisCount = (Integer) redisTemplate.opsForValue().get(limitKey);
        if (redisCount == null) {
            //初始次数
            redisTemplate.opsForValue().set(limitKey, 1, accessLimit.second(), TimeUnit.SECONDS);
        } else {
            if (redisCount.intValue() >= accessLimit.maxCount()) {
                //访问受限
                return true;
            }
            // 次数自增
            redisTemplate.opsForValue().increment(limitKey);
        }
        return false;
    }

    /**
     * 获取目标注解
     * 如果方法上有注解就返回方法上的注解配置，否则就取类上的配置
     *
     * @param method
     * @param annotationClass
     * @param <A>
     * @return
     */
    public <A extends Annotation> A getTagAnnotation(Method method, Class<A> annotationClass) {
        // 获取方法中是否包含指定注解
        Annotation methodAnnotate = method.getAnnotation(annotationClass);
        //获取类中是否包含注解，也就是controller是否标有注解
        Annotation classAnnotate = method.getDeclaringClass().getAnnotation(annotationClass);
        return (A) (methodAnnotate != null ? methodAnnotate : classAnnotate);
    }

    /**
     * 回写给客户端
     *
     * @param response
     * @param result
     * @throws IOException
     */
    private void resonseOut(HttpServletResponse response, Result result) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        PrintWriter out = null;
        String json = JSONObject.toJSON(result).toString();
        out = response.getWriter();
        out.append(json);
    }
}
