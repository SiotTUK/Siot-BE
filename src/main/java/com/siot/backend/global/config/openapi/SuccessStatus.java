package com.siot.backend.global.config.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Swagger 문서의 성공 응답 코드. {@code ResponseEntity.status(...)} 로 200 이 아닌 코드를 돌려주는 API 에 붙인다
 * (springdoc 은 반환 타입만 보고 200 으로 표시하기 때문).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SuccessStatus {

    /** 201, 202, 204 등 */
    int value();

    /** 응답 설명 (예: "가입 완료. 세션 쿠키 발급") */
    String description() default "";
}
