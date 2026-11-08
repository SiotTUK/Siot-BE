package com.siot.backend.global.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void convertsPageWithMapper() {
        var page = new PageImpl<>(List.of(1, 2), PageRequest.of(0, 2), 5);

        PageResponse<String> response = PageResponse.from(page, n -> "item-" + n);

        assertThat(response.content()).containsExactly("item-1", "item-2");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
    }
}
