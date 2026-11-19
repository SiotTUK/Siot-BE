package com.siot.backend.rag.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRangesTest {

    @Test
    void parsesRangesAndSinglePages() {
        assertThat(PageRanges.parse("1-3,5")).containsExactly(1, 2, 3, 5);
        assertThat(PageRanges.parse(" 7 , 1 - 2 ")).containsExactly(1, 2, 7);
        assertThat(PageRanges.parse(null)).isEmpty();
        assertThat(PageRanges.parse("  ")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3-1", "a", "1-", "1,,2", "-2", "1-99999"})
    void rejectsInvalidRanges(String spec) {
        assertThatThrownBy(() -> PageRanges.parse(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsConsecutivePagesAsRanges() {
        assertThat(PageRanges.format(Set.of(5, 1, 2, 3, 9, 10))).isEqualTo("1-3,5,9-10");
        assertThat(PageRanges.format(Set.of())).isEmpty();
    }
}
