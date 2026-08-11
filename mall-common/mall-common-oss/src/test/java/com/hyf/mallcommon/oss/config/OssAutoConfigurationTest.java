package com.hyf.mallcommon.oss.config;

import com.aliyun.oss.OSS;
import com.hyf.mallcommon.oss.controller.OssController;
import com.hyf.mallcommon.oss.service.OssService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OssAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssAutoConfiguration.class));

    @Test
    void usesLocalUploadWhenCredentialsAreMissing() {
        contextRunner
                .withPropertyValues(
                        "alioss.endpoint=oss-cn-beijing.aliyuncs.com",
                        "alioss.bucket-name=skyhyf")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context).hasSingleBean(OssService.class);
                    assertThat(context).hasSingleBean(OssController.class);
                });
    }

    @Test
    void createsOssBeansWhenAllRequiredPropertiesArePresent() {
        contextRunner
                .withPropertyValues(
                        "alioss.endpoint=oss-cn-beijing.aliyuncs.com",
                        "alioss.access-key-id=test-access-key-id",
                        "alioss.access-key-secret=test-access-key-secret",
                        "alioss.bucket-name=skyhyf")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OSS.class);
                    assertThat(context).hasSingleBean(OssService.class);
                });
    }
}
