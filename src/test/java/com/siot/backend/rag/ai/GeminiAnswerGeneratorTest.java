package com.siot.backend.rag.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CB-15: 근거가 한국어여도 질문 언어로 답한다 */
class GeminiAnswerGeneratorTest {

    private static final List<RetrievedChunk> CONTEXT = List.of(new RetrievedChunk(
            1L, "주민세는 8월 16일부터 31일까지 냅니다.", 11, 11, "시흥시 외국인주민 생활안내", null, 0.9));

    private final ChatModel model = mock(ChatModel.class);
    private final GeminiAnswerGenerator generator = new GeminiAnswerGenerator(model);

    @Test
    void asksForTheQuestionLanguage() {
        when(model.chat(anyList())).thenReturn(response("Pay it from August 16 to 31."));

        assertThat(generator.generate("When do I pay the resident tax?", "en", CONTEXT, List.of()))
                .isEqualTo("Pay it from August 16 to 31.");

        List<ChatMessage> messages = sentMessages(1).get(0);
        assertThat(((SystemMessage) messages.get(0)).text()).contains("Write the whole answer in English");
        assertThat(((UserMessage) messages.get(messages.size() - 1)).singleText()).endsWith("(Answer in English.)");
    }

    @Test
    void retriesOnceWhenAnsweredInKorean() {
        when(model.chat(anyList())).thenReturn(
                response("주민세는 8월 16일부터 31일까지 내요."),
                response("Pay the resident tax from August 16 to 31."));

        assertThat(generator.generate("When do I pay the resident tax?", "en", CONTEXT, List.of()))
                .isEqualTo("Pay the resident tax from August 16 to 31.");

        List<ChatMessage> retry = sentMessages(2).get(1);
        assertThat(((AiMessage) retry.get(retry.size() - 2)).text()).isEqualTo("주민세는 8월 16일부터 31일까지 내요.");
        assertThat(((UserMessage) retry.get(retry.size() - 1)).singleText()).contains("in English only");
    }

    @Test
    void doesNotRetryKoreanQuestionsOrKoreanNamesInParentheses() {
        when(model.chat(anyList())).thenReturn(
                response("주민세는 8월에 내요."),
                response("Apply at the Ansan Immigration Office (안산출입국·외국인사무소) after booking on HiKorea."));

        generator.generate("주민세는 언제 내요?", "ko", CONTEXT, List.of());
        generator.generate("Where do I apply?", "en", CONTEXT, List.of());

        verify(model, times(2)).chat(anyList());
    }

    @Test
    void detectsMostlyKoreanText() {
        assertThat(GeminiAnswerGenerator.isMostlyKorean("주민세는 8월 16일부터 31일까지 내요.")).isTrue();
        assertThat(GeminiAnswerGenerator.isMostlyKorean("Call the center (외국인종합안내센터) at 1345.")).isFalse();
        assertThat(GeminiAnswerGenerator.isMostlyKorean("居民税在8月缴纳（주민세）。")).isFalse();
        assertThat(GeminiAnswerGenerator.isMostlyKorean("1345")).isFalse();
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    @SuppressWarnings("unchecked")
    private List<List<ChatMessage>> sentMessages(int calls) {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(model, times(calls)).chat(captor.capture());
        return captor.getAllValues();
    }
}
