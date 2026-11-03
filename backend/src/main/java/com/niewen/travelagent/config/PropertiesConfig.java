package com.niewen.travelagent.config;

import com.niewen.travelagent.config.properties.AdminBootstrapProperties;
import com.niewen.travelagent.config.properties.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 显式注册自定义配置对象；Spring Boot 根据 YAML/环境变量绑定字段并执行校验。 */
@Configuration
@EnableConfigurationProperties({SecurityProperties.class, AdminBootstrapProperties.class})
public class PropertiesConfig {}
