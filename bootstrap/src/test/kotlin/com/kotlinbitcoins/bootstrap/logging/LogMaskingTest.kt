package com.kotlinbitcoins.bootstrap.logging

import com.kotlinbitcoins.bootstrap.support.ApplicationTestBase
import net.logstash.logback.argument.StructuredArguments.kv
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class LogMaskingTest : ApplicationTestBase() {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Test
    fun `masks sensitive fields and bearer tokens in JSON logs`(output: CapturedOutput) {
        logger.info(
            "login attempt {} {} {}",
            kv("password", "s3cr3t-pass"),
            kv("accessToken", "opaque-token-value"),
            kv("Authorization", "Bearer abc.def.ghi"),
        )
        logger.info("received header Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl")

        assertThat(output.out)
            .contains("login attempt")
            .contains("****")
            .doesNotContain("s3cr3t-pass")
            .doesNotContain("opaque-token-value")
            .doesNotContain("abc.def.ghi")
            .doesNotContain("eyJzdWIiOiJ4In0")
    }
}
