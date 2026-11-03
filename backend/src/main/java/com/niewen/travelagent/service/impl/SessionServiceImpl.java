package com.niewen.travelagent.service.impl;

import com.niewen.travelagent.service.SessionService;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;

/** Redis indexed repository 能按用户检索会话，改密时无需扫描所有 Redis key。 */
@Service
public class SessionServiceImpl implements SessionService {
    private final RedisIndexedSessionRepository sessions;

    public SessionServiceImpl(RedisIndexedSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public void revokeAll(long userId) {
        // principal name 使用数据库 ID，后续即使修改登录名也不会漏掉旧会话。
        // 删除所有设备的会话，不只销毁当前 HTTP 请求携带的 Session。
        sessions.findByPrincipalName(Long.toString(userId)).keySet().forEach(sessions::deleteById);
    }
}
