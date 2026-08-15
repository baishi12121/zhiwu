package com.hyf.mallsearchservice.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.hyf.mallsearchservice.dataobject.ProductImageSyncDO;
import com.hyf.mallsearchservice.dataobject.ProductSyncDO;
import com.hyf.mallsearchservice.document.ProductDoc;
import com.hyf.mallsearchservice.mapper.SyncMapper;
import com.hyf.mallsearchservice.service.IndexService;
import com.hyf.mallsearchservice.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MySQL to Elasticsearch product sync implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private static final int BATCH_SIZE = 500;

    private final SyncMapper syncMapper;
    private final ElasticsearchClient esClient;
    private final IndexService indexService;

    @Override
    public long fullSync() {
        log.info("[product-index] start full sync");
        indexService.recreateIndex();

        long total = syncMapper.count();
        log.info("[product-index] MySQL product count: {}", total);
        if (total == 0) {
            return 0;
        }

        long synced = 0;
        long cursor = 0;
        while (true) {
            List<ProductSyncDO> batch = syncMapper.selectPage(cursor, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            List<Long> ids = batch.stream().map(ProductSyncDO::getId).toList();
            Map<Long, List<String>> imageMap = groupMainImages(syncMapper.selectMainImages(ids));

            BulkRequest.Builder bulk = new BulkRequest.Builder();
            int batchSynced = 0;
            for (ProductSyncDO p : batch) {
                if (!isOnSale(p)) {
                    continue;
                }
                ProductDoc doc = toDoc(p, imageMap.get(p.getId()));
                bulk.operations(o -> o.index(idx -> idx
                        .index(ProductDoc.INDEX_NAME)
                        .id(String.valueOf(doc.getId()))
                        .document(doc)));
                batchSynced++;
            }
            if (batchSynced > 0) {
                bulkIndex(bulk.build());
                synced += batchSynced;
            }

            cursor = batch.get(batch.size() - 1).getId();
            log.info("[product-index] full sync progress: indexed={}, scanned={}/{}", synced, cursor, total);

            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }
        log.info("[product-index] full sync finished, indexed={}", synced);
        return synced;
    }

    @Override
    public void syncProduct(Long productId) {
        if (productId == null) {
            return;
        }
        ProductSyncDO product = syncMapper.selectById(productId);
        if (!isOnSale(product)) {
            deleteProduct(productId);
            return;
        }

        List<ProductImageSyncDO> images = syncMapper.selectMainImages(List.of(productId));
        ProductDoc doc = toDoc(product, groupMainImages(images).get(productId));
        try {
            esClient.index(i -> i
                    .index(ProductDoc.INDEX_NAME)
                    .id(String.valueOf(productId))
                    .document(doc));
            log.info("[product-index] synced product {}", productId);
        } catch (Exception e) {
            log.error("[product-index] sync product {} failed: {}", productId, e.getMessage(), e);
            throw new IllegalStateException("sync product index failed: " + productId, e);
        }
    }

    @Override
    public void deleteProduct(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            if (!indexService.exists()) {
                return;
            }
            esClient.delete(d -> d
                    .index(ProductDoc.INDEX_NAME)
                    .id(String.valueOf(productId)));
            log.info("[product-index] deleted product {}", productId);
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            if (e.status() == 404) {
                log.info("[product-index] product {} is already absent", productId);
                return;
            }
            log.error("[product-index] delete product {} failed: {}", productId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("[product-index] delete product {} failed: {}", productId, e.getMessage(), e);
            throw new IllegalStateException("delete product index failed: " + productId, e);
        }
    }

    @Override
    public long deltaSync(LocalDateTime lastSync) {
        log.warn("[product-index] delta sync is not implemented, lastSync={}", lastSync);
        return 0;
    }

    private void bulkIndex(BulkRequest request) {
        try {
            BulkResponse resp = esClient.bulk(request);
            if (resp.errors()) {
                long errors = resp.items().stream()
                        .filter(it -> it.error() != null)
                        .count();
                log.warn("[product-index] bulk partial failure, errors={}", errors);
            }
        } catch (Exception e) {
            log.error("[product-index] bulk index failed: {}", e.getMessage(), e);
            throw new IllegalStateException("bulk product index failed", e);
        }
    }

    private boolean isOnSale(ProductSyncDO product) {
        return product != null && product.getStatus() != null && product.getStatus() == 1;
    }

    private Map<Long, List<String>> groupMainImages(List<ProductImageSyncDO> images) {
        return images.stream().collect(Collectors.groupingBy(
                ProductImageSyncDO::getProductId,
                Collectors.mapping(ProductImageSyncDO::getImageUrl, Collectors.toList())
        ));
    }

    private ProductDoc toDoc(ProductSyncDO p, List<String> images) {
        ProductDoc d = new ProductDoc();
        d.setId(p.getId());
        d.setSpuCode(p.getSpuCode());
        d.setName(p.getName());
        d.setSubtitle(p.getSubtitle());
        d.setDescription(p.getDescription());
        d.setCategoryId(p.getCategoryId());
        d.setCategoryName(p.getCategoryName());
        d.setBrandId(p.getBrandId());
        d.setBrandName(p.getBrandName());
        d.setPrice(toDouble(p.getPrice()));
        d.setOldPrice(toDouble(p.getOldPrice()));
        d.setDiscount(toDouble(p.getDiscount()));
        d.setInventory(p.getInventory());
        d.setSalesCount(p.getSalesCount());
        d.setCommentCount(p.getCommentCount());
        d.setCollectCount(p.getCollectCount());
        d.setStatus(p.getStatus());
        d.setIsPreSale(p.getIsPreSale());
        if (images != null && !images.isEmpty()) {
            d.setMainImage(images.get(0));
            d.setImages(images);
        }
        d.setCreateTime(formatTime(p.getCreateTime()));
        d.setUpdateTime(formatTime(p.getUpdateTime()));
        d.setSuggestion(buildSuggestion(p));
        return d;
    }

    private List<String> buildSuggestion(ProductSyncDO p) {
        List<String> s = new ArrayList<>();
        if (p.getName() != null) {
            s.add(p.getName());
        }
        if (p.getBrandName() != null) {
            s.add(p.getBrandName());
        }
        if (p.getCategoryName() != null) {
            s.add(p.getCategoryName());
        }
        return s;
    }

    private Double toDouble(BigDecimal d) {
        return d == null ? null : d.doubleValue();
    }

    private String formatTime(LocalDateTime t) {
        return t == null ? null : t.toString();
    }
}
