package com.siot.backend.translation.bill;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** RUL-07 고지서 사진 번역, 최근 번역, 납부 마감 알림. 생활수칙 화면과 챗봇 화면이 같이 쓴다 */
@Tag(name = "6-2. 고지서 번역", description = "고지서 사진을 읽어 사용자 언어로 요약한다 (RUL-07). "
        + "사진은 저장하지 않고, 개인정보를 가린 결과만 최근 번역으로 남긴다")
@RestController
@RequestMapping("/api/translations/bills")
@RequiredArgsConstructor
public class BillTranslationController {

    private final BillTranslationService billTranslationService;
    private final BillTranslationHistoryService historyService;

    @Operation(summary = "RUL-07 고지서 사진 번역",
            description = """
                    고지서 사진(JPG·PNG) 또는 PDF 를 올리면 무엇인지 / 언제까지 / 얼마인지 / 어떻게 내는지를 사용자 언어로 요약하고, \
                    가산세·압류·체류기간 연장 제한 같은 주의 사항과 관련 안내 항목(자동차세 CL-10, 재산세 CL-11)을 함께 돌려준다.

                    - 업로드 전에 "사진은 외부 AI(Google Gemini)로 전송되며 학습에 쓰일 수 있습니다. 이름·주소는 가리고 찍는 것을 \
                    권장합니다. 사진은 저장하지 않지만, 개인정보를 가린 번역 결과는 '최근 번역'에 최대 10개까지 저장되며 \
                    언제든 지울 수 있습니다" 를 안내하고 동의를 받아 consent=true 로 보낸다. 아니면 400 BILL_CONSENT_REQUIRED.
                    - 파일은 10MB 이하. PDF 는 첫 쪽만 읽는다 (여러 쪽이면 firstPageOnly=true).
                    - 글자를 읽지 못하면 readable=false 와 다시 찍기 안내(retake). 이때는 저장하지 않는다 (id=null).
                    - 읽은 결과는 최근 번역으로 저장해 id 를 돌려준다 (회원당 최근 10개, 사진은 저장하지 않음).
                    - remind=true 면 납부 마감 알림(D-7·D-1)도 켠다. 기한을 못 읽었거나 지났으면 오류 없이 reminder=false.
                    - 회원당 1시간에 10번까지 (넘으면 429). AI 를 쓸 수 없으면 503 AI_UNAVAILABLE.""")
    @ApiErrorCodes({ErrorCode.BILL_CONSENT_REQUIRED, ErrorCode.BILL_FILE_INVALID, ErrorCode.BILL_FILE_TOO_LARGE,
            ErrorCode.BILL_TRANSLATION_TOO_FREQUENT, ErrorCode.BILL_TRANSLATION_LIMIT, ErrorCode.AI_UNAVAILABLE})
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BillTranslationResponse>> translateBill(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "고지서 사진 또는 PDF (image/jpeg, image/png, application/pdf, 10MB 이하)")
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Parameter(description = "외부 AI 전송 동의. true 여야 한다")
            @RequestParam(value = "consent", required = false) String consent,
            @Parameter(description = "요약 언어 (ko/en/zh/vi). 생략하면 회원 언어")
            @RequestParam(value = "language", required = false) String language,
            @Parameter(description = "true 면 납부 마감 알림(D-7·D-1)도 켠다. 기본 false")
            @RequestParam(value = "remind", required = false) String remind) {
        BillTranslationResponse response = billTranslationService.translate(principal.getId(), file,
                "true".equalsIgnoreCase(consent), language);
        return ResponseEntity.ok(ApiResponse.of(
                historyService.save(principal.getId(), response, "true".equalsIgnoreCase(remind))));
    }

    @Operation(summary = "RUL-07 최근 고지서 번역 목록",
            description = "새것부터 최대 10개. 전체 결과는 GET /api/translations/bills/{id}. "
                    + "reminderAvailable=false 면 기한이 없거나 지나 마감 알림을 켤 수 없다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BillTranslationListItem>>> recentTranslations(
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.of(historyService.list(principal.getId())));
    }

    @Operation(summary = "RUL-07 저장한 고지서 번역 보기",
            description = "번역할 때의 결과를 그대로 돌려준다 (주의 사항·요약은 번역 시점 기준). 본인 것만 볼 수 있다.")
    @ApiErrorCodes({ErrorCode.BILL_TRANSLATION_NOT_FOUND})
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BillTranslationResponse>> translation(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(historyService.get(principal.getId(), id)));
    }

    @Operation(summary = "RUL-07 납부 마감 알림 켜기·끄기",
            description = """
                    켜면 납부 기한 D-7·D-1 오전 9시에 알림(kind=BILL_DUE, linkType=BILL_TRANSLATION)을 만든다. \
                    인증된 이메일이 있으면 메일로도 보낸다. 알림 설정의 TAX 를 끄면 보내지 않는다.
                    - 기한을 못 읽었거나 지난 번역은 켤 수 없다 (400 BILL_REMINDER_UNAVAILABLE). 끄기는 언제나 된다.""")
    @ApiErrorCodes({ErrorCode.BILL_TRANSLATION_NOT_FOUND, ErrorCode.BILL_REMINDER_UNAVAILABLE, ErrorCode.INVALID_INPUT})
    @PutMapping("/{id}/reminder")
    public ResponseEntity<ApiResponse<BillTranslationResponse>> changeReminder(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody BillReminderRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                historyService.changeReminder(principal.getId(), id, request.enabled())));
    }

    @Operation(summary = "RUL-07 고지서 번역 기록 삭제",
            description = "마감 알림도 더 오지 않는다. 이미 받은 알림은 남지만 번역 연결(linkCode)은 사라진다.")
    @ApiErrorCodes({ErrorCode.BILL_TRANSLATION_NOT_FOUND})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTranslation(@AuthenticationPrincipal MemberPrincipal principal,
                                                  @PathVariable Long id) {
        historyService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
