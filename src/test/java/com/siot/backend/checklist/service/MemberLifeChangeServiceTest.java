package com.siot.backend.checklist.service;

import com.siot.backend.checklist.domain.MemberLifeChange;
import com.siot.backend.checklist.domain.MemberLifeChangeId;
import com.siot.backend.checklist.dto.LifeChangeRequest;
import com.siot.backend.checklist.dto.LifeChangeResponse;
import com.siot.backend.checklist.repository.MemberLifeChangeRepository;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MemberLifeChangeServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 1);

    @Mock
    private MemberLifeChangeRepository memberLifeChangeRepository;

    /** 오늘 = 2026-11-27 (한국 시간) */
    private final java.time.Clock clock = java.time.Clock.fixed(
            java.time.Instant.parse("2026-11-27T03:00:00Z"), java.time.ZoneId.of("Asia/Seoul"));

    private MemberLifeChangeService memberLifeChangeService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        memberLifeChangeService = new MemberLifeChangeService(memberLifeChangeRepository, clock);
    }

    @Test
    @DisplayName("올바른 코드면 생활 변화를 새로 등록한다")
    void register_validCode_saves() {
        given(memberLifeChangeRepository.findById(new MemberLifeChangeId(1L, "LC-08"))).willReturn(Optional.empty());
        given(memberLifeChangeRepository.save(any(MemberLifeChange.class))).willAnswer(inv -> inv.getArgument(0));

        LifeChangeResponse response = memberLifeChangeService.registerLifeChange(1L, new LifeChangeRequest("LC-08", BASE_DATE));

        assertThat(response).isNotNull();
        verify(memberLifeChangeRepository).save(any(MemberLifeChange.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"LC-00", "LC-09", "LC-99", "lc-01", "LC-01-1", "", "LC-1"})
    @DisplayName("LC-01 ~ LC-08 이 아니면 DB 에 닿기 전에 INVALID_LIFE_CHANGE_CODE")
    void register_invalidCode_rejected(String code) {
        assertThatThrownBy(() -> memberLifeChangeService.registerLifeChange(1L, new LifeChangeRequest(code, BASE_DATE)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_LIFE_CHANGE_CODE);
        verifyNoInteractions(memberLifeChangeRepository);
    }

    @Test
    @DisplayName("삭제도 잘못된 코드는 404 가 아니라 INVALID_LIFE_CHANGE_CODE")
    void delete_invalidCode_rejected() {
        assertThatThrownBy(() -> memberLifeChangeService.deleteLifeChange(1L, "LC-99"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_LIFE_CHANGE_CODE);
        verify(memberLifeChangeRepository, never()).findById(any());
    }

    @Test
    @DisplayName("올바른 코드지만 등록 안 된 변화를 삭제하면 LIFE_CHANGE_NOT_FOUND")
    void delete_notRegistered_notFound() {
        given(memberLifeChangeRepository.findById(new MemberLifeChangeId(1L, "LC-01"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberLifeChangeService.deleteLifeChange(1L, "LC-01"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LIFE_CHANGE_NOT_FOUND);
    }

    @Test
    @DisplayName("이사(LC-01)는 내일 이후 날짜면 저장 전에 LIFE_CHANGE_DATE_INVALID (BE #21)")
    void register_futureMoveDate_rejected() {
        assertThatThrownBy(() -> memberLifeChangeService.registerLifeChange(1L,
                new LifeChangeRequest("LC-01", LocalDate.of(2026, 11, 28))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LIFE_CHANGE_DATE_INVALID);
        verifyNoInteractions(memberLifeChangeRepository);
    }

    @Test
    @DisplayName("이사일이 오늘이면 저장하고, 다른 생활 변화는 미래 날짜도 저장한다 (BE #21)")
    void register_todayMoveAndFutureOtherDates_saved() {
        given(memberLifeChangeRepository.findById(any(MemberLifeChangeId.class))).willReturn(Optional.empty());
        given(memberLifeChangeRepository.save(any(MemberLifeChange.class))).willAnswer(inv -> inv.getArgument(0));

        assertThat(memberLifeChangeService.registerLifeChange(1L,
                new LifeChangeRequest("LC-01", LocalDate.of(2026, 11, 27))).baseDate()).isEqualTo("2026-11-27");
        assertThat(memberLifeChangeService.registerLifeChange(1L,
                new LifeChangeRequest("LC-07", LocalDate.of(2027, 1, 10))).baseDate()).isEqualTo("2027-01-10");
    }
}
