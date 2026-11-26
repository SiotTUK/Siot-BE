package com.siot.backend.rag.service;

import com.siot.backend.rag.dto.RelatedPage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CB-10: 행사를 묻는 질문({@link EventIntentDetector})에 다가오는 행사 상세 링크를 찾는다.
 * <p>
 * 행사 데이터(event, event_translation)는 역할 B 가 수집·관리한다. 여기서는 읽기만 하며, 다음 약속에 기댄다.
 * <ul>
 *   <li>보여 줄 행사 = {@code status = 'ACTIVE'} (관리자 삭제·취소는 다른 값)</li>
 *   <li>{@code event_id} 는 재수집해도 바뀌지 않는다 (출처 + 원본 ID 로 갱신) → 프론트엔드 경로 /events/{eventId}</li>
 *   <li>모든 행사에 ko 번역 행이 있다 (질문 언어 번역이 없으면 ko 제목)</li>
 * </ul>
 * 순서: 질문에 들어 있는 제목 단어 수(많은 순) → 질문이 말한 카테고리(공연·전시 등) → 다문화 행사 → 시작일 빠른 순.
 */
@Component
@RequiredArgsConstructor
public class EventRelatedPageFinder {

    public static final String TYPE = "EVENT";
    /** 점수를 매길 후보 수 (시작일 빠른 순). 끝나지 않은 행사가 이보다 많아도 질문 단어가 맞는 행사가 앞에 오도록 넉넉히 */
    static final int CANDIDATES = 200;
    /** 제목 단어로 볼 최소 길이 ("시" 같은 한 글자 제외) */
    private static final int MIN_TOKEN_LENGTH = 2;

    /** 끝나지 않은 행사: 종료일이 오늘 이후, 종료일이 없으면 시작일이 오늘 이후 (날짜가 모두 없으면 제외) */
    private static final String SQL = """
            SELECT e.event_id, e.is_multicultural, e.starts_on, e.category,
                   ko.title AS ko_title, t.title AS title
            FROM event e
            JOIN event_translation ko ON ko.event_id = e.event_id AND ko.language = 'ko'
            LEFT JOIN event_translation t ON t.event_id = e.event_id AND t.language = :language
            WHERE e.status = 'ACTIVE'
              AND COALESCE(e.ends_on, e.starts_on) >= :today
            ORDER BY e.starts_on NULLS LAST, e.event_id
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    private record Candidate(long id, boolean multicultural, LocalDate startsOn, String koTitle, String title, int score,
                             boolean categoryMatch) {
    }

    /** 행사 질문이 아니면 빈 목록 */
    public List<RelatedPage> find(String question, String language, int limit) {
        if (limit <= 0 || !EventIntentDetector.detect(question)) {
            return List.of();
        }
        String compact = compact(question);
        // "공연 있어요?" 처럼 카테고리를 말하면 그 카테고리 행사를 먼저 (EVE-10 카테고리)
        Set<String> wanted = EventIntentDetector.categories(question).stream().map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        List<Candidate> candidates = jdbc.query(SQL, new MapSqlParameterSource()
                        .addValue("language", language)
                        .addValue("today", LocalDate.now(clock))
                        .addValue("limit", CANDIDATES),
                (rs, rowNum) -> {
                    String koTitle = rs.getString("ko_title");
                    String title = rs.getString("title");
                    return new Candidate(rs.getLong("event_id"), rs.getBoolean("is_multicultural"),
                            rs.getObject("starts_on", LocalDate.class), koTitle, title,
                            score(compact, koTitle) + (title == null ? 0 : score(compact, title)),
                            wanted.contains(rs.getString("category")));
                });
        return candidates.stream()
                .sorted(Comparator.comparingInt(Candidate::score).reversed()
                        .thenComparing(Candidate::categoryMatch, Comparator.reverseOrder())
                        .thenComparing(Candidate::multicultural, Comparator.reverseOrder())
                        .thenComparing(Candidate::startsOn, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(Candidate::id))
                .limit(limit)
                .map(c -> new RelatedPage(TYPE, String.valueOf(c.id()), c.title() != null ? c.title() : c.koTitle()))
                .toList();
    }

    /** 질문(공백 제거)에 들어 있는 제목 단어 수. 조사가 붙어도 맞도록 제목 단어가 질문에 들어 있는지 본다 */
    static int score(String compactQuestion, String title) {
        if (title == null) {
            return 0;
        }
        return (int) Arrays.stream(title.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}·「」『』()（）\\[\\]]+"))
                .filter(token -> token.length() >= MIN_TOKEN_LENGTH)
                .distinct()
                .filter(compactQuestion::contains)
                .count();
    }

    private static String compact(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
