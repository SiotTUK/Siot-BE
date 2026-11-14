package com.siot.backend.rag.service;

import com.siot.backend.rag.dto.RelatedPage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * CB-10: 답변 근거 조각과 같은 문서 버전·겹치는 쪽을 근거로 삼는 항목(guide_document_reference)을 찾는다.
 * 쪽 정보가 없는 연결은 문서 전체를 근거로 본다.
 */
@Component
@RequiredArgsConstructor
public class RelatedPageFinder {

    static final int MAX_PAGES = 3;

    private static final String SQL = """
            SELECT g.kind, g.code, COALESCE(t.title, ko.title, g.code) AS title, MIN(g.sort_order) AS sort_order
            FROM document_chunk c
            JOIN guide_document_reference r ON r.document_version_id = c.document_version_id
             AND (r.page_start IS NULL OR c.page_end IS NULL OR r.page_start <= c.page_end)
             AND (r.page_end IS NULL OR c.page_start IS NULL OR r.page_end >= c.page_start)
            JOIN guide g ON g.guide_id = r.guide_id AND g.deleted_at IS NULL
            LEFT JOIN guide_translation t ON t.guide_id = g.guide_id AND t.language = :language
            LEFT JOIN guide_translation ko ON ko.guide_id = g.guide_id AND ko.language = 'ko'
            WHERE c.chunk_id IN (:chunkIds)
            GROUP BY g.kind, g.code, t.title, ko.title
            ORDER BY MIN(g.sort_order), g.code
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public List<RelatedPage> find(Collection<Long> chunkIds, String language) {
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SQL, new MapSqlParameterSource()
                        .addValue("chunkIds", chunkIds)
                        .addValue("language", language)
                        .addValue("limit", MAX_PAGES),
                (rs, rowNum) -> new RelatedPage(rs.getString("kind"), rs.getString("code"), rs.getString("title")));
    }

    /**
     * 질문에 항목 제목(공백 무시) 또는 제목의 첫 단어(3자 이상)가 들어 있는 항목.
     * 많이 맞은 순: 제목 전체 > 긴 첫 단어 > 짧은 첫 단어 ("자동차세" 질문은 "자동차세 내기"가 "자동차 …"보다 먼저).
     * 문서 연결이 없을 때(CB-10 생활수칙 등)와 AI 오류로 검색하지 못했을 때(CB-02) 쓴다.
     * 한국어 제목과 질문 언어 제목을 모두 비교한다.
     */
    private static final String TITLE_SQL = """
            SELECT kind, code, title FROM (
                SELECT g.kind, g.code, COALESCE(t.title, ko.title) AS title, MIN(g.sort_order) AS sort_order,
                       MAX(CASE
                           WHEN :question LIKE '%' || REPLACE(LOWER(x.title), ' ', '') || '%'
                               THEN 1000 + CHAR_LENGTH(x.title)
                           WHEN CHAR_LENGTH(SPLIT_PART(x.title, ' ', 1)) >= 3
                                AND :question LIKE '%' || LOWER(SPLIT_PART(x.title, ' ', 1)) || '%'
                               THEN CHAR_LENGTH(SPLIT_PART(x.title, ' ', 1))
                           ELSE 0 END) AS score
                FROM guide g
                JOIN guide_translation ko ON ko.guide_id = g.guide_id AND ko.language = 'ko'
                LEFT JOIN guide_translation t ON t.guide_id = g.guide_id AND t.language = :language
                JOIN guide_translation x ON x.guide_id = g.guide_id AND x.language IN ('ko', :language)
                WHERE g.deleted_at IS NULL
                GROUP BY g.kind, g.code, t.title, ko.title
            ) m
            WHERE score > 0
            ORDER BY score DESC, sort_order, code
            LIMIT :limit
            """;

    public List<RelatedPage> findByTitle(String question, String language) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String compact = question.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
        return jdbc.query(TITLE_SQL, new MapSqlParameterSource()
                        .addValue("question", compact)
                        .addValue("language", language)
                        .addValue("limit", MAX_PAGES),
                (rs, rowNum) -> new RelatedPage(rs.getString("kind"), rs.getString("code"), rs.getString("title")));
    }
}
