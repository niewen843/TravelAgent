package com.niewen.travelagent.service;

import com.niewen.travelagent.dto.ChangePasswordDTO;
import com.niewen.travelagent.dto.RegisterDTO;
import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.vo.UserVO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 账号业务契约，控制器和认证适配器依赖接口，不直接操作 Mapper。 */
public interface UserService {
    /** 校验输入并创建普通用户，账号和成功审计在同一事务提交；不会自动登录。 */
    UserVO register(RegisterDTO input, String traceId);
    /** 认证内部使用，返回实体包含哈希，不能直接作为 HTTP 响应。 */
    Optional<AppUser> findAccountByUsername(String normalizedUsername);
    /** 会话守卫每次请求重新读取账号状态，不复用 Session 中的账号快照。 */
    Optional<AppUser> findAccountById(long id);
    /** 当前身份不存在时抛出会话失效异常，仅返回内部实体。 */
    AppUser requireAccount(long id);
    /** 当前身份的公开投影；调用方必须使用认证主体的 ID。 */
    UserVO currentUser(long id);
    /** 以下两个跨账号查询在实现上有 ADMIN 方法鉴权。 */
    List<UserVO> adminList(long afterId, int limit);
    UserVO adminAccount(long id);
    /** 验证旧密码并原子修改哈希和版本；提交后由调用方撤销 Redis 会话。 */
    void changePassword(long id, ChangePasswordDTO input, String traceId);
    /** 写入最近登录时间和审计，并返回刷新后的公开信息。 */
    UserVO recordSuccessfulLogin(long id, String traceId);
    /** 管理重置的事务部分：校验管理员版本、更新目标、记录审计，返回临时密码截止时间。 */
    Instant resetUserPassword(long actor, long actorVersion, long target, String temporaryPassword, String traceId);
    /** 仅由受控启动初始化调用，不提供公开 HTTP 入口。 */
    void initializeAdmin(String username, String password, String traceId);
}
