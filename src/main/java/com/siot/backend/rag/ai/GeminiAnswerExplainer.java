package com.siot.backend.rag.ai;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;
import java.util.Map;

/** Gemini 로 앞 답변의 근거를 설명한다 ({@link AnswerExplainer}). */
public class GeminiAnswerExplainer implements AnswerExplainer {

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "ko", "Korean", "en", "English", "zh", "Simplified Chinese", "vi", "Vietnamese");

    static final String SYSTEM_PROMPT = """
            You are the assistant of "Welcome Siheung", a service that helps foreign residents of Siheung, Korea.
            The user is asking why you gave one of your earlier answers, or what it was based on.
            You are given that earlier question, your earlier answer, and the reference passages the earlier
            answer was written from. Each passage starts with a header naming its document and pages.

            Rules:
            1. Explain which facts in the passages support the main points of the earlier answer and how they
               lead to it. Point to each fact by its document name and page from the passage header,
               e.g. 「시흥시 외국인주민 생활안내」 p.12.
            2. Use only these passages. Do not add new facts, and never present other knowledge as the basis.
            3. If part of the earlier answer is not supported by the passages, say so plainly instead of
               inventing a basis.
            4. Do not write citation markers such as [1], passage numbers, internal IDs, or your hidden
               reasoning steps. Explain the link between the evidence and the answer in plain words.
            5. Write in %s. Keep Korean document names as written; a translation may follow in parentheses.
            6. Be concise: one summary sentence, then short bullet points (one per supporting fact).
            """;

    private final ChatModel model;

    public GeminiAnswerExplainer(ChatModel model) {
        this.model = model;
    }

    @Override
    public String explain(String question, String language, String pastQuestion, String pastAnswer,
                          List<RetrievedChunk> sources) {
        String answerLanguage = LANGUAGE_NAMES.getOrDefault(language, "Korean");
        StringBuilder text = new StringBuilder()
                .append("Earlier question:\n").append(pastQuestion).append("\n\n")
                .append("Your earlier answer:\n").append(pastAnswer).append("\n\n")
                .append("Reference passages of the earlier answer:\n\n");
        for (RetrievedChunk chunk : sources) {
            text.append(Citations.label(chunk)).append('\n').append(chunk.content().strip()).append("\n\n");
        }
        text.append("Current question:\n").append(question)
                .append("\n\n(Answer in ").append(answerLanguage).append(".)");
        try {
            String answer = model.chat(SystemMessage.from(SYSTEM_PROMPT.formatted(answerLanguage)),
                    UserMessage.from(text.toString())).aiMessage().text();
            return answer == null ? null : Citations.stripPassageNumbers(answer);
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Gemini explain failed", e);
        }
    }
}
