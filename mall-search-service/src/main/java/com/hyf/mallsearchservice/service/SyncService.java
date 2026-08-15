package com.hyf.mallsearchservice.service;

import java.time.LocalDateTime;

/**
 * MySQL to Elasticsearch product sync service.
 */
public interface SyncService {

    /**
     * Recreate product index and bulk sync all products.
     *
     * @return synced product count
     */
    long fullSync();

    /**
     * Sync one product document from MySQL to ES.
     *
     * @param productId product id
     */
    void syncProduct(Long productId);

    /**
     * Remove one product document from ES.
     *
     * @param productId product id
     */
    void deleteProduct(Long productId);

    /**
     * Reserved delta sync entry.
     *
     * @param lastSync last sync time
     * @return synced product count
     */
    long deltaSync(LocalDateTime lastSync);
}
