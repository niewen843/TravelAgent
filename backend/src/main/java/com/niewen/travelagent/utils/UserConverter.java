package com.niewen.travelagent.utils;

import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.vo.UserVO;

/** 显式转换实体与公开视图，避免 Controller 直接返回含密码哈希的数据库实体。 */
public final class UserConverter {
    private UserConverter() {}

    public static UserVO toView(AppUser user) {
        return new UserVO(user.getId(), user.getNormalizedUsername(), user.getNickname(), user.getRole(),
                user.getStatus(), Boolean.TRUE.equals(user.getMustChangePassword()), user.getCreatedAt(), user.getLastLoginAt());
    }

    /** 构造插入实体；status、authVersion 和时间仍使用建表语句定义的数据库默认值。 */
    public static AppUser newAccount(String username, String nickname, String hash, String role, boolean mustChange) {
        AppUser user = new AppUser();
        user.setNormalizedUsername(username);
        user.setNickname(nickname);
        user.setPasswordHash(hash);
        user.setRole(role);
        user.setMustChangePassword(mustChange);
        return user;
    }
}
