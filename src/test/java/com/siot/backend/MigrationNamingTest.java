package com.siot.backend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 마이그레이션 파일 이름 규칙 검사 (docs/conventions.md).
 * V1~V4 는 초기 골격, 이후 파일은 작성 시각 기반 버전을 사용해 팀원 간 번호 충돌을 막는다.
 */
class MigrationNamingTest {

    private static final Set<String> LEGACY = Set.of(
            "V1__enable_extensions.sql",
            "V2__init_schema.sql",
            "V3__add_requirement_columns.sql",
            "V4__add_notification_email_delivery.sql");

    /** 예: V2026_11_08_1530__add_event_category.sql */
    private static final Pattern TIMESTAMP_NAME =
            Pattern.compile("V20\\d{2}_(0[1-9]|1[0-2])_(0[1-9]|[12]\\d|3[01])_([01]\\d|2[0-3])[0-5]\\d__[a-z0-9_]+\\.sql");

    @Test
    void migrationFilesFollowNamingRule() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/*.sql");
        List<String> names = Arrays.stream(resources).map(Resource::getFilename).toList();

        assertThat(names).containsAll(LEGACY);
        assertThat(names.stream().filter(name -> !LEGACY.contains(name)))
                .as("새 마이그레이션은 V{yyyy}_{MM}_{dd}_{HHmm}__{snake_case_설명}.sql 형식이어야 합니다")
                .allMatch(name -> TIMESTAMP_NAME.matcher(name).matches());
    }
}
