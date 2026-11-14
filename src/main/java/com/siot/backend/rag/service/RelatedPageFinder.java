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
}
