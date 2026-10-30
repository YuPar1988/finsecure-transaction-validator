package com.finsecure.validator.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaskingTest {

    @Test
    void keepsOnlyLastFourDigits() {
        assertThat(Masking.maskAccount("4111111111111111")).isEqualTo("************1111");
    }

    @Test
    void shortValuesAreFullyMasked() {
        assertThat(Masking.maskAccount("123")).isEqualTo("****");
    }

    @Test
    void masksAccountInsideJson() {
        String json = "{\"transactionId\":\"TXN-1\",\"accountNumber\" : \"4111111111111111\",\"amount\":10}";
        assertThat(Masking.maskJson(json))
                .contains("\"accountNumber\" : \"************1111\"")
                .doesNotContain("4111111111111111");
    }

    @Test
    void leavesOtherJsonUntouched() {
        String json = "{\"status\":\"UP\"}";
        assertThat(Masking.maskJson(json)).isEqualTo(json);
    }
}
