package com.siot.backend.rag.ai;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;

/** Gemini 로 질문을 한국어 검색어(독립 질문)로 바꾼다 (CB-07, CB-14). */
public class GeminiQueryRewriter implements QueryRewriter {

    static final String SYSTEM_PROMPT = """
            You turn a user's question into a standalone Korean search query for Korean documents
            about living in Korea as a foreign resident.
            - Write the query in Korean. If the question is in another language, translate it into natural Korean
              and use the Korean terms that official documents use (e.g. 외국인등록, 체류기간 연장, 연말정산).
            - Keep visa codes (E-9, F-4 ...), numbers and proper nouns as they are.
            - If conversation history is given, use it only to resolve what the question refers to
              (e.g. "it", "that", "그거"). If the question is already standalone or changes the topic, do not add to it.
            - Output only the query, with no explanation or quotes.
            """;

    private final ChatModel model;

    public GeminiQueryRewriter(ChatModel model) {
        this.model = model;
    }

    @Override
    public String rewrite(String question, List<ChatTurn> history) {
        StringBuilder text = new StringBuilder();
        if (!history.isEmpty()) {
            text.append("Conversation history:\n");
            for (ChatTurn turn : history) {
                text.append("User: ").append(turn.question()).append('\n');
                if (turn.answer() != null) {
                    text.append("Assistant: ").append(turn.answer()).append('\n');
                }
            }
            text.append('\n');
        }
        text.append("Question:\n").append(question);
        try {
            String rewritten = model.chat(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(text.toString()))
                    .aiMessage().text();
            return rewritten == null ? null : rewritten.strip();
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Gemini query rewrite failed", e);
        }
    }
}
