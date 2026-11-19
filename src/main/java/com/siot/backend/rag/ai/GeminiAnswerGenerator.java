package com.siot.backend.rag.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gemini 로 답변 생성. 검색된 조각만 근거로 쓰고, 근거에 없으면 {@link AnswerGenerator#NO_ANSWER} 를 돌려받는다.
 */
public class GeminiAnswerGenerator implements AnswerGenerator {

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "ko", "Korean", "en", "English", "zh", "Simplified Chinese", "vi", "Vietnamese");

    static final String SYSTEM_PROMPT = """
            You are the assistant of "Welcome Siheung", a service that helps foreign residents of Siheung, Korea.

            Rules:
            1. Answer ONLY with information found in the numbered reference passages provided by the user message.
               Do not add facts, numbers, fees, deadlines or links that are not in the passages.
            2. If the passages do not contain the answer, reply with exactly: NO_ANSWER
            3. Reply in the same language as the question (Korean, English, Chinese or Vietnamese).
               If you cannot tell the language, reply in %s.
            4. Do not judge an individual's visa eligibility, legal or medical situation. For such questions,
               explain what the passages say and suggest contacting the Immigration Contact Center (1345).
            5. Be concise and practical: short paragraphs or bullet points, plain words.
            6. Do not mention passage numbers or the word "passage"; the app shows the sources separately.
            7. Earlier conversation turns may be given only so you understand what the question refers to.
               Facts in your answer must still come from the reference passages.
            """;

    private final ChatModel model;

    public GeminiAnswerGenerator(ChatModel model) {
        this.model = model;
    }

    @Override
    public String generate(String question, String language, List<RetrievedChunk> context,
                           List<ChatTurn> history) {
        String fallbackLanguage = LANGUAGE_NAMES.getOrDefault(language, "Korean");
        StringBuilder passages = new StringBuilder();
        for (int i = 0; i < context.size(); i++) {
            RetrievedChunk chunk = context.get(i);
            passages.append("[").append(i + 1).append("] (").append(chunk.documentName()).append(")\n")
                    .append(chunk.content().strip()).append("\n\n");
        }
        String userText = "Reference passages:\n\n" + passages + "Question:\n" + question;

        // CB-07: 앞선 대화는 실제 대화 메시지로 넘기고, 마지막 메시지에 근거 조각과 현재 질문을 담는다
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT.formatted(fallbackLanguage)));
        // 근거 없음·오류였던 턴(answer == null)을 "NO_ANSWER" 라는 AI 답변으로 넣으면 모델이 그 응답을 따라 하게 되고,
        // 일시적인 AI 오류도 "답 없음" 처럼 보인다. 질문만 다음 사용자 메시지 앞에 붙인다 (같은 역할 메시지가 연속되지 않게).
        StringBuilder unanswered = new StringBuilder();
        for (ChatTurn turn : history) {
            if (turn.answer() == null) {
                unanswered.append("- ").append(turn.question()).append('\n');
                continue;
            }
            messages.add(UserMessage.from(withUnanswered(unanswered, turn.question())));
            messages.add(AiMessage.from(turn.answer()));
        }
        messages.add(UserMessage.from(withUnanswered(unanswered, userText)));
        try {
            String answer = model.chat(messages).aiMessage().text();
            return answer == null ? NO_ANSWER : answer.strip();
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Gemini chat failed", e);
        }
    }

    /** 답하지 못한 앞 질문들을 맥락으로 앞에 붙이고 비운다 */
    private static String withUnanswered(StringBuilder unanswered, String text) {
        if (unanswered.isEmpty()) {
            return text;
        }
        String result = "Earlier questions in this conversation (not answered):\n" + unanswered + "\n" + text;
        unanswered.setLength(0);
        return result;
    }
}
