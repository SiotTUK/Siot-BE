package com.siot.backend.auth.security;

import com.siot.backend.auth.domain.CredentialPolicy;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return appUserRepository.findByEmail(CredentialPolicy.normalizeEmail(email))
                .map(MemberPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("member not found"));
    }
}
