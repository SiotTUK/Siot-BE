package com.siot.backend.translation.bill;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;

import java.util.Locale;
import java.util.Map;

/**
 * RUL-07 고지서 파일 형식 확인. 보낸 Content-Type 과 파일 시작 바이트가 모두 JPG·PNG·PDF 이고 서로 같아야 한다.
 */
final class BillFileValidator {

    static final String JPEG = "image/jpeg";
    static final String PNG = "image/png";
    static final String PDF = "application/pdf";

    private static final Map<String, String> ALIASES = Map.of(
            "image/jpg", JPEG,
            "image/pjpeg", JPEG);

    private BillFileValidator() {
    }

    /** @return 형식 (image/jpeg, image/png, application/pdf) */
    static String detect(String declaredContentType, byte[] bytes) {
        String declared = normalize(declaredContentType);
        String actual = sniff(bytes);
        if (actual == null || !actual.equals(declared)) {
            throw new BusinessException(ErrorCode.BILL_FILE_INVALID);
        }
        return actual;
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return null;
        }
        String type = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(type, type);
    }

    private static String sniff(byte[] b) {
        if (b == null) {
            return null;
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return PNG;
        }
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return PDF;
        }
        return null;
    }
}
