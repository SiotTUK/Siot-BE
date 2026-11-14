package com.siot.backend.rag.ai;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;

/** Gemini 로 이어지는 질문을 독립 질문으로 바꾼다 (CB-07). */
public class GeminiQueryRewriter implements QueryRewriter {

    static final String SYSTEM_PROMPT = """
            You rewrite a follow-up question into a standalone search query.
            - Use the conversation history only to resolve what the follow-up refers to (e.g. "it", "that", "그거").
            - Keep the language of the follow-up question.
            - If the follow-up is already standalone or changes the topic, return it unchanged.
            - Output only the rewritten question, with no explanation or quotes.
            """;

    private final ChatModel model;

    public GeminiQueryRewriter(ChatModel model) {
        this.model = model;
    }

    @Override
    public String rewrite(String question, List<ChatTurn> history) {
        StringBuilder text = new StringBuilder("Conversation history:\n");
        for (ChatTurn turn : history) {
            text.append("User: ").append(turn.question()).append('\n');
            if (turn.answer() != null) {
                text.append("Assistant: ").append(turn.answer()).append('\n');
            }
        }
        text.append("\nFollow-up question:\n").append(question);
        try {
            String rewritten = model.chat(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(text.toString()))
                    .aiMessage().text();
            return rewritten == null ? null : rewritten.strip();
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Gemini query rewrite failed", e);
        }
    }
}
