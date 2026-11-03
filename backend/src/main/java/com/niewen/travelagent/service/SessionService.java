package com.niewen.travelagent.service;

/** 会话撤销独立于账号事务，Redis 操作不混入 MySQL 的本地事务。 */
public interface SessionService {
    void revokeAll(long userId);
}
