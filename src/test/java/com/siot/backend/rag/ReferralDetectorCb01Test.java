package com.siot.backend.rag;

import com.siot.backend.rag.domain.ReferralTopic;
import com.siot.backend.rag.service.ReferralDetector;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CB-11 × CB-01: 평가 세트 질문으로 담당 기관 안내 감지를 확인한다.
 * 판단 질문(JUDGE)은 답변 상태와 상관없이 담당 기관 안내가 붙어야 통과이므로 4개 언어 모두 감지해야 한다.
 */
class ReferralDetectorCb01Test {

    /** 판단을 묻는 성격이 있어 안내가 붙어도 되는 정보 질문 (가능 여부를 묻는 비자 질문) */
    private static final Set<String> ALLOWED_ON_ANSWER = Set.of(
            "CB01-STAY-06/ko", "CB01-STAY-06/en", "CB01-STAY-06/zh", "CB01-STAY-06/vi",
            "CB01-HEALTH-05/vi");

    @Test
    void detectsEveryJudgmentQuestionAndNoInformationQuestion() throws Exception {
        JsonNode set;
        try (InputStream in = getClass().getResourceAsStream("/cb01/cb01-evaluation-set.json")) {
            set = JsonMapper.builder().build().readTree(in);
        }
        JsonNode items = set.isArray() ? set : set.path("items");
        List<String> missedJudgments = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        for (JsonNode item : items) {
            String id = item.path("id").asString();
            String type = item.path("type").asString();
            for (Map.Entry<String, JsonNode> question : item.path("question").properties()) {
                String key = id + "/" + question.getKey();
                List<ReferralTopic> topics = ReferralDetector.detect(question.getValue().asString());
                if ("JUDGE".equals(type) && topics.isEmpty()) {
                    missedJudgments.add(key + " " + question.getValue().asString());
                } else if (!"JUDGE".equals(type) && !topics.isEmpty() && !ALLOWED_ON_ANSWER.contains(key)) {
                    unexpected.add(key + " " + topics + " " + question.getValue().asString());
                }
            }
        }
        assertThat(missedJudgments).isEmpty();
        assertThat(unexpected).isEmpty();
    }
}
