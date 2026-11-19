package com.siot.backend.global.config.openapi;

import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Swagger 문서를 실제 동작에 맞춘다.
 * <ul>
 *   <li>성공 코드: {@link SuccessStatus} (201·202·204)</li>
 *   <li>오류 응답: {@link ApiErrorCodes} + 공통 오류(401·403·CSRF·400)를 HTTP 상태별로 묶고 코드별 예시를 넣는다</li>
 *   <li>공개 API(/api/auth/**)는 세션 인증 표시를 지운다</li>
 * </ul>
 */
public class OpenApiErrorResponseCustomizer implements GlobalOperationCustomizer, GlobalOpenApiCustomizer {

    static final String ERROR_SCHEMA = "ErrorResponse";
    private static final String EXT_ERRORS = "x-siot-error-codes";
    private static final String EXT_SUCCESS = "x-siot-success-status";
    private static final String EXT_SUCCESS_DESC = "x-siot-success-description";
    private static final String PUBLIC_PREFIX = "/api/auth/";
    private static final String ADMIN_PREFIX = "/api/admin/";

    /** 1단계: 메서드 어노테이션을 확장 필드에 옮겨 둔다 (경로는 아직 모름) */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiErrorCodes errors = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
        if (errors != null) {
            operation.addExtension(EXT_ERRORS, Arrays.stream(errors.value()).map(Enum::name).toList());
        }
        SuccessStatus success = handlerMethod.getMethodAnnotation(SuccessStatus.class);
        if (success != null) {
            operation.addExtension(EXT_SUCCESS, success.value());
            operation.addExtension(EXT_SUCCESS_DESC, success.description());
        }
        return operation;
    }

    /** 2단계: 경로·메서드를 알고 있는 전체 문서에서 응답을 완성한다 */
    @Override
    public void customise(OpenAPI openApi) {
        openApi.getComponents().addSchemas(ERROR_SCHEMA, errorSchema());
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) ->
                item.readOperationsMap().forEach((method, operation) -> apply(path, method, operation)));
    }

    private void apply(String path, PathItem.HttpMethod method, Operation operation) {
        Map<String, Object> ext = operation.getExtensions() == null ? Map.of() : operation.getExtensions();
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        applySuccess(operation, ext);

        Set<ErrorCode> codes = new LinkedHashSet<>();
        boolean isPublic = path.startsWith(PUBLIC_PREFIX);
        if (!isPublic) {
            codes.add(ErrorCode.UNAUTHORIZED);
        }
        if (path.startsWith(ADMIN_PREFIX)) {
            codes.add(ErrorCode.FORBIDDEN);
        }
        if (method != PathItem.HttpMethod.GET) {
            codes.add(ErrorCode.CSRF_TOKEN_INVALID);
        }
        if (operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty())) {
            codes.add(ErrorCode.INVALID_INPUT);
        }
        @SuppressWarnings("unchecked")
        List<String> declared = (List<String>) ext.getOrDefault(EXT_ERRORS, List.of());
        declared.forEach(name -> codes.add(ErrorCode.valueOf(name)));
        addErrorResponses(operation, codes);

        if (isPublic) {
            operation.setSecurity(new ArrayList<>());   // 빈 목록 = 인증 필요 없음
        }
        if (operation.getExtensions() != null) {
            operation.getExtensions().keySet().removeIf(k -> k.startsWith("x-siot-"));
            if (operation.getExtensions().isEmpty()) {
                operation.setExtensions(null);
            }
        }
    }

    private static void applySuccess(Operation operation, Map<String, Object> ext) {
        ApiResponses responses = operation.getResponses();
        ApiResponse ok = responses.get("200");
        Object status = ext.get(EXT_SUCCESS);
        String description = (String) ext.getOrDefault(EXT_SUCCESS_DESC, "");
        if (status == null) {
            if (ok != null && !description.isBlank()) {
                ok.setDescription(description);
            }
            return;
        }
        int code = (Integer) status;
        responses.remove("200");
        ApiResponse success = new ApiResponse().description(description.isBlank() ? defaultDescription(code) : description);
        if (code != 204 && ok != null && ok.getContent() != null && !isEmptyBody(ok.getContent())) {
            success.setContent(ok.getContent());
        }
        responses.put(String.valueOf(code), success);
    }

    /** ResponseEntity&lt;Void&gt; 는 springdoc 이 빈 스키마로 표시한다 */
    private static boolean isEmptyBody(Content content) {
        return content.values().stream().allMatch(m -> m.getSchema() == null
                || (m.getSchema().get$ref() == null && m.getSchema().getProperties() == null
                && m.getSchema().getType() == null && m.getSchema().getTypes() == null));
    }

    private static String defaultDescription(int code) {
        return switch (code) {
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            default -> "OK";
        };
    }

    private static void addErrorResponses(Operation operation, Set<ErrorCode> codes) {
        Map<Integer, List<ErrorCode>> byStatus = codes.stream()
                .collect(Collectors.groupingBy(c -> c.getStatus().value(), TreeMap::new, Collectors.toList()));
        byStatus.forEach((status, list) -> {
            String description = list.stream()
                    .map(c -> "`" + c.name() + "` " + c.getMessage())
                    .collect(Collectors.joining("<br>"));
            Map<String, Example> examples = new LinkedHashMap<>();
            for (ErrorCode c : list) {
                examples.put(c.name(), new Example().summary(c.getMessage()).value(exampleBody(c)));
            }
            MediaType media = new MediaType()
                    .schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))
                    .examples(examples);
            operation.getResponses().put(String.valueOf(status),
                    new ApiResponse().description(description).content(new Content().addMediaType("application/json", media)));
        });
    }

    private static Schema<?> errorSchema() {
        StringSchema code = new StringSchema();
        code.setDescription("오류 코드. 프론트엔드는 이 값으로 사용자 언어 문구를 표시한다");
        code.setEnum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        StringSchema message = new StringSchema();
        message.setDescription("한국어 기본 문구");
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("실패 응답 공통 형식");
        schema.addProperty("code", code);
        schema.addProperty("message", message);
        ObjectSchema details = new ObjectSchema();
        details.setDescription("추가 정보 (일부 오류만, 없으면 생략). LOGIN_FAILED: failedAttempts·maxAttempts·remainingAttempts");
        details.setAdditionalProperties(true);
        schema.addProperty("details", details);
        schema.setRequired(List.of("code", "message"));
        return schema;
    }

    /** 오류 예시 본문. details 가 있는 오류는 실제 응답처럼 넣는다 */
    private static Map<String, Object> exampleBody(ErrorCode c) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", c.name());
        body.put("message", c.getMessage());
        if (c == ErrorCode.LOGIN_FAILED) {
            body.put("details", Map.of("failedAttempts", 2, "maxAttempts", 5, "remainingAttempts", 3));
        }
        return body;
    }
}
