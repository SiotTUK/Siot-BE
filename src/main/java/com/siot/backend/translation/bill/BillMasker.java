package com.siot.backend.translation.bill;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RUL-07: AI 가 가리지 못한 개인정보를 서버에서 한 번 더 가린다 (안전망).
 * 등록번호, 12자리 이상 숫자열(계좌·전자납부번호), 차량번호 뒷자리, 주소의 번지·호수.
 * 이름은 형태로 찾을 수 없으므로 프롬프트에서 빼도록 한다. 기관 문의 전화는 가리지 않는다.
 */
final class BillMasker {

    /** 주민등록번호·외국인등록번호 */
    private static final Pattern REGISTRATION_NO =
            Pattern.compile("(?<!\\d)\\d{6}\\s?[-–]?\\s?[1-8]\\d{6}(?!\\d)");
    /** 하이픈으로 이어진 숫자열 또는 붙은 숫자열. 숫자가 12개 이상이면 가린다 (날짜·전화·금액은 그보다 짧다) */
    private static final Pattern NUMBER_RUN =
            Pattern.compile("(?<![\\d-])\\d+(?:-\\d+)+(?![\\d-])|(?<!\\d)\\d{12,}(?!\\d)");
    private static final int LONG_NUMBER_DIGITS = 12;
    /** 차량번호 12가3456, 123가 3456 → 12가**** */
    private static final Pattern VEHICLE_NO = Pattern.compile("(?<!\\d)(\\d{2,3})\\s?([가-힣])\\s?\\d{4}(?!\\d)");
    /** 도로명 주소 건물번호: 정왕대로 123, 서울대로 12-3 */
    private static final Pattern ROAD_NO =
            Pattern.compile("([가-힣0-9]{1,15}(?:로|길))\\s*\\d{1,5}(?:-\\d{1,5})?(?![\\d,.%-])");
    /** 지번: 정왕동 1234-5, ○○리 산12번지 */
    private static final Pattern LOT_NO =
            Pattern.compile("([가-힣]{1,10}(?:동|리))\\s*(?:산\\s?)?\\d{1,5}(?:-\\d{1,5})?(?:번지)?(?![\\d,.%-])");
    /** 아파트 동·호: 101동 1203호 */
    private static final Pattern UNIT_NO = Pattern.compile("\\d{1,4}\\s?동\\s?\\d{1,5}\\s?호");

    private BillMasker() {
    }

    static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = REGISTRATION_NO.matcher(text).replaceAll("******-*******");
        result = maskLongNumbers(result);
        // 동·호(101동 1203호)가 차량번호 모양과 겹치므로 먼저 가린다
        result = UNIT_NO.matcher(result).replaceAll("***동 ***호");
        result = VEHICLE_NO.matcher(result).replaceAll("$1$2****");
        result = ROAD_NO.matcher(result).replaceAll("$1 ***");
        result = LOT_NO.matcher(result).replaceAll("$1 ***");
        return result;
    }

    static List<String> mask(List<String> texts) {
        return texts.stream().map(BillMasker::mask).toList();
    }

    private static String maskLongNumbers(String text) {
        Matcher matcher = NUMBER_RUN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String run = matcher.group();
            long digits = run.chars().filter(Character::isDigit).count();
            String replacement = digits >= LONG_NUMBER_DIGITS ? run.replaceAll("\\d", "*") : run;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
