package com.siot.backend.member.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.auth.security.SessionAuthenticator;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.member.dto.MemberResponse;
import com.siot.backend.member.dto.MemberUpdateRequest;
import com.siot.backend.member.dto.OnboardingRequest;
import com.siot.backend.member.dto.WithdrawalRequest;
import com.siot.backend.member.service.MemberService;
import com.siot.backend.member.service.MemberWithdrawalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "3. 회원", description = "내 정보·화면 언어·온보딩 답, 회원 탈퇴, 가입 후 이메일 인증 (M-06, M-07, M-10, F02~F06, NTF-09)")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberWithdrawalService memberWithdrawalService;
    private final SessionAuthenticator sessionAuthenticator;

    /** 로그인 상태 확인 + 내 정보. 로그인이 만료되었으면 401 (M-10) */
    @Operation(summary = "M-10 내 정보 (로그인 상태 확인)",
            description = "401 이면 로그인이 만료된 것이므로 로그인 화면으로 보낸다. onboarded=false 면 온보딩 전이다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(@AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.of(memberService.getMember(principal.getId())));
    }

    /** 내 정보 수정 (현재는 화면 언어). 보내지 않은 항목은 그대로 둔다 */
    @Operation(summary = "내 정보 수정 (화면 언어)",
            description = "보내지 않은(null) 항목은 바꾸지 않는다. language 는 비밀번호 재설정·알림 메일 언어로도 쓰이므로, "
                    + "사용자가 화면 언어를 바꾸면 이 API 로 저장한다.")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> update(@AuthenticationPrincipal MemberPrincipal principal,
                                                              @Valid @RequestBody MemberUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(memberService.update(principal.getId(), request)));
    }

    /** 온보딩 답 저장 (F02~F06). 처음 온보딩과 내 정보 화면 수정에 같이 쓴다 */
    @Operation(summary = "온보딩 답 저장 (F02~F06)",
            description = "유형(필수)·입국일·목적·거주 정보·만료일을 저장한다. 항상 전체를 덮어쓰므로, 내 정보 화면에서 고칠 때도 "
                    + "5개 답을 모두 보낸다 (보내지 않은 선택 항목은 지워진다). 저장하면 onboarded=true 가 되고, "
                    + "체크리스트 완료 기록은 지워지지 않는다.")
    @PutMapping("/me/onboarding")
    public ResponseEntity<ApiResponse<MemberResponse>> saveOnboarding(@AuthenticationPrincipal MemberPrincipal principal,
                                                                      @Valid @RequestBody OnboardingRequest request) {
        return ResponseEntity.ok(ApiResponse.of(memberService.saveOnboarding(principal.getId(), request)));
    }

    /**
     * M-06 / M-07: 회원 탈퇴. 비밀번호를 다시 확인한 뒤 즉시 물리 삭제하고 모든 세션을 끊는다.
     * 본문(비밀번호)이 필요해서 DELETE 대신 POST 를 사용한다.
     */
    @Operation(summary = "M-06·M-07 회원 탈퇴",
            description = "비밀번호를 다시 확인한 뒤 즉시 물리 삭제한다 (복구 불가). 다른 기기를 포함한 모든 세션이 끊긴다. "
                    + "오류 신고는 작성자 정보만 지우고 남긴다. 비밀번호가 틀리면 401 이 아니라 400 이다 (로그인 만료로 처리하지 않도록).")
    @SuccessStatus(value = 204, description = "탈퇴 완료 (세션 쿠키 삭제)")
    @ApiErrorCodes(ErrorCode.PASSWORD_INCORRECT)
    @PostMapping("/me/withdrawal")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal MemberPrincipal principal,
                                         @Valid @RequestBody WithdrawalRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        memberWithdrawalService.withdraw(principal.getId(), request.password());
        // 현재 요청의 로그인 상태와 세션 쿠키도 정리한다 (다른 기기 세션은 이벤트로 삭제됨)
        sessionAuthenticator.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
}
