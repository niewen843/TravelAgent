package com.niewen.travelagent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 启动管理员初始化配置，默认关闭。不得生成包含 password 的 toString。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "travel.admin.bootstrap")
public class AdminBootstrapProperties {
    /** 仅首次部署启用，普通运行不需要提供初始化密码。 */
    private boolean enabled;
    /** 初始化的规范化用户名，不允许占用已有普通用户名称。 */
    private String username = "";
    /** 来自本地私有配置或部署环境，必须通过与注册相同的密码规则校验。 */
    private String password = "";
}
