package com.siot.backend.member.controller;

import com.siot.backend.auth.security.MemberPrincipal;
import com.siot.backend.auth.security.SessionAuthenticator;
import com.siot.backend.global.common.ApiResponse;
import com.siot.backend.member.dto.MemberResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.siot.backend.global.config.openapi.ApiErrorCodes;
import com.siot.backend.global.config.openapi.SuccessStatus;
import com.siot.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "3. 회원", description = "내 정보, 회원 탈퇴, 가입 후 이메일 인증 (M-06, M-07, M-10, NTF-09)")
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
