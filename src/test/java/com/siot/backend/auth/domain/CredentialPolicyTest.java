package com.siot.backend.auth.domain;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"a@b.co", "user.name+tag@example.com", "user_01@mail.example.co.kr", "x@sub-domain.org"})
    void validEmails(String email) {
        assertThatCode(() -> CredentialPolicy.validateEmail(email)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "user01", "user@", "@example.com", "user@example", "user@@example.com",
            "user name@example.com", "user@-example.com", "사용자@example.com"})
    void invalidEmails(String email) {
        assertThatThrownBy(() -> CredentialPolicy.validateEmail(email))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_FORMAT);
    }

    @Test
    void emailLongerThan255IsRejected() {
        String email = "a".repeat(250) + "@b.com";
        assertThatThrownBy(() -> CredentialPolicy.validateEmail(email))
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_FORMAT);
    }

    @Test
    void normalizeEmailTrimsAndLowercases() {
        assertThat(CredentialPolicy.normalizeEmail("  User@Example.COM ")).isEqualTo("user@example.com");
        assertThat(CredentialPolicy.normalizeEmail(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd1234", "A1!@#$%^", "Passw0rd-with-symbols"})
    void validPasswords(String password) {
        assertThatCode(() -> CredentialPolicy.validatePassword(password, "user01@example.com"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "abc1234,          PASSWORD_LENGTH",
            "abcdefgh,         PASSWORD_LETTER_AND_DIGIT_REQUIRED",
            "12345678,         PASSWORD_LETTER_AND_DIGIT_REQUIRED",
            "'abcd 1234',      PASSWORD_WHITESPACE",
            "abcd1234비밀,     PASSWORD_INVALID_CHARACTER",
            "user0123@b.co,    PASSWORD_SAME_AS_EMAIL",
            "USER0123@B.CO,    PASSWORD_SAME_AS_EMAIL"
    })
    void invalidPasswords(String password, ErrorCode expected) {
        assertThatThrownBy(() -> CredentialPolicy.validatePassword(password, "user0123@b.co"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    @Test
    void passwordLongerThan64IsRejected() {
        assertThatThrownBy(() -> CredentialPolicy.validatePassword("a1".repeat(33), "user01@example.com"))
                .extracting("errorCode").isEqualTo(ErrorCode.PASSWORD_LENGTH);
    }
}
