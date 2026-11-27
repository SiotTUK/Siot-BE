package com.siot.backend.rag.service;

import com.siot.backend.rag.ai.RetrievedChunk;
import com.siot.backend.rag.domain.AnswerStatus;

import java.util.List;

/**
 * 메타 질문({@link MetaQuestionDetector}) 답변 문구 (ko/en/zh/vi).
 * 대화 기록과 답변 당시 저장한 근거만으로 만든다 (AI 가 쪽수·문서 이름을 지어내지 않도록).
 * 근거는 내부 조각 번호가 아니라 "「문서 이름」 쪽" 으로 보여 준다.
 */
final class MetaAnswers {

    /** 인용하는 질문·근거 원문 최대 길이 */
    private static final int QUOTE_LENGTH = 100;

    private MetaAnswers() {
    }

    static String noHistory(String language) {
        return switch (lang(language)) {
            case "en" -> "There are no earlier questions in this conversation yet.";
            case "zh" -> "此对话中还没有之前的问题。";
            case "vi" -> "Cuộc trò chuyện này chưa có câu hỏi nào trước đó.";
            default -> "이 대화에는 아직 이전 질문이 없어요.";
        };
    }

    static String turnNotFound(String language, int turn, int total) {
        return switch (lang(language)) {
            case "en" -> "There is no question #%d in this conversation. You've asked %d question(s) so far."
                    .formatted(turn, total);
            case "zh" -> "此对话中没有第%d个问题。到目前为止您共提了%d个问题。".formatted(turn, total);
            case "vi" -> "Không có câu hỏi thứ %d trong cuộc trò chuyện này. Đến nay bạn đã hỏi %d câu."
                    .formatted(turn, total);
            default -> "%d번째 질문은 없어요. 이 대화에서 지금까지 %d개의 질문을 하셨어요.".formatted(turn, total);
        };
    }

    static String pastQuestion(String language, int turn, String question) {
        String q = quote(question);
        return switch (lang(language)) {
            case "en" -> "Your %s question was: \"%s\"".formatted(enOrdinal(turn), q);
            case "zh" -> "您的第%d个问题是：“%s”".formatted(turn, q);
            case "vi" -> "%s của bạn là: \"%s\"".formatted(capitalize(viLabel(turn)), q);
            default -> "%s은 \"%s\"였어요.".formatted(koLabel(turn), q);
        };
    }

    static String pastAnswer(String language, int turn, String question, String answer) {
        String q = quote(question);
        return switch (lang(language)) {
            case "en" -> "For your %s question (\"%s\"), I answered:\n\n%s".formatted(enOrdinal(turn), q, answer);
            case "zh" -> "对于您的第%d个问题（“%s”），我的回答是：\n\n%s".formatted(turn, q, answer);
            case "vi" -> "Với %s của bạn (\"%s\"), tôi đã trả lời:\n\n%s".formatted(viLabel(turn), q, answer);
            default -> "%s(\"%s\")에는 이렇게 답했어요.\n\n%s".formatted(koLabel(turn), q, answer);
        };
    }

    /** 답하지 못한 턴 (근거 없음·오류, 또는 답변이 저장되지 않음) */
    static String unanswered(String language, int turn, String question, String status) {
        String q = quote(question);
        boolean noEvidence = AnswerStatus.NO_EVIDENCE.name().equals(status);
        return switch (lang(language)) {
            case "en" -> noEvidence
                    ? "For your %s question (\"%s\"), I couldn't find the answer in our materials, so I didn't answer it and there are no sources."
                    .formatted(enOrdinal(turn), q)
                    : "For your %s question (\"%s\"), I couldn't answer because of a temporary error, so there are no sources."
                    .formatted(enOrdinal(turn), q);
            case "zh" -> noEvidence
                    ? "对于您的第%d个问题（“%s”），资料中没有找到答案，所以没有回答，也没有依据资料。".formatted(turn, q)
                    : "对于您的第%d个问题（“%s”），由于暂时性错误没有回答，所以没有依据资料。".formatted(turn, q);
            case "vi" -> noEvidence
                    ? "Với %s của bạn (\"%s\"), tôi không tìm thấy câu trả lời trong tài liệu nên đã không trả lời và không có tài liệu căn cứ."
                    .formatted(viLabel(turn), q)
                    : "Với %s của bạn (\"%s\"), tôi đã không trả lời được do lỗi tạm thời nên không có tài liệu căn cứ."
                    .formatted(viLabel(turn), q);
            default -> noEvidence
                    ? "%s(\"%s\")에는 자료에서 답을 찾지 못해 답변하지 않았어요. 그래서 근거 자료도 없어요."
                    .formatted(koLabel(turn), q)
                    : "%s(\"%s\")에는 일시적인 오류로 답변하지 못했어요. 그래서 근거 자료도 없어요."
                    .formatted(koLabel(turn), q);
        };
    }

