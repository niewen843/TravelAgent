package com.niewen.travelagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot 入口；以当前包为根扫描各层组件，Mapper 扫描单独在 MybatisPlusConfig 配置。 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        // 配置加载、数据库连接及可选 admin 初始化交由各自配置/Runner，入口不执行业务 SQL。
        SpringApplication.run(BackendApplication.class, args);
    }

}
