package com.niewen.travelagent.controller;

import com.niewen.travelagent.dto.ResetPasswordDTO;
import com.niewen.travelagent.service.AdminService;
import com.niewen.travelagent.utils.RequestUtils;
import com.niewen.travelagent.vo.AuditEventVO;
import com.niewen.travelagent.vo.ResetPasswordVO;
import com.niewen.travelagent.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 管理端入口。路径鉴权、Controller 方法鉴权和 Service 方法鉴权共同保护跨用户查询。 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @PostMapping("/users/{id}/password-reset")
    public ResetPasswordVO resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordDTO input,
                                         Principal principal, HttpServletRequest request) {
        // 操作者由 Session 认证身份决定，不能在请求体指定；统一过滤器同时校验 CSRF 和限流。
        return admin.resetPassword(RequestUtils.userId(principal), id, input, RequestUtils.traceId(request));
    }

    @GetMapping("/users")
    public List<UserVO> users(@RequestParam(defaultValue = "0") long afterId,
                              @RequestParam(defaultValue = "30") int limit,
                              Principal principal, HttpServletRequest request) {
        // actor 从登录身份提取，afterId 只是分页游标，不作为操作者身份。
        return admin.users(RequestUtils.userId(principal), afterId, limit, RequestUtils.traceId(request));
    }

    @GetMapping("/users/{id}")
    public UserVO user(@PathVariable long id, Principal principal, HttpServletRequest request) {
        // id 是被查看的目标用户，服务会将 actor 和 target 分开写入审计。
        return admin.user(RequestUtils.userId(principal), id, RequestUtils.traceId(request));
    }

    @GetMapping("/audit-events")
    public List<AuditEventVO> events(@RequestParam(required = false) Long targetUserId,
                                     @RequestParam(defaultValue = "9223372036854775807") long beforeId,
                                     @RequestParam(defaultValue = "30") int limit,
                                     Principal principal, HttpServletRequest request) {
        // beforeId 默认为 Long.MAX_VALUE，以 ID 降序查看最新记录，下一页用本页最小 ID。
        return admin.events(RequestUtils.userId(principal), targetUserId, beforeId, limit, RequestUtils.traceId(request));
    }
}