    /** 답변은 했지만 저장된 근거가 없음 (메타 질문 답변, 근거 저장 이전 답변) */
    static String noStoredSources(String language, int turn, String question) {
        String q = quote(question);
        return switch (lang(language)) {
            case "en" -> "No sources were saved for my answer to your %s question (\"%s\").".formatted(enOrdinal(turn), q);
            case "zh" -> "我对您的第%d个问题（“%s”）的回答没有保存依据资料。".formatted(turn, q);
            case "vi" -> "Câu trả lời cho %s của bạn (\"%s\") không có tài liệu căn cứ được lưu.".formatted(viLabel(turn), q);
            default -> "%s(\"%s\")에 대한 답변에는 저장된 근거 자료가 없어요.".formatted(koLabel(turn), q);
        };
    }

    /**
     * 답변 당시 저장한 근거 목록.
     *
     * @param sources 아직 남아 있는 근거 조각
     * @param deleted 문서 삭제·새 버전 반영으로 지금은 확인할 수 없는 근거 수
     */
    static String sources(String language, int turn, String question, List<RetrievedChunk> sources, int deleted) {
        String q = quote(question);
        String lang = lang(language);
        if (sources.isEmpty()) {
            return switch (lang) {
                case "en" -> "The sources of my answer to your %s question (\"%s\") have since been deleted or replaced with a new version, so they can't be shown."
                        .formatted(enOrdinal(turn), q);
                case "zh" -> "我对您的第%d个问题（“%s”）的回答所依据的资料已被删除或更新为新版本，现在无法显示。".formatted(turn, q);
                case "vi" -> "Các tài liệu làm căn cứ cho câu trả lời %s của bạn (\"%s\") đã bị xóa hoặc thay bằng phiên bản mới nên hiện không thể hiển thị."
                        .formatted(viLabel(turn), q);
                default -> "%s(\"%s\")에 대한 답변의 근거 자료는 삭제되거나 새 버전으로 바뀌어 지금은 확인할 수 없어요."
                        .formatted(koLabel(turn), q);
            };
        }
        StringBuilder text = new StringBuilder(switch (lang) {
            case "en" -> "My answer to your %s question (\"%s\") was based on these materials:".formatted(enOrdinal(turn), q);
            case "zh" -> "我对您的第%d个问题（“%s”）的回答依据以下资料：".formatted(turn, q);
            case "vi" -> "Câu trả lời cho %s của bạn (\"%s\") dựa trên các tài liệu sau:".formatted(viLabel(turn), q);
            default -> "%s(\"%s\")에 대한 답변은 아래 자료를 근거로 했어요.".formatted(koLabel(turn), q);
        }).append("\n");
        for (RetrievedChunk source : sources) {
            text.append("\n- ").append(location(lang, source));
            String excerpt = quote(source.content());
            if (!excerpt.isEmpty()) {
                text.append("\n  \"").append(excerpt).append('"');
            }
        }
        if (deleted > 0) {
            text.append("\n\n").append(switch (lang) {
                case "en" -> "%d of the sources have since been deleted or replaced with a new version, so they can't be shown.".formatted(deleted);
                case "zh" -> "其中%d份资料已被删除或更新为新版本，现在无法显示。".formatted(deleted);
                case "vi" -> "%d tài liệu trong số đó đã bị xóa hoặc thay bằng phiên bản mới nên hiện không thể hiển thị.".formatted(deleted);
                default -> "근거 자료 중 %d개는 삭제되거나 새 버전으로 바뀌어 지금은 확인할 수 없어요.".formatted(deleted);
            });
        }
        return text.toString();
    }

    /** 「문서 이름」 12~13쪽 */
    static String location(String language, RetrievedChunk source) {
        String name = "「" + (source.documentName() == null ? "?" : source.documentName()) + "」";
        Integer start = source.pageStart();
        if (start == null) {
            return name;
        }
        Integer end = source.pageEnd();
        boolean range = end != null && !end.equals(start);
        return name + " " + switch (lang(language)) {
            case "en" -> range ? "p. " + start + "–" + end : "p. " + start;
            case "zh" -> range ? "第" + start + "~" + end + "页" : "第" + start + "页";
            case "vi" -> range ? "trang " + start + "–" + end : "trang " + start;
            default -> range ? start + "~" + end + "쪽" : start + "쪽";
        };
    }

    private static String lang(String language) {
        return language == null ? "ko" : language;
    }

    /** 줄바꿈을 한 칸으로 줄이고 길면 자른다 */
    private static String quote(String text) {
        if (text == null) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").strip();
        return collapsed.length() <= QUOTE_LENGTH ? collapsed : collapsed.substring(0, QUOTE_LENGTH).strip() + "…";
    }

    private static String koLabel(int turn) {
        return turn == 1 ? "첫 번째 질문" : turn + "번째 질문";
    }

    private static String viLabel(int turn) {
        return turn == 1 ? "câu hỏi đầu tiên" : "câu hỏi thứ " + turn;
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static String enOrdinal(int n) {
        return switch (n) {
            case 1 -> "first";
            case 2 -> "second";
            case 3 -> "third";
            default -> {
                int mod100 = n % 100;
                String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
                    case 1 -> "st";
                    case 2 -> "nd";
                    case 3 -> "rd";
                    default -> "th";
                };
                yield n + suffix;
            }
        };
    }
}
