package com.siot.backend.event.collect;

import com.siot.backend.event.domain.EventTag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 날짜·시각 파싱, 행사명 정규화(출처 간 중복), 키워드 규칙(is_multicultural, event_tag) */
class EventRulesTest {

    @Test
    void parsesOnlyExactDateFormats() {
        assertThat(EventTexts.isoDate("2026-11-20")).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(EventTexts.isoDate("2026.11.20")).isNull();
        assertThat(EventTexts.isoDate("2026-02-30")).isNull();
        assertThat(EventTexts.isoDate("")).isNull();
        assertThat(EventTexts.basicDate("20260918")).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(EventTexts.basicDate("2026-09-18")).isNull();
    }

    @Test
    void extractsStartAndEndTimesFromFreeText() {
        assertThat(EventTexts.times("19:30 (80분)")).containsExactly(LocalTime.of(19, 30), null);
        assertThat(EventTexts.times("17:00 (러닝타임 120분)")).containsExactly(LocalTime.of(17, 0), null);
        assertThat(EventTexts.times("10:00~17:00")).containsExactly(LocalTime.of(10, 0), LocalTime.of(17, 0));
        assertThat(EventTexts.times("10:00 ~ 18:00 (월요일 휴관)")).containsExactly(LocalTime.of(10, 0), LocalTime.of(18, 0));
        assertThat(EventTexts.times("평일 오후 2시")).containsExactly(null, null);
        assertThat(EventTexts.times("25:00")).containsExactly(null, null);
        assertThat(EventTexts.times(null)).containsExactly(null, null);
    }

    @Test
    void normalizesNamesForCrossSourceDuplicates() {
        assertThat(EventNames.normalize("제21회 시흥갯골축제")).isEqualTo(EventNames.normalize("시흥갯골축제"));
        assertThat(EventNames.normalize("2026 시흥갯골축제")).isEqualTo(EventNames.normalize("시흥 갯골 축제!"));
        assertThat(EventNames.normalize("2026년 WSL 시흥 코리아 오픈")).isEqualTo("wsl시흥코리아오픈");
        assertThat(EventNames.normalize("오이도 축제")).isNotEqualTo(EventNames.normalize("시흥갯골축제"));
    }

    @Test
    void appliesKeywordRules() {
        assertThat(EventKeywords.multicultural("다문화가족 한마당", null)).isTrue();
        assertThat(EventKeywords.multicultural("시흥갯골축제", "외국인 주민도 함께하는 축제")).isTrue();
        assertThat(EventKeywords.multicultural("시흥시 합창 페스티벌", null)).isFalse();

        assertThat(EventKeywords.tags("어린이 뮤지컬 <구름빵>")).containsExactly(EventTag.FAMILY_KIDS);
        assertThat(EventKeywords.tags("외국인 주민 한국어교실")).containsExactly(EventTag.KOREAN_CLASS);
        assertThat(EventKeywords.tags("이주민 정착 첫걸음 교육")).containsExactly(EventTag.NEWCOMER);
        // "신규"는 키워드가 아니다
        assertThat(EventKeywords.tags("합창단 신규 회원 모집")).isEmpty();
    }
}
