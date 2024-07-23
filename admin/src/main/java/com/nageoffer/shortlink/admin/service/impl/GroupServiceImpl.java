package com.nageoffer.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nageoffer.shortlink.admin.common.biz.user.UserContext;
import com.nageoffer.shortlink.admin.common.convention.exception.ClientException;
import com.nageoffer.shortlink.admin.common.convention.result.Result;
import com.nageoffer.shortlink.admin.dao.entity.GroupDO;
import com.nageoffer.shortlink.admin.dao.mapper.GroupMapper;
import com.nageoffer.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.nageoffer.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.nageoffer.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.nageoffer.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.nageoffer.shortlink.admin.remote.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.nageoffer.shortlink.admin.service.GroupService;
import com.nageoffer.shortlink.admin.toolkit.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.nageoffer.shortlink.admin.common.constant.RedisCacheConstant.LOCK_GROUP_CREATE_KEY;

/**
 * 分组服务实现类，继承自ServiceImpl，实现了GroupService接口。
 * 该类提供了分组的创建、查询、更新、删除和排序等功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService; // 短链接远程服务
    private final RedissonClient redissonClient; // Redisson客户端

    @Value("${short-link.group.max-num}") // 最大分组数配置
    private Integer groupMaxNum;

    /**
     * 保存分组，如果没有指定用户名，则使用当前登录用户的用户名。
     *
     * @param groupName 分组名称
     */
    @Override
    public void saveGroup(String groupName) {
        saveGroup(UserContext.getUsername(), groupName);
    }

    /**
     * 保存分组。
     *
     * @param username 用户名
     * @param groupName 分组名称
     */
    @Override
    public void saveGroup(String username, String groupName) {
        RLock lock = redissonClient.getLock(String.format(LOCK_GROUP_CREATE_KEY, username)); // 获取用户名对应的锁
        lock.lock(); // 加锁
        try {
            // 查询已有的分组数量，判断是否超过最大限制
            LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                    .eq(GroupDO::getUsername, username)
                    .eq(GroupDO::getDelFlag, 0);
            List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
            if (CollUtil.isNotEmpty(groupDOList) && groupDOList.size() == groupMaxNum) {
                throw new ClientException(String.format("已超出最大分组数：%d", groupMaxNum));
            }
            // 生成唯一的group ID
            String gid;
            do {
                gid = RandomGenerator.generateRandom();
            } while (!hasGid(username, gid));
            // 创建新的分组记录并保存
            GroupDO groupDO = GroupDO.builder()
                    .gid(gid)
                    .sortOrder(0)
                    .username(username)
                    .name(groupName)
                    .build();
            baseMapper.insert(groupDO);
        } finally {
            lock.unlock(); // 解锁
        }
    }

    /**
     * 查询用户的分组列表，并统计每个分组下的短链接数量。
     *
     * @return 分组响应数据列表
     */
    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        // 查询用户的分组列表，按排序和更新时间降序
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getDelFlag, 0)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .orderByDesc(GroupDO::getSortOrder, GroupDO::getUpdateTime);
        List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
        // 调用远程服务，获取每个分组的短链接数量
        Result<List<ShortLinkGroupCountQueryRespDTO>> listResult = shortLinkActualRemoteService
                .listGroupShortLinkCount(groupDOList.stream().map(GroupDO::getGid).toList());
        // 将分组数据转换为响应数据，并填充短链接数量
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList = BeanUtil.copyToList(groupDOList, ShortLinkGroupRespDTO.class);
        shortLinkGroupRespDTOList.forEach(each -> {
            Optional<ShortLinkGroupCountQueryRespDTO> first = listResult.getData().stream()
                    .filter(item -> Objects.equals(item.getGid(), each.getGid()))
                    .findFirst();
            first.ifPresent(item -> each.setShortLinkCount(first.get().getShortLinkCount()));
        });
        return shortLinkGroupRespDTOList;
    }

    /**
     * 更新分组信息。
     *
     * @param requestParam 更新请求参数
     */
    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam) {
        // 更新分组名称
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, requestParam.getGid())
                .eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        groupDO.setName(requestParam.getName());
        baseMapper.update(groupDO, updateWrapper);
    }

    /**
     * 删除分组，实际上将分组的删除标志设置为1。
     *
     * @param gid 分组ID
     */
    @Override
    public void deleteGroup(String gid) {
        // 将指定分组的删除标志设置为1
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(1);
        baseMapper.update(groupDO, updateWrapper);
    }

    /**
     * 更新分组排序。
     *
     * @param requestParam 分组排序请求参数列表
     */
    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {
        // 遍历请求参数，更新每个分组的排序值
        requestParam.forEach(each -> {
            GroupDO groupDO = GroupDO.builder()
                    .sortOrder(each.getSortOrder())
                    .build();
            LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                    .eq(GroupDO::getUsername, UserContext.getUsername())
                    .eq(GroupDO::getGid, each.getGid())
                    .eq(GroupDO::getDelFlag, 0);
            baseMapper.update(groupDO, updateWrapper);
        });
    }

    /**
     * 检查指定的group ID是否已被使用。
     *
     * @param username 用户名，如果为null，则使用当前登录用户的用户名
     * @param gid 分组ID
     * @return 如果分组ID未被使用，返回true；否则返回false
     */
    private boolean hasGid(String username, String gid) {
        // 根据用户名和group ID查询分组信息，判断是否存在
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, Optional.ofNullable(username).orElse(UserContext.getUsername()));
        GroupDO hasGroupFlag = baseMapper.selectOne(queryWrapper);
        return hasGroupFlag == null;
    }
}