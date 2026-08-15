package com.hyf.mallsearchservice.runner;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.hyf.mallsearchservice.document.ProductDoc;
import com.hyf.mallsearchservice.service.IndexService;
import com.hyf.mallsearchservice.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures the product search index exists and bootstraps data for a fresh ES volume.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100)
public class IndexInitializer implements ApplicationRunner {

    private final IndexService indexService;
    private final SyncService syncService;
    private final ElasticsearchClient esClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[search-init] checking product index");
        indexService.createIndexIfAbsent();
        syncIfIndexEmpty();
    }

    private void syncIfIndexEmpty() {
        try {
            long count = esClient.count(c -> c.index(ProductDoc.INDEX_NAME)).count();
            if (count > 0) {
                log.info("[search-init] product index has {} docs, skip auto sync", count);
                return;
            }

            log.info("[search-init] product index is empty, start full sync from MySQL");
            long synced = syncService.fullSync();
            log.info("[search-init] full sync finished, synced={}", synced);
        } catch (Exception e) {
            log.warn("[search-init] skip auto sync because ES is not ready: {}", e.getMessage());
        }
    }
}
