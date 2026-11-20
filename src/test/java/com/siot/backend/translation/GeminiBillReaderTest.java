package com.siot.backend.translation;

import com.siot.backend.rag.ai.AiUnavailableException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class GeminiBillReaderTest {

    private static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
    private static final String BILL_JSON = """
            {"readable":true,"documentType":"TAX_NOTICE","issuer":"시흥시","taxType":"자동차세",
             "taxObject":"12가****","amount":123450,"dueDate":"2026-12-31","lateAmount":127150,
             "lateDueDate":"2027-01-31","paymentMethods":["Wetax (위택스)","Bank (은행)"],"contact":"031-310-2114",
             "originalLines":["납기내 금액 123,450원"],
             "summary":{"what":"Car tax notice","due":"Pay by 2026-12-31","amount":"123,450 won","howToPay":"Wetax"}}
            """;

    private final ChatModel model = mock(ChatModel.class);
    private final GeminiBillReader reader = new GeminiBillReader(model, JsonMapper.builder().build(), Duration.ZERO);

    @Test
    void readsBillWithImageAndJsonSchemaInOneCall() {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(BILL_JSON));

        BillReading reading = reader.read(IMAGE, "image/jpeg", "en");

        assertThat(reading.readable()).isTrue();
        assertThat(reading.documentType()).isEqualTo("TAX_NOTICE");
        assertThat(reading.taxType()).isEqualTo("자동차세");
        assertThat(reading.amount()).isEqualTo(123450L);
        assertThat(reading.dueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(reading.lateAmount()).isEqualTo(127150L);
        assertThat(reading.penalty()).isNull();
        assertThat(reading.paymentMethods()).containsExactly("Wetax (위택스)", "Bank (은행)");
        assertThat(reading.summary().amount()).isEqualTo("123,450 won");

        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model, times(1)).chat(request.capture());
        assertThat(request.getValue().responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
        JsonObjectSchema schema = (JsonObjectSchema) request.getValue().responseFormat().jsonSchema().rootElement();
        assertThat(schema.required()).containsExactly("readable");
        UserMessage user = (UserMessage) request.getValue().messages().get(1);
        assertThat(user.contents()).anySatisfy(content -> {
            assertThat(content).isInstanceOf(ImageContent.class);
            assertThat(((ImageContent) content).image().mimeType()).isEqualTo("image/jpeg");
        });
        assertThat(user.contents().toString()).contains("en (English)");
    }

    @Test
    void unreadableKeepsOnlyReason() {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(
                "{\"readable\":false,\"retakeReason\":\"CROPPED\",\"amount\":5000}"));

        BillReading reading = reader.read(IMAGE, "image/png", "ko");

        assertThat(reading.readable()).isFalse();
        assertThat(reading.retakeReason()).isEqualTo("CROPPED");
        assertThat(reading.amount()).isNull();
    }

    @Test
    void invalidValuesBecomeNullInsteadOfGuesses() {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(
                "{\"readable\":true,\"documentType\":\"PARKING\",\"amount\":\"약 10만원\",\"dueDate\":\"12월 말\"}"));

        BillReading reading = reader.read(IMAGE, "image/png", "ko");

        assertThat(reading.documentType()).isEqualTo("OTHER");
        assertThat(reading.amount()).isNull();
        assertThat(reading.dueDate()).isNull();
        assertThat(reading.summary()).isNull();
    }

    @Test
    void retriesOnceOnRateLimitThenSucceeds() {
        when(model.chat(any(ChatRequest.class)))
                .thenThrow(new RateLimitException("quota"))
                .thenReturn(reply(BILL_JSON));

        assertThat(reader.read(IMAGE, "image/jpeg", "en").amount()).isEqualTo(123450L);
        verify(model, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void retriesOnlyOnceOn503() {
        when(model.chat(any(ChatRequest.class)))
                .thenThrow(new InternalServerException(new HttpException(503, "overloaded")));

        assertThatThrownBy(() -> reader.read(IMAGE, "image/jpeg", "en"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("503");
        verify(model, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void doesNotRetryOtherErrors() {
        when(model.chat(any(ChatRequest.class))).thenThrow(new AuthenticationException("bad key"));

        assertThatThrownBy(() -> reader.read(IMAGE, "image/jpeg", "en")).isInstanceOf(AiUnavailableException.class);
        verify(model, times(1)).chat(any(ChatRequest.class));
    }

    /** 응답이 깨져도 예외 메시지·로그에 응답 내용(금액·이름)이 남지 않는다 */
    @Test
    void brokenResponseDoesNotLeakContent(CapturedOutput output) {
        when(model.chat(any(ChatRequest.class))).thenReturn(reply("{\"readable\":true,\"issuer\":\"홍길동 123,450원"));

        assertThatThrownBy(() -> reader.read(IMAGE, "image/jpeg", "en"))
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain("홍길동", "123,450");
                    assertThat(e.getCause()).isNull();
                });
        assertThat(output.getAll()).doesNotContain("홍길동", "123,450");
    }

    @Test
    void errorFromModelIsNotChained(CapturedOutput output) {
        when(model.chat(any(ChatRequest.class)))
                .thenThrow(new RuntimeException("request body: 홍길동 123,450원"));

        assertThatThrownBy(() -> reader.read(IMAGE, "image/jpeg", "en"))
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain("홍길동", "123,450");
                    assertThat(e.getCause()).isNull();
                });
        assertThat(output.getAll()).doesNotContain("홍길동", "123,450");
    }

    /** 칸에 값 대신 긴 설명을 늘어놓은 응답 (라이브에서 flash-lite 로 확인됨) 은 쓰지 않는다 */
    @Test
    void rambleInsteadOfValueFails() {
        String ramble = "자동차세 외 1건 (규칙에 따라 taxType은 '자동차세'로 기재 가능하나 원문에 있는 내용 반영 가능함. "
                + "세목이 두 개 이상이므로 '자동차세 및 지방교육세' 활용 가능)";
        when(model.chat(any(ChatRequest.class))).thenReturn(reply(
                "{\"readable\":true,\"taxType\":\"" + ramble + "\",\"amount\":259740}"));

        assertThatThrownBy(() -> reader.read(IMAGE, "image/png", "en"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("taxType")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("자동차세"));
    }

    @Test
    void cutOffResponseFails() {
        when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"readable\":true,\"issuer\":\"시"))
                .finishReason(FinishReason.LENGTH)
                .build());

        assertThatThrownBy(() -> reader.read(IMAGE, "image/jpeg", "en")).isInstanceOf(AiUnavailableException.class);
    }

    private static ChatResponse reply(String json) {
        return ChatResponse.builder().aiMessage(AiMessage.from(json)).finishReason(FinishReason.STOP).build();
    }
}
