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

package com.nageoffer.shortlink.admin.common.biz.user;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.nageoffer.shortlink.admin.common.convention.exception.ClientException;
import com.nageoffer.shortlink.admin.common.convention.result.Results;
import com.nageoffer.shortlink.admin.config.UserFlowRiskControlConfiguration;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;

import static com.nageoffer.shortlink.admin.common.convention.errorcode.BaseErrorCode.FLOW_LIMIT_ERROR;


/**
 * 用户操作流量风控过滤器。该过滤器用于限制用户在特定时间窗口内的操作频率，超过预设频率时将限制操作。
 * 实现原理是通过Redis的Lua脚本执行，确保操作的原子性和一致性。
 */
@Slf4j
@RequiredArgsConstructor
public class UserFlowRiskControlFilter implements Filter {

    /**
     * Redis字符串模板，用于执行Lua脚本和操作Redis。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 用户操作流量风控配置，包含时间窗口和最大访问次数等参数。
     */
    private final UserFlowRiskControlConfiguration userFlowRiskControlConfiguration;

    /**
     * Lua脚本路径，用于执行用户操作频率控制逻辑。
     */
    private static final String USER_FLOW_RISK_CONTROL_LUA_SCRIPT_PATH = "lua/user_flow_risk_control.lua";

    /**
     * 过滤请求。检查当前用户操作是否超过预设的流量限制。
     * 如果超过限制，返回错误响应；否则，继续处理请求。
     *
     * @param request  Servlet请求对象。
     * @param response Servlet响应对象。
     * @param filterChain 过滤器链，用于继续或停止请求处理。
     * @throws IOException      如果发生I/O错误。
     * @throws ServletException 如果发生Servlet相关错误。
     */
    @SneakyThrows
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        // 初始化Redis脚本，加载Lua脚本并设置结果类型。
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(USER_FLOW_RISK_CONTROL_LUA_SCRIPT_PATH)));
        redisScript.setResultType(Long.class);

        // 获取当前操作用户，如果未登录则视为“other”。
        String username = Optional.ofNullable(UserContext.getUsername()).orElse("other");

        Long result;
        try {
            // 执行Lua脚本，检查用户操作频率。
            result = stringRedisTemplate.execute(redisScript, Lists.newArrayList(username), userFlowRiskControlConfiguration.getTimeWindow());
        } catch (Throwable ex) {
            // 如果执行脚本出错，记录日志并返回错误响应。
            log.error("执行用户请求流量限制LUA脚本出错", ex);
            returnJson((HttpServletResponse) response, JSON.toJSONString(Results.failure(new ClientException(FLOW_LIMIT_ERROR))));
            return;
        }

        // 如果操作频率超过限制，返回错误响应；否则，继续处理请求。
        if (result == null || result > userFlowRiskControlConfiguration.getMaxAccessCount()) {
            returnJson((HttpServletResponse) response, JSON.toJSONString(Results.failure(new ClientException(FLOW_LIMIT_ERROR))));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 将JSON字符串写入HTTP响应。
     *
     * @param response HTTP响应对象。
     * @param json     要写入的JSON字符串。
     * @throws Exception 如果发生I/O错误或其他异常。
     */
    private void returnJson(HttpServletResponse response, String json) throws Exception {
        // 设置响应编码和类型。
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=utf-8");
        // 写入JSON字符串到响应。
        try (PrintWriter writer = response.getWriter()) {
            writer.print(json);
        }
    }
}