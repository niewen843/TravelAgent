package com.niewen.travelagent.service;

import com.niewen.travelagent.dto.ResetPasswordDTO;
import com.niewen.travelagent.vo.AuditEventVO;
import com.niewen.travelagent.vo.ResetPasswordVO;
import com.niewen.travelagent.vo.UserVO;
import java.util.List;

/** 管理端业务契约；每个方法都显式接收来自登录身份的操作者和服务端 trace。 */
public interface AdminService {
    /** 验证管理员本人密码，提交重置事务后撤销目标会话，仅本次响应提供临时密码。 */
    ResetPasswordVO resetPassword(long actor, long target,
            ResetPasswordDTO input, String traceId);
    List<UserVO> users(long actor, long afterId, int limit, String traceId);
    UserVO user(long actor, long target, String traceId);
    List<AuditEventVO> events(long actor, Long target, long beforeId, int limit, String traceId);
}
