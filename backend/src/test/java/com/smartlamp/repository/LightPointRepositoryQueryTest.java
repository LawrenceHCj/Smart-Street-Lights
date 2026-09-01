package com.smartlamp.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class LightPointRepositoryQueryTest {
    @Test
    void energyBucketSelectAndGroupByUseTheSameExpression() throws Exception {
        Query query = LightPointRepository.class
                .getMethod("findEnergyBuckets", long.class, long.class)
                .getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("FLOOR(ts / 300000) * 300000 AS bucketTs")
                .contains("GROUP BY device_code, FLOOR(ts / 300000) * 300000");
    }
}
