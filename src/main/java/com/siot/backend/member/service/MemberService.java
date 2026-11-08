package com.siot.backend.member.service;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.member.dto.MemberResponse;
import com.siot.backend.member.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public MemberResponse getMember(Long memberId) {
        return appUserRepository.findById(memberId)
                .map(MemberResponse::from)
                // 세션은 남아 있지만 회원이 삭제된 경우
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
