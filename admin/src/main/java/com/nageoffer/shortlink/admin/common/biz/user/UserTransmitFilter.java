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

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.IOException;


/**
 * 用户信息传输过滤器，用于在请求处理前获取并存储用户信息。
 * 实现了Filter接口，以过滤HTTP请求中的用户信息。
 */
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {

    /**
     * 处理过滤请求。
     * 从HTTP请求头中提取用户信息，如用户名、用户ID和真实姓名，并存储在用户上下文中。
     * 在过滤链执行完毕后，清除用户上下文中的用户信息。
     *
     * @param servletRequest HTTP请求对象
     * @param servletResponse HTTP响应对象
     * @param filterChain 过滤链对象，用于继续执行后续过滤器或终端请求处理
     * @throws IOException 如果在处理请求或响应时发生I/O错误
     * @throws ServletException 如果在处理请求或响应时发生Servlet相关错误
     */
    @SneakyThrows
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
        // 将ServletRequest转换为HttpServletRequest，以便访问HTTP特定方法
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

        // 从请求头中获取用户名
        String username = httpServletRequest.getHeader("username");
        // 如果用户名存在且不为空白，则进一步处理
        if (StrUtil.isNotBlank(username)) {
            // 从请求头中获取用户ID和真实姓名
            String userId = httpServletRequest.getHeader("userId");
            String realName = httpServletRequest.getHeader("realName");
            // 创建用户信息DTO，并填充获取到的用户信息
            UserInfoDTO userInfoDTO = new UserInfoDTO(userId, username, realName);
            // 将用户信息存储在用户上下文中，以供后续处理使用
            UserContext.setUser(userInfoDTO);
        }
        try {
            // 继续执行过滤链中的下一个过滤器或目标Servlet
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            // 请求处理完成后，清除用户上下文中的用户信息
            UserContext.removeUser();
        }
    }
}