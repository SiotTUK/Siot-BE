package com.siot.backend.rag;

import com.siot.backend.rag.domain.ReferralTopic;
import com.siot.backend.rag.service.ReferralDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReferralDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "제 상황이면 비자 연장 가능할까요?",
            "E-9 비자에서 F-2 로 자격 변경 될까요?",
            "Can I get a work visa with my degree?",
            "Am I eligible for permanent residence?",
            "我可以延期签证吗？",
            "Tôi có thể gia hạn thị thực không?",
            "제 경우 비자 연장 신청하면 거절될까요?",
            "Will I be approved for a visa if I apply online?"
    })
    void detectsVisaJudgment(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.VISA);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "사장님이 월급을 안 주는데 고소할 수 있나요?",
            "이거 불법인가요?",
            "Is it illegal to work part-time on a student visa?",
            "我需要请律师吗？",
            "Tôi có bị xử phạt không?"
    })
    void detectsLegalJudgment(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.LEGAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "열이 나고 기침이 나는데 무슨 병인가요?",
            "이 약 먹어도 되나요?",
            "I have a fever and headache, what should I take?",
            "我头痛，应该吃什么药？",
            "Tôi bị đau bụng, có nên uống thuốc không?"
    })
    void detectsMedicalJudgment(String text) {
        assertThat(ReferralDetector.detect(text)).contains(ReferralTopic.MEDICAL);
    }

    @Test
    void detectsSeveralTopicsInOrder() {
        assertThat(ReferralDetector.detect("불법체류면 비자 받을 수 있나요? 처벌받나요?"))
                .containsExactly(ReferralTopic.VISA, ReferralTopic.LEGAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "외국인등록은 어떻게 하나요?",
            "자동차세는 언제 내요?",
            "How do I extend my stay?",
            "비자 연장 서류는 뭐가 필요해요?",
            "체류기간 연장 신청은 어디서 할 수 있나요?",
            "비자 연장은 온라인으로 할 수 있나요?",
            "Can I apply for a visa extension online?",
            "驾照可以延期吗？",
            "운전면허 연장 가능해요?",
            "Can I get an extension on my phone bill?",
            "怎么办理外国人登录？",
            "Điều kiện tham gia sự kiện là gì?",
            ""
    })
    void ignoresInformationQuestions(String text) {
        assertThat(ReferralDetector.detect(text)).isEmpty();
    }
}
