package com.siot.backend.rag.service;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.rag.document.TestPdfs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcludedPagesTest {

    private static final byte[] TEN_PAGES = TestPdfs.blank(10);

    @Test
    void normalizesRanges() {
        assertThat(ExcludedPages.normalize(" 8, 2-3, 4 , 10 ", TEN_PAGES)).isEqualTo("2-4,8,10");
    }

    @Test
    void blankMeansNone() {
        assertThat(ExcludedPages.normalize(null, TEN_PAGES)).isNull();
        assertThat(ExcludedPages.normalize("  ", TEN_PAGES)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3-1", "abc", "51~52", ",", "11", "5-11", "1-10"})
    void rejectsBadRangeOutOfDocumentAndAllPages(String spec) {
        assertInvalid(spec);
    }

    @Test
    void rejectsTooLongValue() {
        assertInvalid("1,".repeat(ExcludedPages.MAX_LENGTH));
    }

    private static void assertInvalid(String spec) {
        assertThatThrownBy(() -> ExcludedPages.normalize(spec, TEN_PAGES))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DOCUMENT_EXCLUDED_PAGES_INVALID));
    }
}
