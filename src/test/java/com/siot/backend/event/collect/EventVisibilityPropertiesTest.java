package com.siot.backend.event.collect;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** siot.event.public-regions 값 읽기와 검증 */
class EventVisibilityPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(EventVisibilityProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void readsCommaSeparatedRegions() {
        runner.withPropertyValues("siot.event.public-regions=SIHEUNG, GYEONGGI")
                .run(context -> assertThat(context.getBean(EventVisibilityProperties.class).publicRegions())
                        .containsExactlyInAnyOrder("SIHEUNG", "GYEONGGI"));
    }

    @Test
    void rejectsEmptyAndUnknownRegions() {
        runner.withPropertyValues("siot.event.public-regions=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("siot.event.public-regions=SIHEUNG,SEOUL")
                .run(context -> assertThat(context).hasFailed());
        runner.run(context -> assertThat(context).hasFailed());
    }
}
