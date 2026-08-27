package com.smartlamp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

class SmartlampApplicationTests {

    @Test
    void applicationEnablesSpringBootAndScheduling() {
        assertThat(AnnotatedElementUtils.hasAnnotation(SmartlampApplication.class, SpringBootApplication.class)).isTrue();
        assertThat(AnnotatedElementUtils.hasAnnotation(SmartlampApplication.class, EnableScheduling.class)).isTrue();
    }
}
