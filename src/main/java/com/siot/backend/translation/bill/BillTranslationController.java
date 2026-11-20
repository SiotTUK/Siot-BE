package com.siot.backend.translation.bill;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** RUL-07 고지서 사진 번역. 생활수칙 화면과 챗봇 화면이 같이 쓴다 */
@Tag(name = "6-2. 고지서 번역", description = "고지서 사진을 읽어 사용자 언어로 요약한다 (RUL-07). 사진은 저장하지 않는다")
@RestController
@RequestMapping("/api/translations")
@RequiredArgsConstructor
public class BillTranslationController {

    private final BillTranslationService billTranslationService;

    @Operation(summary = "RUL-07 고지서 사진 번역",
            description = """
                    고지서 사진(JPG·PNG) 또는 PDF 를 올리면 무엇인지 / 언제까지 / 얼마인지 / 어떻게 내는지를 사용자 언어로 요약하고, \
                    가산세·압류·체류기간 연장 제한 같은 주의 사항과 관련 안내 항목(자동차세 CL-10, 재산세 CL-11)을 함께 돌려준다.

                    - 업로드 전에 "사진은 외부 AI(Google Gemini)로 전송되며 학습에 쓰일 수 있습니다. 이름·주소는 가리고 찍는 것을 \
                    권장합니다" 를 안내하고 동의를 받아 consent=true 로 보낸다. 아니면 400 BILL_CONSENT_REQUIRED.
                    - 파일은 10MB 이하. PDF 는 첫 쪽만 읽는다 (여러 쪽이면 firstPageOnly=true).
                    - 글자를 읽지 못하면 readable=false 와 다시 찍기 안내(retake).
                    - 사진·결과는 저장하지 않고 알림도 만들지 않는다. 회원당 1시간에 10번까지 (넘으면 429).
                    - AI 를 쓸 수 없으면 503 AI_UNAVAILABLE.""")
    @ApiErrorCodes({ErrorCode.BILL_CONSENT_REQUIRED, ErrorCode.BILL_FILE_INVALID, ErrorCode.BILL_FILE_TOO_LARGE,
            ErrorCode.BILL_TRANSLATION_TOO_FREQUENT, ErrorCode.BILL_TRANSLATION_LIMIT, ErrorCode.AI_UNAVAILABLE})
    @PostMapping(value = "/bills", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BillTranslationResponse>> translateBill(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "고지서 사진 또는 PDF (image/jpeg, image/png, application/pdf, 10MB 이하)")
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Parameter(description = "외부 AI 전송 동의. true 여야 한다")
            @RequestParam(value = "consent", required = false) String consent,
            @Parameter(description = "요약 언어 (ko/en/zh/vi). 생략하면 회원 언어")
            @RequestParam(value = "language", required = false) String language) {
        return ResponseEntity.ok(ApiResponse.of(
                billTranslationService.translate(principal.getId(), file, "true".equalsIgnoreCase(consent), language)));
    }
}
