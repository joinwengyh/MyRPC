package com.weng.server;

import com.weng.common.User;
import com.weng.service.UserService;

import java.util.Random;
import java.util.UUID;

/**
 * @Author Hua
 * @Date: 2022/1/24 10:14
 */
public class UserServiceImpl implements UserService {
    @Override
    public User getUserByUserId(Integer id) {
        System.out.println("客户端查询了"+id+"的用户");
        // 模拟从数据库中取用户的行为
        Random random = new Random();
        User user = User.builder().userName(UUID.randomUUID().toString())
                .id(id)
                .sex(random.nextBoolean()).build();

        return user;
    }
}
