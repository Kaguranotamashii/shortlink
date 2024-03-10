package com.nageoffer.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nageoffer.shortlink.admin.dao.entity.UserDO;
import com.nageoffer.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.nageoffer.shortlink.admin.dto.resp.UserRespDTO;

public interface UserService extends IService<UserDO> {


    /**
     * 根据用户查询信息
     * @param username 用户名
     * @return 用户返回信息
     */
    UserRespDTO getUserByUsername(String username);


    /**
     * 判断用户是否存在
     * @param username 用户名
     * @return 存在true 不存在false
     */
    Boolean hasUsername(String username);


    /**
     * 注册
     * @param userRegisterReqDTO 用户注册信息
     */
    void register(UserRegisterReqDTO userRegisterReqDTO);

}
