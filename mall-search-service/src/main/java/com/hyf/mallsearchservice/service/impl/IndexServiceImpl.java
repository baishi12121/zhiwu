package com.hyf.mallsearchservice.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.hyf.mallsearchservice.document.ProductDoc;
import com.hyf.mallsearchservice.service.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/**
 * ES 索引管理实现.
 *
 * <p>用 {@link ElasticsearchClient}(Spring Boot 3.5 自动配置,基于 elasticsearch-java 8.18.8)
 * 直接操作 Indices API。所有请求均用 lambda builder 风格构造,避免 import 具体
 * 请求/响应类(不同小版本类名有差异,builder 风格最稳定)。
 * mapping/settings 从 classpath {@code es/product-index.json} 加载。
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {

    private static final String MAPPING_LOCATION = "es/product-index.json";

    private final ElasticsearchClient esClient;

    @Override
    public boolean exists() {
        try {
            return esClient.indices()
                    .exists(b -> b.index(ProductDoc.INDEX_NAME))
                    .value();
        } catch (Exception e) {
            // ES 未启动时返回 false,不阻断应用启动(搜索能力降级,业务不受影响)
            log.error("[索引] 检查 {} 存在性失败: {}", ProductDoc.INDEX_NAME, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean createIndexIfAbsent() {
        if (exists()) {
            log.info("[索引] {} 已存在,跳过创建", ProductDoc.INDEX_NAME);
            return false;
        }
        return doCreate();
    }

    @Override
    public boolean recreateIndex() {
        // 先删后建;删除失败(如索引不存在)忽略,继续重建
        if (exists()) {
            try {
                boolean ack = esClient.indices()
                        .delete(b -> b.index(ProductDoc.INDEX_NAME))
                        .acknowledged();
                log.info("[索引] 删除 {} 完成, acknowledged={}", ProductDoc.INDEX_NAME, ack);
            } catch (Exception e) {
                log.warn("[索引] 删除 {} 失败,将继续重建: {}", ProductDoc.INDEX_NAME, e.getMessage());
            }
        }
        return doCreate();
    }

    /**
     * 加载 classpath mapping JSON 并创建索引.
     */
    private boolean doCreate() {
        try {
            String mapping = loadMapping();
            boolean ack = esClient.indices()
                    .create(b -> b.index(ProductDoc.INDEX_NAME).withJson(new StringReader(mapping)))
                    .acknowledged();
            log.info("[索引] 创建 {} 完成, acknowledged={}", ProductDoc.INDEX_NAME, ack);
            return ack;
        } catch (Exception e) {
            log.error("[索引] 创建 {} 失败: {}", ProductDoc.INDEX_NAME, e.getMessage(), e);
            return false;
        }
    }

    private String loadMapping() {
        try (InputStream in = new ClassPathResource(MAPPING_LOCATION).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("加载 mapping 失败: " + MAPPING_LOCATION, e);
        }
    }
}
