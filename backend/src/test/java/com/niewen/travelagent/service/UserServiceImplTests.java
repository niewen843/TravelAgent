package com.niewen.travelagent.service;

import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.mapper.AppUserMapper;
import com.niewen.travelagent.service.impl.UserServiceImpl;
import com.niewen.travelagent.utils.UserConverter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证初始化的角色安全边界和幂等行为，不依赖真实数据库。 */
class UserServiceImplTests {
    @Test
    void doesNotPromoteAnExistingOrdinaryUser() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuditService audit = mock(AuditService.class);
        when(mapper.findByUsername("admin")).thenReturn(Optional.of(existing("USER")));
        UserService service = new UserServiceImpl(mapper, encoder, audit, new com.niewen.travelagent.config.properties.SecurityProperties());
        assertThrows(IllegalStateException.class, () -> service.initializeAdmin("ADMIN", "valid-initial-password", "test-trace"));
        verifyNoInteractions(encoder, audit);
        verify(mapper, never()).insertAccount(any());
    }

    @Test
    void existingAdminIsPreservedWithoutResettingPassword() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuditService audit = mock(AuditService.class);
        when(mapper.findByUsername("admin")).thenReturn(Optional.of(existing("ADMIN")));
        new UserServiceImpl(mapper, encoder, audit, new com.niewen.travelagent.config.properties.SecurityProperties()).initializeAdmin("admin", "valid-initial-password", "test-trace");
        verifyNoInteractions(encoder, audit);
        verify(mapper, never()).insertAccount(any());
    }

    private AppUser existing(String role) {
        AppUser user = UserConverter.newAccount("admin", "管理员", "encoded", role, true);
        user.setId(1L);
        return user;
    }
}
