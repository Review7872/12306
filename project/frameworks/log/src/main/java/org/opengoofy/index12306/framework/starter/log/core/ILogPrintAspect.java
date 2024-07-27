
package org.opengoofy.index12306.framework.starter.log.core;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.SystemClock;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.opengoofy.index12306.framework.starter.log.annotation.ILog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * {@link ILog} 日志打印 AOP 切面
 */
@Aspect
public class ILogPrintAspect {

    /**
     * 方法日志打印器。
     * 该方法在目标方法执行前后记录日志，包括方法的开始时间、结束时间、输入参数和返回值。
     * 支持通过注解配置是否打印输入参数和输出参数。
     *
     * @param joinPoint 切点，包含目标方法的信息和参数。
     * @return 目标方法的返回值。
     * @throws Throwable 目标方法抛出的任何异常。
     */
    @Around("@within(org.opengoofy.index12306.framework.starter.log.annotation.ILog) || @annotation(org.opengoofy.index12306.framework.starter.log.annotation.ILog)")
    public Object printMLog(ProceedingJoinPoint joinPoint) throws Throwable {
        // 记录方法开始执行的时间
        long startTime = SystemClock.now();
        // 获取目标方法的签名信息
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        // 获取目标方法所在的类的Logger实例
        Logger log = LoggerFactory.getLogger(methodSignature.getDeclaringType());
        // 记录当前时间，作为方法开始时间
        String beginTime = DateUtil.now();
        Object result = null;

        try {
            // 执行目标方法，获取结果
            result = joinPoint.proceed();
        } finally {
            // 获取目标方法对象
            Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
            // 尝试获取方法上的ILog注解，如果不存在，则尝试获取类上的ILog注解
            ILog logAnnotation = Optional.ofNullable(targetMethod.getAnnotation(ILog.class)).orElse(joinPoint.getTarget().getClass().getAnnotation(ILog.class));
            // 如果存在ILog注解，则进行日志打印
            if (logAnnotation != null) {
                // 构建日志打印对象
                ILogPrintDTO logPrint = new ILogPrintDTO();
                // 设置方法开始时间
                logPrint.setBeginTime(beginTime);
                // 根据注解配置，判断是否需要打印输入参数
                if (logAnnotation.input()) {
                    logPrint.setInputParams(buildInput(joinPoint));
                }
                // 根据注解配置，判断是否需要打印输出参数
                if (logAnnotation.output()) {
                    logPrint.setOutputParams(result);
                }
                // 尝试获取当前请求的方法类型和URI
                String methodType = "", requestURI = "";
                try {
                    ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    assert servletRequestAttributes != null;
                    methodType = servletRequestAttributes.getRequest().getMethod();
                    requestURI = servletRequestAttributes.getRequest().getRequestURI();
                } catch (Exception ignored) {
                }
                // 打印日志，包括方法类型、请求URI、执行时间以及详细日志信息
                log.info("[{}] {}, executeTime: {}ms, info: {}", methodType, requestURI, SystemClock.now() - startTime, JSON.toJSONString(logPrint));
            }
        }

        // 返回目标方法的执行结果
        return result;
    }

    private Object[] buildInput(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Object[] printArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if ((args[i] instanceof HttpServletRequest) || args[i] instanceof HttpServletResponse) {
                continue;
            }
            if (args[i] instanceof byte[]) {
                printArgs[i] = "byte array";
            } else if (args[i] instanceof MultipartFile) {
                printArgs[i] = "file";
            } else {
                printArgs[i] = args[i];
            }
        }
        return printArgs;
    }
}
