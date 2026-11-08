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

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberWithdrawalService memberWithdrawalService;
    private final SessionAuthenticator sessionAuthenticator;

    /** 로그인 상태 확인 + 내 정보. 로그인이 만료되었으면 401 (M-10) */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(@AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.of(memberService.getMember(principal.getId())));
    }

    /**
     * M-06 / M-07: 회원 탈퇴. 비밀번호를 다시 확인한 뒤 즉시 물리 삭제하고 모든 세션을 끊는다.
     * 본문(비밀번호)이 필요해서 DELETE 대신 POST 를 사용한다.
     */
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
