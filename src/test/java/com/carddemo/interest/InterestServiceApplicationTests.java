package com.carddemo.interest;

import com.carddemo.interest.application.InterestCalculationJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InterestServiceApplicationTests {

    @Autowired
    private InterestCalculationJob interestCalculationJob;

    @Test
    void contextLoads() {
        assertThat(interestCalculationJob).isNotNull();
    }
}
