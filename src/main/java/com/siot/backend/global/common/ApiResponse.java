package com.siot.backend.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 성공 응답 공통 형식: {@code {"data": ...}}
 * <p>
 * 실패 응답은 {@link com.siot.backend.global.exception.ErrorResponse} ({@code {"code": ..., "message": ...}}) 를 사용한다.
 * 본문이 없는 성공(삭제 등)은 204 No Content 로 응답하고 ApiResponse 를 쓰지 않는다.
 *
 * <pre>
 * return ResponseEntity.ok(ApiResponse.of(eventService.getEvent(id)));
 * return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
 * </pre>
 */
@Schema(description = "성공 응답 공통 형식")
public record ApiResponse<T>(@Schema(description = "응답 데이터") T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
