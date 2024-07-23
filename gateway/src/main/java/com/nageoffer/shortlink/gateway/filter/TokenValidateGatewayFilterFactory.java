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

package com.nageoffer.shortlink.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nageoffer.shortlink.gateway.config.Config;
import com.nageoffer.shortlink.gateway.dto.GatewayErrorResult;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * SpringCloud Gateway Token 拦截器
 *
 * 该类继承自 AbstractGatewayFilterFactory，用于创建一个自定义的网关过滤器工厂，
 * 该过滤器工厂用于验证请求中的 Token，并根据验证结果决定是否继续处理请求。
 */
@Component
public class TokenValidateGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> {

    // 注入 StringRedisTemplate，用于操作 Redis 中的数据
    private final StringRedisTemplate stringRedisTemplate;

    // 构造函数，初始化过滤器工厂并注入 StringRedisTemplate
    public TokenValidateGatewayFilterFactory(StringRedisTemplate stringRedisTemplate) {
        super(Config.class);
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 应用过滤器逻辑
     *
     * @param config 配置对象，包含过滤器的配置信息
     * @return 返回一个 GatewayFilter 对象，包含过滤器的具体逻辑
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 获取当前请求
            ServerHttpRequest request = exchange.getRequest();
            // 获取请求路径
            String requestPath = request.getPath().toString();
            // 获取请求方法
            String requestMethod = request.getMethod().name();

            // 检查请求路径是否在白名单中
            if (!isPathInWhiteList(requestPath, requestMethod, config.getWhitePathList())) {
                // 从请求头中获取用户名和 Token
                String username = request.getHeaders().getFirst("username");
                String token = request.getHeaders().getFirst("token");
                Object userInfo;

                // 检查用户名和 Token 是否存在，并从 Redis 中获取用户信息
                if (StringUtils.hasText(username) && StringUtils.hasText(token) && (userInfo = stringRedisTemplate.opsForHash().get("short-link:login:" + username, token)) != null) {
                    // 将用户信息转换为 JSONObject
                    JSONObject userInfoJsonObject = JSON.parseObject(userInfo.toString());
                    // 修改请求头，添加 userId 和 realName
                    ServerHttpRequest.Builder builder = exchange.getRequest().mutate().headers(httpHeaders -> {
                        httpHeaders.set("userId", userInfoJsonObject.getString("id"));
                        httpHeaders.set("realName", URLEncoder.encode(userInfoJsonObject.getString("realName"), StandardCharsets.UTF_8));
                    });
                    // 继续处理请求
                    return chain.filter(exchange.mutate().request(builder.build()).build());
                }

                // 如果 Token 验证失败，返回 401 状态码和错误信息
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.writeWith(Mono.fromSupplier(() -> {
                    DataBufferFactory bufferFactory = response.bufferFactory();
                    GatewayErrorResult resultMessage = GatewayErrorResult.builder()
                            .status(HttpStatus.UNAUTHORIZED.value())
                            .message("Token validation error")
                            .build();
                    return bufferFactory.wrap(JSON.toJSONString(resultMessage).getBytes());
                }));
            }

            // 如果请求路径在白名单中，直接继续处理请求
            return chain.filter(exchange);
        };
    }

    /**
     * 检查请求路径是否在白名单中
     *
     * @param requestPath 请求路径
     * @param requestMethod 请求方法
     * @param whitePathList 白名单路径列表
     * @return 如果请求路径在白名单中，返回 true，否则返回 false
     */
    private boolean isPathInWhiteList(String requestPath, String requestMethod, List<String> whitePathList) {
        // 检查请求路径是否在白名单中，或者请求路径和方法是特定的注册路径
        return (!CollectionUtils.isEmpty(whitePathList) && whitePathList.stream().anyMatch(requestPath::startsWith)) ||
                (Objects.equals(requestPath, "/api/short-link/admin/v1/user") && Objects.equals(requestMethod, "POST"));
    }
}
