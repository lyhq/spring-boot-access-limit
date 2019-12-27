> 原文作者：rstyro  
> 原文链接： https://rstyro.github.io/blog/2019/04/15/api接口防刷/

> 接口防刷，通俗点理解就是限制某个用户在某段时间内访问某个接口的次数。

### 实现思路及方法
- **对请求来源的ip请求次数做限制；**
- 使用网关控制流量洪峰，对于在一个时间段内出现流量异常的用户，可以拒绝其请求；
- 对http请求头进行信息校验；（例如host，User-Agent，Referer）；
- 对用户唯一身份标识进行限制和校验，如uid，token等；
- 前后端协议采用签名机制，签名可以保障请求URL的完整安全，签名匹配再继续下一步操作；
- 验证码（最简单有效的防护），采用点触验证，旋转验证，滑动验证或第三方验证码服务，普通数字字母验证码很容易被破解；
- 使用黑名单或者恶意IP库，对于黑名单用户，限制其操作，API接口直接返回默认结果避免浪费资源；
- 针对可疑用户要求其主动发短信（或其他主动行为）来验证身份；
- 在数据安全方面做好策略，攻击者得不到有效数据，提高攻击者成本；
- ......
### 案例
下面通过代码实现**对请求来源的ip请求次数做限制**。
整个案例的原理就是服务器通过redis记录下用户请求的次数，如果请求次数超过限制次数就不让访问。在redis保存的key是有时效性的，过期就会删除；使用ip作为key, 访问次数作为value保存在redis中。
#### 自定义注解 @AccessLimit
```java
@Documented
@Inherited
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessLimit {
    // 在 second 秒内，最大只能请求 maxCount 次
    int second() default 1;

    int maxCount() default 1;

    //是否需要登录
    boolean needLogin() default true;
}
```
#### 自定义限流拦截器 AccessLimitInterceptor
使用自定义拦截器对请求进行次数校验；
```java
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
//        return super.preHandle(request, response, handler);
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
```
自定义拦截器写好，接下来注册下。
#### 自定义配置类 WebMvcConfig
使用自定义配置类将拦截器注册进去
```java
@Slf4j
@Component
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AccessLimitInterceptor accessLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("注册自定义拦截...");
        registry.addInterceptor(accessLimitInterceptor);
    }
}
```
#### 测试接口 Controller
**使用方式：**
- 第一种：直接在类上使用注解@RequestLimit(maxCount = 5, second = 1)
- 第二种：在方法上使用注解@RequestLimit(maxCount = 5, second = 1)
默认1秒内，每个接口只能请求一次;
```java
@RestController
@RequestMapping("/index")
@AccessLimit(maxCount = 5, second = 1)
public class IndexController {

    /**
     * @return
     * @AccessLimit 修饰在方法上，优先使用其参数
     */
    @GetMapping("/test1")
    @AccessLimit
    public Result test() {
        return Result.ok();
    }

    /**
     * @return
     * @AccessLimit 修饰在类上，用的是类的参数
     */
    @GetMapping("/test2")
    public Result test2() {
        return Result.ok();
    }
}
```
#### 测试
启动应用程序，使用postman测试；  
**正常访问的情况**
![](https://raw.githubusercontent.com/lyhq/pigureBed/master/imgs/20191227110435.png)  

**访问受限的情况**
![](https://raw.githubusercontent.com/lyhq/pigureBed/master/imgs/20191227110403.png)

> 代码地址：