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

package com.nageoffer.shortlink.admin.common.web;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.nageoffer.shortlink.admin.common.convention.exception.AbstractException;
import com.nageoffer.shortlink.admin.common.convention.result.Result;
import com.nageoffer.shortlink.admin.common.convention.result.Results;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;
import java.util.Optional;

/**
 * 全局异常处理器，用于捕获并处理应用程序中抛出的异常。
 * 通过@RestControllerAdvice注解，将该类声明为一个处理所有REST控制器中异常的建议类。
 */

@Component("globalExceptionHandlerByAdmin")
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理方法参数验证异常。
     * 当客户端提交的数据不符合验证规则时，会抛出MethodArgumentNotValidException异常。
     *
     * @param request HTTP请求对象，用于获取请求方法和URL。
     * @param ex MethodArgumentNotValidException异常实例，包含验证失败的详细信息。
     * @return 返回一个Result对象，包含错误代码和错误消息。
     */
    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    /**
     * 处理自定义的抽象异常。
     * 抽象异常类AbstractException是所有自定义业务异常的基类。
     *
     * @param request HTTP请求对象，用于获取请求方法和URL。
     * @param ex AbstractException异常实例，包含业务错误的详细信息。
     * @return 返回一个Result对象，包含错误代码和错误消息。
     */
    /**
     * 拦截应用内抛出的异常
     */
    @ExceptionHandler(value = {AbstractException.class})
    public Result abstractException(HttpServletRequest request, AbstractException ex) {
        if (ex.getCause() != null) {
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex.toString(), ex.getCause());
            return Results.failure(ex);
        }
        log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex.toString());
        return Results.failure(ex);
    }

    /**
     * 处理所有未被其他异常处理器捕获的异常。
     * 这是一个通用的异常处理器，用于处理运行时异常和其他未预料到的异常。
     *
     * @param request HTTP请求对象，用于获取请求方法和URL。
     * @param throwable 抛出的异常对象，可以是任何类型的异常。
     * @return 返回一个Result对象，根据异常类型返回不同的错误代码和消息。
     */
    /**
     * 拦截未捕获异常
     */
    @ExceptionHandler(value = Throwable.class)
    public Result defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        // 注意，此处是为了聚合模式添加的代码，正常不需要该判断
        if (Objects.equals(throwable.getClass().getSuperclass().getSimpleName(), AbstractException.class.getSimpleName())) {
            String errorCode = ReflectUtil.getFieldValue(throwable, "errorCode").toString();
            String errorMessage = ReflectUtil.getFieldValue(throwable, "errorMessage").toString();
            return Results.failure(errorCode, errorMessage);
        }
        return Results.failure();
    }

    /**
     * 获取完整的请求URL，包括查询参数。
     *
     * @param request HTTP请求对象。
     * @return 返回完整的请求URL。
     */
    private String getUrl(HttpServletRequest request) {
        if (StringUtils.isEmpty(request.getQueryString())) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }
}