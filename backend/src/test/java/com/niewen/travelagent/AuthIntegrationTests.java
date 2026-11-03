package com.niewen.travelagent;

import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.utils.UserConverter;
import com.niewen.travelagent.mapper.AppUserMapper;
import com.niewen.travelagent.mapper.TestDataMapper;
import com.niewen.travelagent.constant.SessionConstants;
import com.niewen.travelagent.service.SessionService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 HTTP Cookie、MySQL Mapper XML 和 Redis 验证持久化会话，不用 mock Session 掩盖问题。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTests {
    private static final String NAMESPACE = "travel:test:" + UUID.randomUUID();
    private static final String PASSWORD = "Test-only-password-2948";
    @Value("${local.server.port}") private int port;
    @Autowired private AppUserMapper accounts;
    @Autowired private PasswordEncoder encoder;
    @Autowired private TestDataMapper data;
    @Autowired private ObjectMapper mapper;
    @Autowired private RedisIndexedSessionRepository sessions;
    @Autowired private SessionService revocation;
    @Autowired private com.niewen.travelagent.service.UserService userService;
    private final List<Long> userIds = new ArrayList<>();
    private final List<String> traces = new ArrayList<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.session.data.redis.namespace", () -> NAMESPACE + ":sessions");
        registry.add("travel.security.rate-limit-namespace", () -> NAMESPACE + ":limits");
        // 业务用例不共享 IP 登录额度；限流上限由专门测试验证，重置仍使用默认的 5 次额度。
        registry.add("travel.security.login-ip-limit", () -> "500");
        registry.add("travel.admin.bootstrap.enabled", () -> "false");
    }

    @AfterEach
    void removeOnlyThisTestsFixtures() {
        for (long id : userIds) {
            revocation.revokeAll(id);
            data.removeAuditForUser(id);
        }
        traces.forEach(data::removeAuditByTrace);
        userIds.forEach(data::deleteUser);
    }

    @Test
    void csrfIsRequiredForLoginRegistrationAndLogout() throws Exception {
        Browser browser = new Browser();
        assertEquals(401, browser.get("/auth/me").statusCode());
        assertEquals(403, browser.post("/auth/login", "username=test&password=anything", false, true).statusCode());
        assertEquals(403, browser.post("/auth/register", "{}", false, false).statusCode());
        assertEquals(403, browser.post("/auth/logout", "", false, true).statusCode());
    }

    @Test
    void loginRotatesCookiePersistsIdentityAndLogoutRevokesIt() throws Exception {
        AppUser user = fixture("USER", false);
        Browser browser = new Browser();
        JsonNode previousCsrf = mapper.readTree(browser.get("/auth/csrf").body());
        String oldCookie = browser.cookie();
        HttpResponse<String> login = browser.login(user);
        assertEquals(200, login.statusCode());
        assertFalse(login.body().contains("passwordHash"));
        assertFalse(login.body().contains("authVersion"));
        assertNotEquals(oldCookie, browser.cookie());
        assertTrue(login.headers().allValues("set-cookie").stream().anyMatch(c -> c.contains("HttpOnly") && c.contains("SameSite=Lax")));
        assertEquals(user.getId(), mapper.readTree(browser.get("/auth/me").body()).get("id").asLong());
        var savedSessions = sessions.findByPrincipalName(Long.toString(user.getId()));
        assertEquals(1, savedSessions.size());
        var saved = savedSessions.values().iterator().next();
        assertEquals(Duration.ofHours(24), saved.getMaxInactiveInterval());
        Long deadline = saved.getAttribute(SessionConstants.EXPIRES_AT);
        assertTrue(deadline > Instant.now().plus(Duration.ofDays(6)).toEpochMilli());
        var context = (org.springframework.security.core.context.SecurityContext)
                saved.getAttribute("SPRING_SECURITY_CONTEXT");
        assertNull(((org.springframework.security.core.userdetails.User) context.getAuthentication().getPrincipal()).getPassword());
        HttpResponse<String> oldTokenRejected = browser.send(HttpRequest.newBuilder(uri("/auth/logout"))
                .header(previousCsrf.get("headerName").asText(), previousCsrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        assertEquals(403, oldTokenRejected.statusCode());
        String authenticatedCookie = browser.cookie();
        assertEquals(204, browser.post("/auth/logout", "", true, true).statusCode());
        assertEquals(401, browser.get("/auth/me").statusCode());
        HttpResponse<String> replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/auth/me"))
                .header("Cookie", "travel-session=" + authenticatedCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, replay.statusCode());
    }

    @Test
    void registrationNormalizesUsernameAndCannotAcceptAnAdminRole() throws Exception {
        Browser browser = new Browser();
        String name = "reg_" + UUID.randomUUID();
        String body = mapper.writeValueAsString(java.util.Map.of("username", name.toUpperCase(), "nickname", "测试旅人",
                "password", PASSWORD, "role", "ADMIN"));
        HttpResponse<String> result = browser.post("/auth/register", body, true, false);
        assertEquals(201, result.statusCode());
        long id = mapper.readTree(result.body()).get("id").asLong();
        userIds.add(id);
        AppUser account = accounts.findById(id).orElseThrow();
        assertEquals(name, account.getNormalizedUsername());
        assertEquals("USER", account.getRole());
        assertTrue(account.getPasswordHash().startsWith("{bcrypt}"));
        assertTrue(encoder.matches(PASSWORD, account.getPasswordHash()));
        assertEquals(409, browser.post("/auth/register", body, true, false).statusCode());
    }

    @Test
    void ordinaryUserCannotReadAnotherAccountAndAdminReadsAreAudited() throws Exception {
        AppUser user = fixture("USER", false);
        AppUser other = fixture("USER", false);
        AppUser admin = fixture("ADMIN", false);
        Browser browser = new Browser();
        assertEquals(200, browser.login(user).statusCode());
        assertEquals(403, browser.get("/admin/users/" + other.getId()).statusCode());
        assertEquals(403, browser.get("/admin/audit-events").statusCode());
        Browser administrator = new Browser();
        assertEquals(200, administrator.login(admin).statusCode());
        HttpResponse<String> viewed = administrator.get("/admin/users/" + other.getId());
        assertEquals(200, viewed.statusCode());
        assertEquals(other.getId(), mapper.readTree(viewed.body()).get("id").asLong());
        assertFalse(viewed.body().contains("password"));
        assertEquals(1, data.countAdminUserViews(admin.getId(), other.getId()));
    }

    @Test
    void disabledAccountsAndRevokedRolesInvalidateExistingSessions() throws Exception {
        AppUser user = fixture("USER", false);
        Browser browser = new Browser();
        assertEquals(200, browser.login(user).statusCode());
        data.setUserStatus(user.getId(), "DISABLED");
        assertEquals(401, browser.get("/auth/me").statusCode());
        assertEquals(401, browser.login(user).statusCode());
        AppUser admin = fixture("ADMIN", false);
        Browser administrator = new Browser();
        assertEquals(200, administrator.login(admin).statusCode());
        data.setUserRole(admin.getId(), "USER");
        assertEquals(401, administrator.get("/admin/users").statusCode());
    }

    @Test
    void initialPasswordBlocksAdminAccessAndPasswordChangeRevokesAllDevices() throws Exception {
        AppUser admin = fixture("ADMIN", true);
        Browser first = new Browser();
        Browser second = new Browser();
        assertEquals(200, first.login(admin).statusCode());
        assertEquals(200, second.login(admin).statusCode());
        assertEquals(403, first.get("/admin/users").statusCode());
        String next = "Updated-test-password-5831";
        HttpResponse<String> change = first.post("/auth/password", mapper.writeValueAsString(
                java.util.Map.of("currentPassword", PASSWORD, "newPassword", next)), true, false);
        assertEquals(204, change.statusCode());
        assertEquals(401, first.get("/auth/me").statusCode());
        assertEquals(401, second.get("/auth/me").statusCode());
        AppUser updated = accounts.findById(admin.getId()).orElseThrow();
        assertFalse(updated.getMustChangePassword());
        assertEquals(2L, updated.getAuthVersion());
        assertEquals(401, first.login(admin).statusCode());
        assertEquals(200, first.post("/auth/login", "username=" + admin.getNormalizedUsername() + "&password=" + encode(next), true, true).statusCode());
        assertEquals(200, first.get("/admin/users").statusCode());
    }

    @Test
    void absoluteExpiryDoesNotDependOnRedisIdleTimeout() throws Exception {
        AppUser user = fixture("USER", false);
        Browser browser = new Browser();
        assertEquals(200, browser.login(user).statusCode());
        sessions.findByPrincipalName(Long.toString(user.getId())).values().forEach(session -> {
            session.setAttribute(SessionConstants.EXPIRES_AT, 1L);
            sessions.save(session);
        });
        assertEquals(401, browser.get("/auth/me").statusCode());
    }

    @Test
    void wrongPasswordAndUnknownUsernameReturnTheSameError() throws Exception {
        AppUser user = fixture("USER", false);
        Browser browser = new Browser();
        HttpResponse<String> wrong = browser.post("/auth/login", "username=" + user.getNormalizedUsername() + "&password=wrong", true, true);
        HttpResponse<String> missing = browser.post("/auth/login", "username=missing_" + UUID.randomUUID() + "&password=wrong", true, true);
        assertEquals(401, wrong.statusCode());
        assertEquals(401, missing.statusCode());
        assertEquals(wrong.body(), missing.body());
    }

    @Test
    void adminResetRequiresOwnPasswordAndRevokesAllOldSessionsThenForcesChange() throws Exception {
        AppUser admin = fixture("ADMIN", false);
        AppUser target = fixture("USER", false);
        Browser administrator = new Browser();
        Browser first = new Browser();
        Browser second = new Browser();
        assertEquals(200, administrator.login(admin).statusCode());
        assertEquals(200, first.login(target).statusCode());
        assertEquals(200, second.login(target).statusCode());
        String path = "/admin/users/" + target.getId() + "/password-reset";
        assertEquals(403, first.post(path, resetBody(PASSWORD), true, false).statusCode());
        assertEquals(403, administrator.post(path, resetBody(PASSWORD), false, false).statusCode());
        HttpResponse<String> wrong = administrator.post(path, resetBody("incorrect-password"), true, false);
        assertEquals(400, wrong.statusCode());
        assertEquals("FAILED", data.findAuditByTrace(wrong.headers().firstValue("X-Trace-Id").orElseThrow()).result());
        assertEquals(1L, accounts.findById(target.getId()).orElseThrow().getAuthVersion());
        assertEquals(200, first.get("/auth/me").statusCode());

        HttpResponse<String> reset = administrator.post(path, resetBody(PASSWORD), true, false);
        assertEquals(200, reset.statusCode());
        assertTrue(reset.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        JsonNode result = mapper.readTree(reset.body());
        String temporary = result.get("temporaryPassword").asText();
        assertEquals(target.getId(), result.get("userId").asLong());
        assertTrue(Instant.parse(result.get("expiresAt").asText()).isAfter(Instant.now().plusSeconds(1700)));
        AppUser changed = accounts.findById(target.getId()).orElseThrow();
        assertTrue(changed.getMustChangePassword());
        assertEquals(2L, changed.getAuthVersion());
        assertTrue(encoder.matches(temporary, changed.getPasswordHash()));
        assertFalse(changed.getPasswordHash().contains(temporary));
        var event = data.findAuditByTrace(reset.headers().firstValue("X-Trace-Id").orElseThrow());
        assertEquals("ADMIN_RESET_PASSWORD", event.action());
        assertEquals("SUCCESS", event.result());
        assertEquals(admin.getId(), event.actorUserId());
        assertEquals(target.getId(), event.targetUserId());
        assertEquals(401, first.get("/auth/me").statusCode());
        assertEquals(401, second.get("/auth/me").statusCode());
        assertEquals(401, first.login(target).statusCode());
        HttpResponse<String> login = first.loginWith(target, temporary);
        assertEquals(200, login.statusCode());
        JsonNode identity = mapper.readTree(login.body());
        assertTrue(identity.get("mustChangePassword").asBoolean());
        assertEquals("", identity.get("username").asText());
        assertTrue(identity.get("lastLoginAt").isNull());
        assertEquals("", mapper.readTree(first.get("/auth/me").body()).get("username").asText());
        assertEquals("PASSWORD_CHANGE_REQUIRED", mapper.readTree(first.get("/admin/users").body()).get("code").asText());
        assertEquals(403, first.post("/auth/register", "{}", true, false).statusCode());
        String next = "User-selected-new-password-9482";
        assertEquals(204, first.post("/auth/password", mapper.writeValueAsString(java.util.Map.of(
                "currentPassword", temporary, "newPassword", next)), true, false).statusCode());
        assertEquals(401, first.get("/auth/me").statusCode());
        assertEquals(401, first.loginWith(target, temporary).statusCode());
        assertEquals(200, first.loginWith(target, next).statusCode());
        assertEquals(target.getNormalizedUsername(), mapper.readTree(first.get("/auth/me").body()).get("username").asText());
        assertFalse(accounts.findById(target.getId()).orElseThrow().getMustChangePassword());
        // 查询接口不会重新返回临时密码，审计接口也不包含它。
        assertFalse(administrator.get("/admin/users/" + target.getId()).body().contains(temporary));
        assertFalse(administrator.get("/admin/audit-events?targetUserId=" + target.getId()).body().contains(temporary));
    }

    @Test
    void expiredTemporaryPasswordCannotLoginOrUseAnAlreadyEstablishedRestrictedSession() throws Exception {
        AppUser target = fixture("USER", true);
        Browser browser = new Browser();
        assertEquals(200, browser.login(target).statusCode());
        data.expireTemporaryPassword(target.getId());
        assertEquals(401, browser.get("/auth/me").statusCode());
        assertEquals(401, browser.login(target).statusCode());
    }

    @Test
    void adminResetCannotTargetAdminsOrDisabledUsersAndPasswordVerificationIsRateLimited() throws Exception {
        AppUser admin = fixture("ADMIN", false);
        AppUser peer = fixture("ADMIN", false);
        AppUser disabled = fixture("USER", false);
        AppUser user = fixture("USER", false);
        data.setUserStatus(disabled.getId(), "DISABLED");
        Browser browser = new Browser();
        assertEquals(200, browser.login(admin).statusCode());
        for (AppUser forbidden : List.of(admin, peer, disabled)) {
            HttpResponse<String> denied = browser.post("/admin/users/" + forbidden.getId() + "/password-reset",
                    resetBody(PASSWORD), true, false);
            assertEquals(403, denied.statusCode());
            assertEquals("DENIED", data.findAuditByTrace(denied.headers().firstValue("X-Trace-Id").orElseThrow()).result());
            assertEquals(1L, accounts.findById(forbidden.getId()).orElseThrow().getAuthVersion());
        }
        for (int i = 0; i < 2; i++) {
            assertEquals(400, browser.post("/admin/users/" + user.getId() + "/password-reset",
                    resetBody("wrong-password"), true, false).statusCode());
        }
        HttpResponse<String> limited = browser.post("/admin/users/+" + user.getId() + "/password-reset",
                resetBody(PASSWORD), true, false);
        assertEquals(429, limited.statusCode());
        assertEquals("900", limited.headers().firstValue("Retry-After").orElseThrow());
        assertEquals(1L, accounts.findById(user.getId()).orElseThrow().getAuthVersion());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
    void resetTransactionRollsBackOnAuditFailureAndRejectsStaleAdminVerification() {
        AppUser admin = fixture("ADMIN", false);
        AppUser target = fixture("USER", false);
        // 审计 trace 不能为空。故意违反约束，证明目标密码、版本和强制改密状态也随事务回滚。
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                userService.resetUserPassword(admin.getId(), 1L, target.getId(), "Temporary-test-only-9128", ""));
        AppUser unchanged = accounts.findById(target.getId()).orElseThrow();
        assertEquals(1L, unchanged.getAuthVersion());
        assertFalse(unchanged.getMustChangePassword());
        assertTrue(encoder.matches(PASSWORD, unchanged.getPasswordHash()));
        data.setUserVersion(admin.getId(), 2L);
        var error = assertThrows(com.niewen.travelagent.exception.ApiException.class, () ->
                userService.resetUserPassword(admin.getId(), 1L, target.getId(), "Temporary-test-only-9128", "test-trace"));
        assertEquals("ACCOUNT_CHANGED", error.code());
        assertEquals(1L, accounts.findById(target.getId()).orElseThrow().getAuthVersion());
    }

    private String resetBody(String password) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("adminPassword", password));
    }

    private AppUser fixture(String role, boolean mustChange) {
        AppUser account = UserConverter.newAccount("auth_test_" + UUID.randomUUID(), "鉴权测试", encoder.encode(PASSWORD), role, mustChange);
        accounts.insertAccount(account);
        userIds.add(account.getId());
        return account;
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path) throws Exception { return send(HttpRequest.newBuilder(uri(path)).GET().build()); }
        HttpResponse<String> login(AppUser user) throws Exception {
            return post("/auth/login", "username=" + user.getNormalizedUsername() + "&password=" + encode(PASSWORD), true, true);
        }
        HttpResponse<String> loginWith(AppUser user, String password) throws Exception {
            return post("/auth/login", "username=" + user.getNormalizedUsername() + "&password=" + encode(password), true, true);
        }
        String cookie() { return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("travel-session")).map(c -> c.getValue()).findFirst().orElse(""); }
        HttpResponse<String> post(String path, String body, boolean csrf, boolean form) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", form ? "application/x-www-form-urlencoded" : "application/json");
            if (csrf) {
                JsonNode token = mapper.readTree(get("/auth/csrf").body());
                request.header(token.get("headerName").asText(), token.get("token").asText());
            }
            return send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build());
        }
        private HttpResponse<String> send(HttpRequest request) throws Exception {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            response.headers().firstValue("X-Trace-Id").ifPresent(traces::add);
            return response;
        }
    }
}
