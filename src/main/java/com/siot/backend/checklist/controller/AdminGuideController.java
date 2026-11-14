package com.siot.backend.checklist.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.checklist.dto.GuideTranslationResponse;
import com.siot.backend.checklist.dto.GuideTranslationUpdateRequest;
import com.siot.backend.checklist.service.AdminGuideService;
import com.siot.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자: 항목 상세 문구 조회·저장 (CHK-06 필드, NTF-07 안내 내용 변경 기록) */
@RestController
@RequestMapping("/api/admin/guides/{code}/translations/{language}")
@RequiredArgsConstructor
public class AdminGuideController {

    private final AdminGuideService adminGuideService;

    @GetMapping
    public ResponseEntity<ApiResponse<GuideTranslationResponse>> get(@PathVariable String code,
                                                                     @PathVariable String language) {
        return ResponseEntity.ok(ApiResponse.of(adminGuideService.get(code, language)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<GuideTranslationResponse>> save(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String code,
            @PathVariable String language,
            @Valid @RequestBody GuideTranslationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(adminGuideService.save(principal.getId(), code, language, request)));
    }
}
