package com.siot.backend.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 목록(페이지) 응답 형식. Spring Data {@link Page} 를 그대로 직렬화하지 않고 이 형식으로 변환한다.
 *
 * <pre>
 * Page&lt;Event&gt; page = eventRepository.findAll(pageable);
 * return ResponseEntity.ok(ApiResponse.of(PageResponse.from(page, EventResponse::from)));
 * </pre>
 *
 * @param page 0부터 시작하는 페이지 번호
 */
@Schema(description = "목록(페이지) 응답")
public record PageResponse<T>(
        @Schema(description = "이 페이지의 항목") List<T> content,
        @Schema(description = "페이지 번호 (0부터)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 항목 수", example = "1") long totalElements,
        @Schema(description = "전체 페이지 수", example = "1") int totalPages,
        @Schema(description = "다음 페이지가 있으면 true", example = "false") boolean hasNext
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        return from(page.map(mapper));
    }
}
