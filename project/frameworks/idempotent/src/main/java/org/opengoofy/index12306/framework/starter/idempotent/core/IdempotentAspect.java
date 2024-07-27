/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengoofy.index12306.framework.starter.idempotent.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.opengoofy.index12306.framework.starter.idempotent.annotation.Idempotent;

import java.lang.reflect.Method;

/**
 * 幂等注解 AOP 拦截器
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Aspect
public final class IdempotentAspect {

    /**
     * 增强方法标记 {@link Idempotent} 注解逻辑
     * 使用 AOP 围绕注解 {@link Idempotent} 的方法执行，提供幂等性处理。
     * 幂等性处理主要通过检查是否已经执行过相同的操作来实现，以避免重复操作带来的问题。
     *
     * @param joinPoint 切面连接点，包含目标方法的信息。
     * @return 目标方法的返回值，或者在幂等性检查失败时返回特定值或抛出异常。
     * @throws Throwable 如果目标方法执行过程中抛出异常，则将异常抛出。
     */
    @Around("@annotation(org.opengoofy.index12306.framework.starter.idempotent.annotation.Idempotent)")
    public Object idempotentHandler(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法上的幂等性注解实例，用于后续的幂等性检查和处理。
        Idempotent idempotent = getIdempotent(joinPoint);
        // 根据幂等性注解的配置，获取对应的幂等性处理实例。
        IdempotentExecuteHandler instance = IdempotentExecuteHandlerFactory.getInstance(idempotent.scene(), idempotent.type());
        Object resultObj;
        try {
            // 执行幂等性检查和处理，这可能包括检查是否已经执行过相同操作等。
            instance.execute(joinPoint, idempotent);
            // 继续执行目标方法，即环绕通知中的proceed()。
            resultObj = joinPoint.proceed();
            // 目标方法执行成功后，进行后续的处理，例如清理状态等。
            instance.postProcessing();
        } catch (RepeatConsumptionException ex) {
            // 处理幂等性检查失败的情况，根据错误标志决定是返回特定值还是重新抛出异常。
            /**
             * 触发幂等逻辑时可能有两种情况：
             *    * 1. 消息还在处理，但是不确定是否执行成功，那么需要返回错误，方便 RocketMQ 再次通过重试队列投递
             *    * 2. 消息处理成功了，该消息直接返回成功即可
             */
            if (!ex.getError()) {
                return null;
            }
            throw ex;
        } catch (Throwable ex) {
            // 处理目标方法执行过程中抛出的异常，进行幂等性相关的异常处理。
            // 客户端消费存在异常，需要删除幂等标识方便下次 RocketMQ 再次通过重试队列投递
            instance.exceptionProcessing();
            throw ex;
        } finally {
            // 无论操作成功还是失败，最后都需要清理幂等性相关的上下文状态。
            IdempotentContext.clean();
        }
        return resultObj;
    }


    /**
     * 通过 ProceedingJoinPoint 参数获取方法上的 Idempotent 注解。
     * 此方法用于检查给定方法是否被标记为幂等的，幂等性注解用于指示方法多次调用具有相同效果。
     *
     * @param joinPoint AOP 的 ProceedingJoinPoint 对象，包含当前执行的方法信息。
     * @return 返回 Method 上的 Idempotent 注解，如果方法没有被注解，则返回 null。
     * @throws NoSuchMethodException 如果无法找到目标方法，则抛出此异常。
     */
    public static Idempotent getIdempotent(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        // 将 ProceedingJoinPoint 转换为 MethodSignature，以便获取方法名称和参数类型。
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();

        // 获取目标对象的类，然后通过方法名称和参数类型获取具体的方法对象。
        // 这一步是为了绕过 Java 的反射访问限制，获取到注解信息。
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());

        // 从方法对象上获取 Idempotent 注解，如果方法没有被注解，则返回 null。
        return targetMethod.getAnnotation(Idempotent.class);
    }
}
