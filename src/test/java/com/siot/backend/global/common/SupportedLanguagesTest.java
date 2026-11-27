package com.siot.backend.global.common;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BE #26: 요청 쿼리 language 공통 규칙 */
class SupportedLanguagesTest {

    @Test
    void omittedOrBlankMeansApiDefault() {
        assertThat(SupportedLanguages.requireValid(null)).isNull();
        assertThat(SupportedLanguages.requireValid("")).isNull();
        assertThat(SupportedLanguages.requireValid("  ")).isNull();
    }

    @Test
    void supportedValuesPassThrough() {
        for (String language : SupportedLanguages.ALL) {
            assertThat(SupportedLanguages.requireValid(language)).isEqualTo(language);
        }
    }

    @Test
    void unsupportedValuesAreRejectedNotReplaced() {
        for (String language : new String[]{"xx", "EN", "ko-KR", "ja"}) {
            assertThatThrownBy(() -> SupportedLanguages.requireValid(language))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }
}
