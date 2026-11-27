package com.siot.backend.rag.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.global.common.PageResponse;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.rag.dto.DocumentCreateRequest;
import com.siot.backend.rag.dto.DocumentReprocessRequest;
import com.siot.backend.rag.dto.DocumentResponse;
import com.siot.backend.rag.dto.DocumentUpdateRequest;
import com.siot.backend.rag.dto.DocumentVersionCreateRequest;
import com.siot.backend.rag.dto.DocumentVersionResponse;
import com.siot.backend.rag.service.DocumentAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADM-02~04, NTF-06: 챗봇 근거 문서(PDF) 등록·교체·정보 관리·반영 상태 (관리자 전용) */
@Tag(name = "9-4. 관리자: 문서", description = "챗봇 근거 문서(PDF) 등록·교체, 문서 정보, 반영 상태 확인·재실행·재처리 (ADM-02~04, NTF-06). 관리자만")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDocumentController {

    private final DocumentAdminService documentAdminService;

    @Operation(summary = "ADM-03 문서 목록",
            description = "최근 등록 순. 문서명·원문 링크·발행일·사용 여부와 현재 버전(검색에 쓰는 버전), 최근 업로드 버전의 상태.")
    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<PageResponse<DocumentResponse>>> list(
            @Parameter(description = "페이지 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(documentAdminService.list(pageable))));
    }

    @Operation(summary = "ADM-02 새 문서 등록",
            description = "PDF 와 문서 정보를 올린다. excludedPages 로 적재에서 뺄 쪽(빈칸 서식 등)을 지정할 수 있다. "
                    + "파일이 저장되면 바로 응답하고(첫 버전 PENDING), 텍스트 추출·분할·임베딩·검색 반영은 "
                    + "뒤에서 진행된다 (ADM-04). 진행 상태는 버전 목록에서 확인한다.")
    @SuccessStatus(value = 201, description = "등록됨. 반영은 뒤에서 진행")
    @ApiErrorCodes({ErrorCode.DOCUMENT_FILE_INVALID, ErrorCode.DOCUMENT_FILE_TOO_LARGE,
            ErrorCode.DOCUMENT_EXCLUDED_PAGES_INVALID})
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> create(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @ModelAttribute DocumentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(documentAdminService.create(principal.getId(), request)));
    }

    @Operation(summary = "ADM-03 문서 정보 수정",
            description = "보낸 값만 바뀐다. active=false 면 챗봇 검색에서 빠진다 (버전·조각은 지우지 않음).")
    @ApiErrorCodes(ErrorCode.DOCUMENT_NOT_FOUND)
    @PatchMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<DocumentResponse>> update(
            @PathVariable Long documentId,
            @Valid @RequestBody DocumentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(documentAdminService.update(documentId, request)));
    }

    @Operation(summary = "ADM-02 새 버전 업로드 (문서 교체)",
            description = "반영이 모두 성공하면 새 버전이 검색에 쓰이고 이전 버전은 빠진다. 실패하면 이전 버전을 계속 쓴다 (ADM-04). "
                    + "다른 버전을 반영하는 중이거나 현재 버전과 같은 파일이면 409. "
                    + "현재 버전에 적재에서 뺀 쪽이 있으면 excludedPages 를 꼭 보낸다 (빼지 않으려면 빈 값).")
    @SuccessStatus(value = 201, description = "업로드됨. 반영은 뒤에서 진행")
    @ApiErrorCodes({ErrorCode.DOCUMENT_NOT_FOUND, ErrorCode.DOCUMENT_FILE_INVALID, ErrorCode.DOCUMENT_FILE_TOO_LARGE,
            ErrorCode.DOCUMENT_VERSION_IN_PROGRESS, ErrorCode.DOCUMENT_FILE_UNCHANGED,
            ErrorCode.DOCUMENT_EXCLUDED_PAGES_INVALID, ErrorCode.DOCUMENT_EXCLUDED_PAGES_REQUIRED})
    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentVersionResponse>> addVersion(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long documentId,
            @Valid @ModelAttribute DocumentVersionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(documentAdminService.addVersion(principal.getId(), documentId, request)));
    }

    @Operation(summary = "ADM-04 현재 버전 재처리 (조각 규칙·뺄 쪽 변경)",
            description = "현재 버전의 저장된 파일로 새 버전(REPROCESS)을 만들어 다시 반영한다. 파일은 다시 올리지 않는다. "
                    + "성공하면 새 버전이 현재 버전이 되고, 실패하면 이전 버전을 계속 쓴다. 문서 내용 변경이 아니므로 사용자 알림 대상이 아니다. "
                    + "본문 excludedPages 를 생략하면 현재 값을 그대로 쓰고, 빈 문자열이면 빼지 않는다.")
    @SuccessStatus(value = 202, description = "재처리 요청됨. 반영은 뒤에서 진행")
    @ApiErrorCodes({ErrorCode.DOCUMENT_NOT_FOUND, ErrorCode.DOCUMENT_VERSION_IN_PROGRESS,
            ErrorCode.DOCUMENT_CURRENT_VERSION_MISSING, ErrorCode.DOCUMENT_EXCLUDED_PAGES_INVALID})
    @PostMapping("/documents/{documentId}/reprocess")
    public ResponseEntity<ApiResponse<DocumentVersionResponse>> reprocess(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long documentId,
            @RequestBody(required = false) DocumentReprocessRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(documentAdminService.reprocess(principal.getId(), documentId,
                        request == null ? null : request.excludedPages())));
    }

    @Operation(summary = "ADM-04·NTF-06 버전 목록 (반영 상태)",
            description = "최신 버전 먼저. 상태(대기·처리 중·완료·실패), 실패 단계·이유, 파일 업로드 시각과 반영 완료 시각을 구분해 보여준다.")
    @ApiErrorCodes(ErrorCode.DOCUMENT_NOT_FOUND)
    @GetMapping("/documents/{documentId}/versions")
    public ResponseEntity<ApiResponse<List<DocumentVersionResponse>>> versions(@PathVariable Long documentId) {
        return ResponseEntity.ok(ApiResponse.of(documentAdminService.versions(documentId)));
    }

    @Operation(summary = "ADM-04 실패한 버전 다시 반영",
            description = "FAILED 버전만, 현재 버전보다 새 버전만 다시 반영할 수 있다. 상태가 PENDING 으로 바뀌고 뒤에서 처리된다.")
    @SuccessStatus(value = 202, description = "다시 반영 요청됨")
    @ApiErrorCodes({ErrorCode.DOCUMENT_VERSION_NOT_FOUND, ErrorCode.DOCUMENT_VERSION_NOT_RETRYABLE,
            ErrorCode.DOCUMENT_VERSION_IN_PROGRESS})
    @PostMapping("/document-versions/{versionId}/retry")
    public ResponseEntity<ApiResponse<DocumentVersionResponse>> retry(@PathVariable Long versionId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(documentAdminService.retry(versionId)));
    }
}
