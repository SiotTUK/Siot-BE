package com.siot.backend.rag.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CB-17: 사용자는 "[1]" 같은 내부 조각 번호가 무엇인지 알 수 없으므로 지운다 */
class CitationsTest {

    @Test
    void stripsPassageNumbers() {
        assertThat(Citations.stripPassageNumbers("종량제 봉투에 담아 배출해야 합니다 [1]."))
                .isEqualTo("종량제 봉투에 담아 배출해야 합니다.");
        assertThat(Citations.stripPassageNumbers("Recycle cans [1, 2][3] and bottles【4】."))
                .isEqualTo("Recycle cans and bottles.");
        assertThat(Citations.stripPassageNumbers("Call 1345 [2-3]")).isEqualTo("Call 1345");
    }

    @Test
    void keepsOtherBrackets() {
        assertThat(Citations.stripPassageNumbers("[전화번호]로 연락하세요. E-9 [필수]"))
                .isEqualTo("[전화번호]로 연락하세요. E-9 [필수]");
    }

    @Test
    void labelsPassagesByDocumentAndPage() {
        assertThat(Citations.label(new RetrievedChunk(1L, "x", 9, 10, "시흥시 외국인주민 생활안내", null, 0.9)))
                .isEqualTo("--- 「시흥시 외국인주민 생활안내」 p.9-10 ---");
        assertThat(Citations.label(new RetrievedChunk(1L, "x", 9, 9, "안내", null, 0.9)))
                .isEqualTo("--- 「안내」 p.9 ---");
    }
}
