package com.siot.backend.rag.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * CB-05: 외국인등록번호(주민등록번호 형식)·여권번호·전화번호를 AI 로 보내기 전과 저장 전에 가린다.
 * 이름·주소는 가리지 않는다 (요구사항). 이메일도 대상이 아니다.
 */
public final class PiiMasker {

    public record Result(String text, boolean masked) {
    }

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            // 외국인등록번호·주민등록번호: 6자리-7자리 (뒷자리 첫 숫자 1~8)
            new Rule(Pattern.compile("(?<!\\d)\\d{6}\\s?[-–]?\\s?[1-8]\\d{6}(?!\\d)"), "[등록번호]"),
            // 휴대전화 (국가번호 +82 포함)
            new Rule(Pattern.compile("(?<![\\d+])(?:\\+?82[\\s-]?)?0?1[016789][\\s.-]?\\d{3,4}[\\s.-]?\\d{4}(?!\\d)"), "[전화번호]"),
            // 지역번호 전화 (02-123-4567, 031-1234-5678)
            new Rule(Pattern.compile("(?<![\\d+])0\\d{1,2}[\\s.-]\\d{3,4}[\\s.-]\\d{4}(?!\\d)"), "[전화번호]"),
            // 해외 전화 (+84 90 123 4567 등)
            new Rule(Pattern.compile("\\+\\d{1,3}[\\s-]?\\d{1,4}(?:[\\s-]?\\d{2,4}){2,3}(?!\\d)"), "[전화번호]"),
            // 여권번호: 한국 신형(M123A4567), 일반형(영문 1~2자 + 숫자 7~8자리)
            new Rule(Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]\\d{3}[A-Za-z]\\d{4}(?![A-Za-z0-9])"), "[여권번호]"),
            new Rule(Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]{1,2}\\d{7,8}(?![A-Za-z0-9])"), "[여권번호]")
    );

    private PiiMasker() {
    }

    public static Result mask(String text) {
        if (text == null || text.isEmpty()) {
            return new Result(text, false);
        }
        String result = text;
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return new Result(result, !result.equals(text));
    }
}
