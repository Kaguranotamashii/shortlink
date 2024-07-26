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

package com.nageoffer.shortlink.project.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel规则配置类，用于在应用程序启动后初始化Sentinel流控规则。
 * 该类实现了InitializingBean接口，因此可以在所有属性设置完成后调用afterPropertiesSet方法。
 */
@Component
public class SentinelRuleConfig implements InitializingBean {

    /**
     * 在所有属性被设置后，初始化Sentinel流控规则。
     * 此方法中，我们创建了一个针对创建短链接操作的流控规则，并将其加载到Sentinel中。
     *
     * @throws Exception 如果初始化过程中出现异常，则抛出。
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 初始化流控规则列表
        List<FlowRule> rules = new ArrayList<>();

        // 创建流控规则实例，用于限制"create_short-link"资源的访问速度
        FlowRule createOrderRule = new FlowRule();
        createOrderRule.setResource("create_short-link");
        createOrderRule.setGrade(RuleConstant.FLOW_GRADE_QPS); // 设置规则类型为QPS
        createOrderRule.setCount(1); // 设置通过规则的请求数量阈值为1

        // 将创建的流控规则添加到规则列表
        rules.add(createOrderRule);

        // 加载流控规则列表到Sentinel的FlowRuleManager中
        FlowRuleManager.loadRules(rules);
    }
}