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

package com.nageoffer.shortlink.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.shortlink.admin.common.biz.user.UserContext;
import com.nageoffer.shortlink.admin.common.convention.exception.ServiceException;
import com.nageoffer.shortlink.admin.common.convention.result.Result;
import com.nageoffer.shortlink.admin.dao.entity.GroupDO;
import com.nageoffer.shortlink.admin.dao.mapper.GroupMapper;
import com.nageoffer.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.nageoffer.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.nageoffer.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.nageoffer.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 回收站服务的实现类，提供短链接回收站的分页查询功能。
 */
@Service(value = "recycleBinServiceImplByAdmin")
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    /**
     * 短链接实际服务的远程调用接口，用于执行与短链接相关的操作。
     */
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    /**
     * 分组数据访问对象，用于查询用户的分组信息。
     */
    private final GroupMapper groupMapper;

    /**
     * 分页查询用户短链接回收站中的短链接。
     *
     * @param requestParam 查询请求参数，包含分页信息和过滤条件。
     * @return 分页查询结果，包含短链接的详细信息。
     * @throws ServiceException 如果用户没有分组信息，则抛出异常。
     */
    @Override
    public Result<Page<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        // 构建查询条件，查询当前用户的所有未删除的分组
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, 0);
        // 根据查询条件获取用户的所有分组
        List<GroupDO> groupDOList = groupMapper.selectList(queryWrapper);
        // 如果用户没有分组，则抛出异常
        if (CollUtil.isEmpty(groupDOList)) {
            throw new ServiceException("用户无分组信息");
        }
        // 将用户的分组ID列表设置到请求参数中，用于后续的查询过滤
        requestParam.setGidList(groupDOList.stream().map(GroupDO::getGid).toList());
        // 调用远程服务，分页查询用户回收站中的短链接
        return shortLinkActualRemoteService.pageRecycleBinShortLink(requestParam.getGidList(), requestParam.getCurrent(), requestParam.getSize());
    }
}