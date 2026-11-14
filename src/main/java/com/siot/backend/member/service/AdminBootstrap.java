package com.siot.backend.member.service;

import com.siot.backend.member.config.AdminProperties;
import com.siot.backend.member.domain.Role;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** ADM-01: 앱 시작 시 SIOT_ADMIN_EMAILS 에 있는 기존 회원을 관리자로 올린다 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final AppUserRepository appUserRepository;

    @Override
    public void run(ApplicationArguments args) {
        promoteConfiguredAdmins();
    }

    /** @return 새로 관리자가 된 회원 수 */
    @Transactional
    public int promoteConfiguredAdmins() {
        int promoted = 0;
        for (String email : adminProperties.emails()) {
            var user = appUserRepository.findByEmail(email);
            if (user.isPresent() && user.get().getRole() != Role.ADMIN) {
                user.get().changeRole(Role.ADMIN);
                promoted++;
            }
        }
        if (promoted > 0) {
            log.info("Promoted {} configured admin(s)", promoted);
        }
        return promoted;
    }
}
