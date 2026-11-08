package com.siot.backend.auth.domain;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"abcd", "user01", "a1b2c3d4e5f6g7h8i9j0"})
    void validLoginIds(String loginId) {
        assertThatCode(() -> CredentialPolicy.validateLoginId(loginId)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a1b2c3d4e5f6g7h8i9j0k", "User01", "user_01", "user 01", "사용자01", ""})
    void invalidLoginIds(String loginId) {
        assertThatThrownBy(() -> CredentialPolicy.validateLoginId(loginId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_ID_FORMAT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd1234", "A1!@#$%^", "Passw0rd-with-symbols"})
    void validPasswords(String password) {
        assertThatCode(() -> CredentialPolicy.validatePassword(password, "user01")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "abc1234,          PASSWORD_LENGTH",
            "abcdefgh,         PASSWORD_LETTER_AND_DIGIT_REQUIRED",
            "12345678,         PASSWORD_LETTER_AND_DIGIT_REQUIRED",
            "'abcd 1234',      PASSWORD_WHITESPACE",
            "abcd1234비밀,     PASSWORD_INVALID_CHARACTER",
            "user0123,         PASSWORD_SAME_AS_LOGIN_ID"
    })
    void invalidPasswords(String password, ErrorCode expected) {
        assertThatThrownBy(() -> CredentialPolicy.validatePassword(password, "user0123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void passwordLongerThan64IsRejected() {
        assertThatThrownBy(() -> CredentialPolicy.validatePassword("a1".repeat(33), "user01"))
                .extracting("errorCode").isEqualTo(ErrorCode.PASSWORD_LENGTH);
    }
}
