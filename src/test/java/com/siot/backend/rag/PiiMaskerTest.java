package com.siot.backend.rag;

import com.siot.backend.rag.service.PiiMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "제 외국인등록번호는 900101-5123456 입니다|제 외국인등록번호는 [등록번호] 입니다",
            "등록번호 9001015123456|등록번호 [등록번호]",
            "전화 010-1234-5678 로 연락|전화 [전화번호] 로 연락",
            "call me at 01012345678|call me at [전화번호]",
            "+82 10-1234-5678 is my number|[전화번호] is my number",
            "사무실 031-123-4567|사무실 [전화번호]",
            "số điện thoại +84 90 123 4567|số điện thoại [전화번호]",
            "passport M123A4567 lost|passport [여권번호] lost",
            "护照号码 E12345678|护照号码 [여권번호]",
            "여권 AB1234567 분실|여권 [여권번호] 분실"
    })
    void masksPersonalNumbers(String input, String expected) {
        PiiMasker.Result result = PiiMasker.mask(input);
        assertThat(result.text()).isEqualTo(expected);
        assertThat(result.masked()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "외국인등록은 입국 후 90일 이내에 해야 하나요?",
            "체류기간 연장 수수료는 얼마예요?",
            "1345 에 전화하면 되나요?",
            "2026년 6월 16일부터 자동차세를 내나요?",
            "제 이름은 Nguyen Van A 이고 주소는 시흥시 정왕동입니다",
            "CL-01 외국인등록"
    })
    void keepsOrdinaryText(String input) {
        PiiMasker.Result result = PiiMasker.mask(input);
        assertThat(result.text()).isEqualTo(input);
        assertThat(result.masked()).isFalse();
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(PiiMasker.mask(null).masked()).isFalse();
        assertThat(PiiMasker.mask("").text()).isEmpty();
    }
}
