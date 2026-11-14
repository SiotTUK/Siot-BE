package com.siot.backend.rag.ai;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

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
            """;

    private final ChatModel model;

    public GeminiAnswerGenerator(ChatModel model) {
        this.model = model;
    }

    @Override
    public String generate(String question, String language, List<RetrievedChunk> context) {
        String fallbackLanguage = LANGUAGE_NAMES.getOrDefault(language, "Korean");
        StringBuilder passages = new StringBuilder();
        for (int i = 0; i < context.size(); i++) {
            RetrievedChunk chunk = context.get(i);
            passages.append("[").append(i + 1).append("] (").append(chunk.documentName()).append(")\n")
                    .append(chunk.content().strip()).append("\n\n");
        }
        String userText = "Reference passages:\n\n" + passages + "Question:\n" + question;
        try {
            String answer = model.chat(
                    SystemMessage.from(SYSTEM_PROMPT.formatted(fallbackLanguage)),
                    UserMessage.from(userText)).aiMessage().text();
            return answer == null ? NO_ANSWER : answer.strip();
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Gemini chat failed", e);
        }
    }
}
