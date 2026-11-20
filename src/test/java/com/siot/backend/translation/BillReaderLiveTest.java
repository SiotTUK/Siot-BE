package com.siot.backend.translation;

import com.siot.backend.checklist.repository.GuideRepository;
import com.siot.backend.checklist.repository.GuideTranslationRepository;
import com.siot.backend.global.common.RequestRateLimiter;
import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.member.repository.AppUserRepository;
import com.siot.backend.rag.config.GeminiProperties;
import com.siot.backend.translation.bill.BillTranslationResponse;
import com.siot.backend.translation.bill.BillTranslationService;
import com.siot.backend.translation.config.TranslationConfig;
import com.siot.backend.translation.config.TranslationProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 Gemini 로 고지서 사진을 읽는다 (RUL-07). 평소 ./gradlew test 와 CI 에서는 건너뛴다.
 * <pre>
 * GEMINI_LIVE=true LIVE_BILL=.claude-docs/bill-samples ./gradlew test --tests '*BillReaderLiveTest' --rerun
 * </pre>
 * <ul>
 *     <li>LIVE_BILL: 사진·PDF 경로 또는 폴더 (폴더면 안의 jpg·png·pdf 전부). 쉼표로 여러 개. 없으면 건너뜀</li>
 *     <li>LIVE_LANGUAGE: 요약 언어 (기본 en)</li>
 *     <li>GEMINI_API_KEY, GEMINI_CHAT_MODEL: .env 또는 환경 변수</li>
 * </ul>
 * 서비스 처리(가림·경고·요약 확인)를 거친 응답 JSON 을 출력하고 build/live/bill-*.json 에 저장한다.
 * 실제 고지서를 쓸 때는 이름·주소·번호를 가리고 찍는다. 결과 파일은 커밋하지 않는다.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE", matches = "true")
class BillReaderLiveTest {

    private static final Path OUTPUT = Path.of("build", "live");

    @Test
    void readRealBills() throws IOException {
        String target = System.getenv("LIVE_BILL");
        assumeTrue(target != null && !target.isBlank(), "LIVE_BILL is not set");
        String language = System.getenv().getOrDefault("LIVE_LANGUAGE", "en");

        BillTranslationService service = service(props());
        JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        Files.createDirectories(OUTPUT);

        List<Path> files = new ArrayList<>();
        for (String part : target.split(",")) {
            files.addAll(files(Path.of(part.strip())));
        }
        // Gemini 가 붐비면(503) 한 파일이 실패해도 나머지는 계속 확인하고 마지막에 실패를 알린다
        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            long start = System.nanoTime();
            try {
                BillTranslationResponse response = service.translate(1L, new MockMultipartFile("file",
                        file.getFileName().toString(), mimeType(file), Files.readAllBytes(file)), true, language);
                long millis = (System.nanoTime() - start) / 1_000_000;
                String body = json.writeValueAsString(response);
                System.out.println("==== " + file.getFileName() + " (" + millis + " ms)\n" + body);
                Files.writeString(OUTPUT.resolve("bill-" + file.getFileName() + ".json"), body);
                assertThat(response.language()).isEqualTo(language);
            } catch (BusinessException e) {
                System.out.println("==== " + file.getFileName() + " FAILED: " + e.getErrorCode());
                failures.add(file.getFileName() + ": " + e.getErrorCode());
            }
        }
        assertThat(failures).as("failed bills").isEmpty();
    }

    private static BillTranslationService service(GeminiProperties props) {
        TranslationProperties translationProps = new TranslationProperties(DataSize.ofMegabytes(10),
                Duration.ofSeconds(5), new TranslationProperties.RateLimit(100, Duration.ofHours(1), Duration.ofMillis(1)));
        BillReader reader = new TranslationConfig().billReader(props, translationProps, JsonMapper.builder().build());
        RequestRateLimiter limiter = mock(RequestRateLimiter.class);
        when(limiter.tryAcquire(anyString(), any(), anyInt(), any())).thenReturn(RequestRateLimiter.Result.ALLOWED);
        GuideRepository guides = mock(GuideRepository.class);
        when(guides.findByCodeIn(any())).thenReturn(List.of());
        AppUserRepository users = mock(AppUserRepository.class);
        return new BillTranslationService(reader, limiter, translationProps, users, guides,
                mock(GuideTranslationRepository.class), Clock.system(java.time.ZoneId.of("Asia/Seoul")));
    }

    private static List<Path> files(Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            return List.of(target);
        }
        try (Stream<Path> list = Files.list(target)) {
            List<Path> files = new ArrayList<>(list.filter(path -> mimeType(path) != null).sorted().toList());
            assertThat(files).as("bill files in " + target).isNotEmpty();
            return files;
        }
    }

    private static String mimeType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        return name.endsWith(".pdf") ? "application/pdf" : null;
    }

    private static GeminiProperties props() throws IOException {
        Map<String, String> env = readDotEnv(Path.of(".env"));
        String apiKey = System.getenv("GEMINI_API_KEY") != null ? System.getenv("GEMINI_API_KEY") : env.get("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY in .env or environment").isNotBlank();
        String chatModel = System.getenv("GEMINI_CHAT_MODEL") != null
                ? System.getenv("GEMINI_CHAT_MODEL")
                : env.getOrDefault("GEMINI_CHAT_MODEL", "gemini-2.5-flash");
        System.out.println("chat model: " + chatModel);
        return new GeminiProperties(apiKey, chatModel, "gemini-embedding-001", 768, 0.2, Duration.ofSeconds(60));
    }

    private static Map<String, String> readDotEnv(Path path) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(path)) {
            return values;
        }
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.strip();
            int eq = trimmed.indexOf('=');
            if (trimmed.isEmpty() || trimmed.startsWith("#") || eq < 0) {
                continue;
            }
            values.put(trimmed.substring(0, eq).strip(), trimmed.substring(eq + 1).strip().replaceAll("^\"|\"$", ""));
        }
        return values;
    }
}
