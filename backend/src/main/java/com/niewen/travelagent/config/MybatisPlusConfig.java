package com.niewen.travelagent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 扫描 Mapper 接口生成代理；XML 路径及字段映射行为由 application.yaml 配置。 */
@Configuration
@MapperScan("com.niewen.travelagent.mapper")
public class MybatisPlusConfig {
    // 当前 XML 使用显式 LIMIT 游标分页，不配置额外的分页拦截器，避免 SQL 被重复改写。
}
