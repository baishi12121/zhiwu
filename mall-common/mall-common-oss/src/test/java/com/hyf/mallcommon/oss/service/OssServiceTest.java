package com.hyf.mallcommon.oss.service;

import com.hyf.mallcommon.oss.properties.AliOssProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OssServiceTest {

    @TempDir
    Path uploadDir;

    @Test
    void uploadsToLocalStorageWhenOssClientIsMissing() throws Exception {
        AliOssProperties properties = new AliOssProperties();
        properties.setLocalDir(uploadDir.toString());
        properties.setLocalUrlPrefix("/uploads");
        OssService service = new OssService(null, properties);

        String url = service.upload(new MockMultipartFile(
                "file",
                "plant.jpg",
                "image/jpeg",
                "image-bytes".getBytes()));

        assertThat(url).startsWith("/uploads/");
        String objectKey = url.substring("/uploads/".length());
        assertThat(Files.readString(uploadDir.resolve(objectKey))).isEqualTo("image-bytes");
    }
}
