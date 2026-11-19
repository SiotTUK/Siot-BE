package com.siot.backend.translation;

import com.siot.backend.rag.ai.AiUnavailableException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeminiTranslatorTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ChatModel model = mock(ChatModel.class);
    private final GeminiTranslator translator = new GeminiTranslator(model, jsonMapper);

    @Test
    void translatesAllLanguagesInOneJsonCall() {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(
                "{\"en\":\"Siheung Gaetgol Festival (시흥갯골축제)\",\"zh\":\"始兴滩涂节 (시흥갯골축제)\",\"vi\":\"Lễ hội Gaetgol Siheung (시흥갯골축제)\"}"));

        Map<String, String> result = translator.translateAll("시흥갯골축제");

        assertThat(result).containsExactly(
                Map.entry("en", "Siheung Gaetgol Festival (시흥갯골축제)"),
                Map.entry("zh", "始兴滩涂节 (시흥갯골축제)"),
                Map.entry("vi", "Lễ hội Gaetgol Siheung (시흥갯골축제)"));

        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model, times(1)).chat(request.capture());
        assertThat(request.getValue().responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
        JsonObjectSchema schema = (JsonObjectSchema) request.getValue().responseFormat().jsonSchema().rootElement();
        assertThat(schema.required()).containsExactly("en", "zh", "vi");
    }

    @Test
    void translatesSingleLanguage() {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply("{\"vi\":\"Đăng ký người nước ngoài\"}"));

        assertThat(translator.translate("외국인등록", "vi")).isEqualTo("Đăng ký người nước ngoài");

        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model).chat(request.capture());
        JsonObjectSchema schema = (JsonObjectSchema) request.getValue().responseFormat().jsonSchema().rootElement();
        assertThat(schema.required()).containsExactly("vi");
    }

    @Test
    void returnsSourceForKoreanOrBlank() {
        assertThat(translator.translate("원문", "ko")).isEqualTo("원문");
        assertThat(translator.translate(" ", "en")).isEqualTo(" ");
        assertThat(translator.translateAll(null)).containsOnlyKeys("en", "zh", "vi").containsValue(null);
        verifyNoInteractions(model);
    }

    @Test
    void rejectsUnsupportedLanguage() {
        assertThatThrownBy(() -> translator.translate("원문", "ja")).isInstanceOf(IllegalArgumentException.class);
    }

    /** 행사 설명처럼 긴 글: 줄바꿈·따옴표·역슬래시·URL 이 JSON 왕복 후에도 그대로여야 한다 */
    @Test
    void keepsLongTextIntactThroughJson() {
        String source = longEventDescription();
        Map<String, String> translations = new LinkedHashMap<>();
        translations.put("en", longTranslation("Siheung Gaetgol Festival (시흥갯골축제) program", source.length()));
        translations.put("zh", longTranslation("始兴滩涂节 (시흥갯골축제) 活动", source.length()));
        translations.put("vi", longTranslation("Chương trình Lễ hội Gaetgol Siheung (시흥갯골축제)", source.length()));
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(jsonMapper.writeValueAsString(translations)));

        Map<String, String> result = translator.translateAll(source);

        assertThat(result).isEqualTo(translations);
        assertThat(result.get("en")).contains("\n- ", "\"Welcome\"", "C:\\path", "https://www.siheung.go.kr/event?id=1&lang=en");

        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model).chat(request.capture());
        String userText = ((UserMessage) request.getValue().messages().get(1)).singleText();
        assertThat(userText).contains(source);
    }

    @Test
    void failsWhenLongOutputIsCutOff() {
        String truncated = "{\"en\":\"" + "a".repeat(5000);
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(truncated, FinishReason.LENGTH));

        assertThatThrownBy(() -> translator.translateAll(longEventDescription()))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("cut off");
        verify(model, times(GeminiTranslator.MAX_ATTEMPTS)).chat(any(ChatRequest.class));
    }

    @Test
    void retriesOnceWhenJsonIsBroken() {
        when(model.chat(any(ChatRequest.class)))
                .thenReturn(reply("{\"en\": \"unterminated"))
                .thenReturn(reply("{\"en\":\"Hello\",\"zh\":\"你好\",\"vi\":\"Xin chào\"}"));

        assertThat(translator.translateAll("안녕하세요")).containsEntry("en", "Hello");
        verify(model, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void failsWhenKoreanRemainsOutsideParentheses() {
        when(model.chat(any(ChatRequest.class)))
                .thenReturn(reply("{\"en\":\"Siheung 갯골 Park\",\"zh\":\"公园\",\"vi\":\"Công viên\"}"));

        assertThatThrownBy(() -> translator.translateAll("시흥갯골생태공원"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("Korean");
        verify(model, times(GeminiTranslator.MAX_ATTEMPTS)).chat(any(ChatRequest.class));
    }

    @Test
    void failsWhenLanguageMissing() {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply("{\"en\":\"Hello\",\"zh\":\"你好\"}"));

        assertThatThrownBy(() -> translator.translateAll("안녕하세요"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("vi");
    }

    @Test
    void wrapsModelErrorWithoutExtraCall() {
        when(model.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("429 Too Many Requests"));

        assertThatThrownBy(() -> translator.translateAll("안녕하세요"))
                .isInstanceOf(AiUnavailableException.class)
                .hasCauseInstanceOf(RuntimeException.class);
        verify(model, times(1)).chat(any(ChatRequest.class));
    }

    private static ChatResponse reply(String text) {
        return reply(text, FinishReason.STOP);
    }

    private static ChatResponse reply(String text, FinishReason finishReason) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).finishReason(finishReason).build();
    }

    private static String longEventDescription() {
        StringBuilder text = new StringBuilder("2026 시흥갯골축제 \"함께 만드는 축제\" 안내\n\n");
        for (int i = 1; i <= 40; i++) {
            text.append("- ").append(i).append("일차: 시흥갯골생태공원(시흥시 동서로 287)에서 10:00~17:00 체험 프로그램을 운영합니다.\n")
                    .append("\t참가비 5,000원, 문의 031-310-1234, 신청 https://www.siheung.go.kr/event?id=").append(i).append("&lang=ko\n");
        }
        return text.toString();
    }

    private static String longTranslation(String title, int minLength) {
        StringBuilder text = new StringBuilder(title).append(" \"Welcome\"\n\n");
        int i = 1;
        while (text.length() < minLength) {
            text.append("- Day ").append(i++).append(": 10:00-17:00, fee 5,000 KRW, call 031-310-1234, C:\\path\n")
                    .append("\thttps://www.siheung.go.kr/event?id=1&lang=en\n");
        }
        return text.toString();
    }
}
