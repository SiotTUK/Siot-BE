package com.siot.backend.event.collect;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 행사 수집 테스트 공통: 설정값과 샘플 응답 (src/test/resources/event) */
final class EventTestFixtures {

    static final String GGC_URL = "https://ggc.test/open/json/playongoing";
    static final String TOURAPI_URL = "https://tour.test/searchFestival2";
    static final String TOURAPI_DETAIL_URL = "https://tour.test/detailCommon2";
    static final String FESTIVAL_URL = "https://festival.test/tn_pubr_public_cltur_fstvl_api";

    private EventTestFixtures() {
    }

    static EventProperties props(String apiKey, int ggcMaxPages) {
        return new EventProperties(apiKey, Duration.ofSeconds(5), Duration.ZERO,
                new EventProperties.Ggc(GGC_URL, 100, ggcMaxPages),
                new EventProperties.TourApi(TOURAPI_URL, 1000, 5, 365, "41", "390", TOURAPI_DETAIL_URL),
                new EventProperties.Festival(FESTIVAL_URL, 1000, 5, "경기도 시흥시"));
    }

    static String resource(String name) {
        try (InputStream in = EventTestFixtures.class.getResourceAsStream("/event/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
