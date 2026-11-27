package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.MemberLifeChange;
import com.siot.backend.checklist.domain.MemberLifeChangeId;
import com.siot.backend.checklist.dto.LifeChangeRequest;
import com.siot.backend.checklist.dto.LifeChangeResponse;
import com.siot.backend.checklist.repository.MemberLifeChangeRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 회원의 생활 변화(F06a, LC-01 ~ LC-08) 등록, 조회, 삭제 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberLifeChangeService {

    /** DB 제약 ck_member_life_change_code 와 같은 규칙. 어긋나면 DB 오류(500) 대신 400 으로 막는다 */
    private static final Pattern LIFE_CHANGE_CODE = Pattern.compile("^LC-0[1-8]$");

    /** 생활 변화: 이사. 이사일은 오늘(한국 시간)까지만 받는다 (BE #21) */
    private static final String MOVE_CODE = "LC-01";

    private final MemberLifeChangeRepository memberLifeChangeRepository;
    /** 한국 시간 (ClockConfig) */
    private final Clock clock;

    public List<LifeChangeResponse> getLifeChanges(Long userId) {
        return memberLifeChangeRepository.findByUserId(userId).stream()
                .map(LifeChangeResponse::from)
                .toList();
    }

    @Transactional
    public LifeChangeResponse registerLifeChange(Long userId, LifeChangeRequest request) {
        validateCode(request.lifeChangeCode());
        // 이사는 이미 한 일만 등록한다 (미래 이사일로 체류지 변경 신고 기한을 계산하지 않게). 출국·입학 등 다른 생활 변화는 미래 날짜 허용
        if (MOVE_CODE.equals(request.lifeChangeCode()) && request.baseDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.LIFE_CHANGE_DATE_INVALID);
        }
        MemberLifeChangeId id = new MemberLifeChangeId(userId, request.lifeChangeCode());

        MemberLifeChange lifeChange = memberLifeChangeRepository.findById(id)
                .orElseGet(() -> MemberLifeChange.of(userId, request.lifeChangeCode(), request.baseDate()));

        lifeChange.changeBaseDate(request.baseDate());
        MemberLifeChange saved = memberLifeChangeRepository.save(lifeChange);

        return LifeChangeResponse.from(saved);
    }

    @Transactional
    public void deleteLifeChange(Long userId, String code) {
        validateCode(code);
        MemberLifeChangeId id = new MemberLifeChangeId(userId, code);
        MemberLifeChange change = memberLifeChangeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIFE_CHANGE_NOT_FOUND));

        memberLifeChangeRepository.delete(change);
    }

    private static void validateCode(String code) {
        if (code == null || !LIFE_CHANGE_CODE.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.INVALID_LIFE_CHANGE_CODE);
        }
    }
}
