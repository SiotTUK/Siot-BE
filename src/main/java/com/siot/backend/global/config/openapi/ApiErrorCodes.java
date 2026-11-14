package com.siot.backend.global.config.openapi;

import com.siot.backend.global.exception.ErrorCode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Swagger 문서에 이 API 가 돌려줄 수 있는 오류 코드를 표시한다.
 * <p>
 * 공통 오류(401 UNAUTHORIZED, 403 FORBIDDEN·CSRF_TOKEN_INVALID, 400 INVALID_INPUT)는
 * {@link OpenApiErrorResponseCustomizer} 가 경로·메서드를 보고 자동으로 붙이므로 여기에는 쓰지 않는다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {

    ErrorCode[] value();
}
