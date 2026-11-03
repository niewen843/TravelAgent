package com.niewen.travelagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.vo.UserVO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis-Plus Mapper。自定义查询和写入均在 resources/mapper/AppUserMapper.xml。
 * BaseMapper 提供框架基础能力，鉴权相关操作使用下方明确命名的 XML 方法。
 * 参数用 @Param 命名并通过 #{...} 绑定，不用字符串拼接 SQL。
 */
public interface AppUserMapper extends BaseMapper<AppUser> {
    /** 按规范化登录名查找账号，包含认证需要的哈希，仅供内部服务使用。 */
    Optional<AppUser> findByUsername(@Param("username") String username);
    /** 按稳定 ID 查询完整实体；返回 Optional，调用方明确处理不存在的情况。 */
    Optional<AppUser> findById(@Param("id") long id);
    /** 插入账号，MySQL 生成的主键回填 entity.id；角色由业务服务指定。 */
    int insertAccount(AppUser entity);
    /** 使用数据库 UTC 时间记录最近成功登录时间。 */
    int updateLastLogin(@Param("id") long id);
    /**
     * 改密的比较并更新：仅当版本匹配且账号 ACTIVE 时更新，返回 0 表示并发冲突。
     * SQL 同时递增版本、清除首次改密标记，不能拆成几个独立更新。
     */
    int updatePasswordIfVersion(@Param("id") long id, @Param("version") long version,
                                @Param("hash") String hash);
    /** 重置事务内锁定管理员，保证验证所依据的密码版本/权限在提交前不被并发修改。 */
    Optional<AppUser> findByIdForUpdate(@Param("id") long id);
    /** 只允许重置仍 ACTIVE 的 USER，并用版本比较防止覆盖并发改密。 */
    int resetPasswordIfVersion(@Param("id") long id, @Param("version") long version, @Param("hash") String hash);
    /** 管理员 ID 游标分页，只查询公开字段，不加载密码哈希。 */
    List<UserVO> listUserViews(@Param("afterId") long afterId, @Param("limit") int limit);
}
