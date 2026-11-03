package com.niewen.travelagent.runner;

import com.niewen.travelagent.config.properties.AdminBootstrapProperties;
import com.niewen.travelagent.service.UserService;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 首次部署的受控启动任务，只负责触发初始化，业务事务位于 UserServiceImpl。 */
@Component
@ConditionalOnProperty(name = "travel.admin.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private final UserService users;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapRunner(UserService users, AdminBootstrapProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 不打印用户名密码配置对象；密码只传入服务进行哈希，数据库不保存明文。
        // 初始化会校验同名账号角色，重复启动不会覆盖已有管理员的密码。
        users.initializeAdmin(properties.getUsername(), properties.getPassword(), UUID.randomUUID().toString());
    }
}
