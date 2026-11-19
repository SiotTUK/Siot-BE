package com.siot.backend.rag;

import com.siot.backend.rag.service.EmergencyDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmergencyDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "집에 불이 났어요 어떻게 해요?",
            "친구가 다쳤는데 병원 어디로 가요?",
            "길에서 사람이 쓰러졌어요",
            "누가 저를 때려요",
            "There is a fire in my building",
            "My friend is bleeding, call an ambulance",
            "I was robbed on the street",
            "家里着火了怎么办",
            "有人受伤了",
            "Nhà tôi bị cháy",
            "Bạn tôi bị thương, cần cấp cứu"
    })
    void detectsEmergency(String text) {
        assertThat(EmergencyDetector.isEmergency(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "외국인등록은 어떻게 하나요?",
            "자동차세는 언제 내요?",
            "I was fired from my job, what about my visa?",
            "How do I extend my stay?",
            "怎么办理外国人登录？",
            "Vứt rác như thế nào?",
            ""
    })
    void ignoresOrdinaryQuestions(String text) {
        assertThat(EmergencyDetector.isEmergency(text)).isFalse();
    }

    @Test
    void detectsDecomposedVietnamese() {
        // 일부 입력기는 성조 글자를 분해형(NFD)으로 보낸다
        String nfd = java.text.Normalizer.normalize("Nhà tôi bị cháy, cần cấp cứu", java.text.Normalizer.Form.NFD);
        assertThat(EmergencyDetector.isEmergency(nfd)).isTrue();
    }
}
