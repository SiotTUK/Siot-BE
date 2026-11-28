package com.siot.backend.checklist.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistDisplayReasonTest {

    @Test
    @DisplayName("보이는 이유 문구는 4개 언어가 모두 있고, 지원하지 않는 언어면 한국어 (BE #42)")
    void labelsInEveryLanguage() {
        for (ChecklistDisplayReason reason : ChecklistDisplayReason.values()) {
            for (String language : List.of("ko", "en", "zh", "vi")) {
                assertThat(reason.label(language)).as(reason + " " + language).isNotBlank();
            }
            assertThat(reason.label("ja")).isEqualTo(reason.label("ko"));
            assertThat(reason.label(null)).isEqualTo(reason.label("ko"));
        }
        assertThat(ChecklistDisplayReason.CAR.label("ko")).isEqualTo("자차가 있어서");
        assertThat(ChecklistDisplayReason.KIDS.label("vi")).isEqualTo("Vì bạn có con");
    }
}
