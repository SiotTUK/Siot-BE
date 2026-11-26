package com.siot.backend.event.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** EVE-10 카테고리: 출처 분류 → 서비스 카테고리 */
class EventCategoryTest {

    @ParameterizedTest
    @CsvSource({"공연, PERFORMANCE", "전시, EXHIBITION", "교육, EDUCATION", "행사, EVENT", " 공연 , PERFORMANCE",
            "체험, EVENT"})
    void mapsGgcCategory(String ggc, EventCategory expected) {
        assertThat(EventCategory.fromGgc(ggc)).isEqualTo(expected);
    }

    @Test
    void unknownGgcCategoryIsEvent() {
        assertThat(EventCategory.fromGgc(null)).isEqualTo(EventCategory.EVENT);
    }

    @ParameterizedTest
    @CsvSource(value = {"EV01, , FESTIVAL", "EV02, , PERFORMANCE", "EV03, , EVENT", "EV09, A0207, EVENT",
            ", A0207, FESTIVAL", ", A0208, EVENT", ", , EVENT"}, nullValues = "")
    void mapsTourApiClassification(String lclsSystm2, String cat2, EventCategory expected) {
        assertThat(EventCategory.fromTourApi(lclsSystm2, cat2)).isEqualTo(expected);
    }

    @Test
    void parsesRequestValueAndNamesInFourLanguages() {
        assertThat(EventCategory.parse(" festival ")).isEqualTo(EventCategory.FESTIVAL);
        assertThat(EventCategory.parse("")).isNull();
        assertThatThrownBy(() -> EventCategory.parse("SPORTS")).isInstanceOf(IllegalArgumentException.class);

        assertThat(EventCategory.EDUCATION.displayName("ko")).isEqualTo("교육·체험");
        assertThat(EventCategory.PERFORMANCE.displayName("en")).isEqualTo("Performance");
        assertThat(EventCategory.EXHIBITION.displayName("zh")).isEqualTo("展览");
        assertThat(EventCategory.FESTIVAL.displayName("vi")).isEqualTo("Lễ hội");
        assertThat(EventCategory.EVENT.displayName("ja")).isEqualTo("행사"); // 지원하지 않는 언어는 한국어
    }
}
