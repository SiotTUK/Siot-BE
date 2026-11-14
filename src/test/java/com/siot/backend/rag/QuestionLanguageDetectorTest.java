package com.siot.backend.rag;

import com.siot.backend.rag.service.QuestionLanguageDetector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionLanguageDetectorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "외국인등록은 언제까지 해야 하나요?|ko",
            "CL-01 외국인등록 서류|ko",
            "How do I register as a foreigner?|en",
            "What is 외국인등록?|en",
            "Where can I pay car tax|en",
            "外国人登录什么时候办理？|zh",
            "汽车税在哪里交|zh",
            "Thuế ô tô nộp khi nào?|vi",
            "Đăng ký người nước ngoài ở đâu?|vi",
            "toi muon dang ky nguoi nuoc ngoai o dau|vi"
    })
    void detectsLanguage(String text, String expected) {
        assertThat(QuestionLanguageDetector.detect(text)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "010-1234-5678",
            "?!",
            "CL-01",                       // 영어 기능어도 베트남어 단어도 없는 라틴 문자
            "外国人登録はいつまでですか"       // 일본어 (가나)
    })
    void returnsEmptyWhenUnsure(String text) {
        assertThat(QuestionLanguageDetector.detect(text)).isEmpty();
    }
}
