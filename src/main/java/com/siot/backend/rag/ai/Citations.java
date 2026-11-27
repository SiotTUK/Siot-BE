package com.siot.backend.rag.ai;

import java.util.regex.Pattern;

/**
 * CB-17: 모델에게 넘기는 근거 조각 표시와, 답변에 새어 나온 내부 번호 지우기.
 * 사용자는 "[1]" 같은 검색 결과 번호가 무엇인지 알 수 없으므로, 근거는 문서 이름·쪽으로만 가리킨다.
 */
public final class Citations {

    /** "[1]", "[1, 2]", "[1][3]", "[2-3]", "【1】" 같은 조각 번호 */
    private static final Pattern PASSAGE_NUMBER =
            Pattern.compile("[ \\t]*(?:\\[\\s*\\d{1,2}(?:\\s*[,，、\\-–~]\\s*\\d{1,2})*\\s*]|【\\s*\\d{1,2}\\s*】)");

    private Citations() {
    }

    /** 모델에게 넘기는 근거 조각 머리말. 번호 대신 문서 이름과 쪽 (모델이 번호를 따라 쓰지 않도록) */
    public static String label(RetrievedChunk chunk) {
        StringBuilder label = new StringBuilder("--- 「")
                .append(chunk.documentName() == null ? "자료" : chunk.documentName()).append("」");
        String pages = pages(chunk.pageStart(), chunk.pageEnd());
        if (pages != null) {
            label.append(" p.").append(pages);
        }
        return label.append(" ---").toString();
    }

    /** "12", "12-13", 쪽 정보가 없으면 null */
    public static String pages(Integer start, Integer end) {
        if (start == null) {
            return null;
        }
        return end == null || end.equals(start) ? String.valueOf(start) : start + "-" + end;
    }

    /** 답변에 새어 나온 조각 번호를 지운다 */
    public static String stripPassageNumbers(String text) {
        if (text == null) {
            return null;
        }
        return PASSAGE_NUMBER.matcher(text).replaceAll("").strip();
    }
}
