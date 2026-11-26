package com.siot.backend.rag.service;

import com.siot.backend.event.domain.EventCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CB-10: 질문이 말한 행사 카테고리 (4개 언어) */
class EventIntentDetectorCategoryTest {

    @Test
    void detectsCategoryWordsInFourLanguages() {
        assertThat(EventIntentDetector.categories("이번 주말에 공연 있어요?")).containsExactly(EventCategory.PERFORMANCE);
        assertThat(EventIntentDetector.categories("Are there any exhibitions this month?"))
                .containsExactly(EventCategory.EXHIBITION);
        assertThat(EventIntentDetector.categories("始兴有什么节日活动?")).containsExactly(EventCategory.FESTIVAL);
        assertThat(EventIntentDetector.categories("Có lớp học tiếng Hàn không?")).containsExactly(EventCategory.EDUCATION);
        assertThat(EventIntentDetector.categories("축제나 전시 알려줘"))
                .containsExactlyInAnyOrder(EventCategory.FESTIVAL, EventCategory.EXHIBITION);
    }

    @Test
    void genericEventWordsAreNotACategory() {
        assertThat(EventIntentDetector.categories("이번 달 행사 알려줘")).isEmpty();
        assertThat(EventIntentDetector.categories("any events this weekend?")).isEmpty();
        assertThat(EventIntentDetector.categories(null)).isEmpty();
        // 영어는 단어 경계로 ("classic" 의 "class" 오탐 없음)
        assertThat(EventIntentDetector.categories("classic events")).isEmpty();
    }
}
