package com.siot.backend.rag;

import com.siot.backend.rag.service.EventIntentDetector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EventIntentDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "이번 주말에 시흥에서 하는 행사 있어요?",
            "다문화 축제는 언제 해요?",
            "Are there any festivals this month?",
            "Any events for foreigners?",
            "这个月有什么活动吗？",
            "Có sự kiện nào cho người nước ngoài không?",
            "Lễ hội đa văn hóa tổ chức khi nào?"
    })
    void detectsEventQuestions(String question) {
        assertThat(EventIntentDetector.detect(question)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "외국인등록은 어디서 해요?",
            "How can I prevent a fine?",
            "Điều kiện gia hạn thị thực là gì?",
            ""
    })
    void ignoresOtherQuestions(String question) {
        assertThat(EventIntentDetector.detect(question)).isFalse();
    }
}
