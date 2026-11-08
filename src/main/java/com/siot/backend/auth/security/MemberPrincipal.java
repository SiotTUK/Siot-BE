package com.siot.backend.auth.security;

import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 세션(Redis)에 저장되는 로그인 사용자 정보.
 * 인증이 끝나면 비밀번호 해시는 지워진다 (CredentialsContainer).
 * 컨트롤러에서 {@code @AuthenticationPrincipal MemberPrincipal principal} 로 받는다.
 */
public class MemberPrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String loginId;
    private final Role role;
    private String passwordHash;

    private MemberPrincipal(Long id, String loginId, Role role, String passwordHash) {
        this.id = id;
        this.loginId = loginId;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    public static MemberPrincipal from(AppUser user) {
        return new MemberPrincipal(user.getId(), user.getLoginId(), user.getRole(), user.getPasswordHash());
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Spring Session 이 사용자별 세션을 찾을 때 쓰는 이름 (M-11 전체 세션 삭제) */
    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
