package com.niewen.travelagent.security;

import jakarta.servlet.FilterChain;
import com.niewen.travelagent.filter.AuthRateLimitFilter;
import com.niewen.travelagent.config.properties.SecurityProperties;
import com.niewen.travelagent.utils.JsonResponseUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTests {
    @Test
    void rejectsOverLimitWithoutInvokingAuthentication() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(31L);
        SecurityProperties properties = new SecurityProperties();
        properties.setRateLimitNamespace("test:limit");
        var filter = new AuthRateLimitFilter(redis, new JsonResponseUtils(new ObjectMapper()), properties);
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addParameter("username", "test_user");
        request.addParameter("password", "test-password");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(429, response.getStatus());
        assertEquals("900", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("RATE_LIMITED"));
        verifyNoInteractions(chain);
    }
}
