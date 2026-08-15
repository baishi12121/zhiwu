package com.hyf.mallsearchservice.service;

/**
 * ES 索引管理接口.
 *
 * <p>职责:索引的存在性检查 / 创建 / 重建。索引 mapping 由
 * {@code resources/es/product-index.json} 定义,本接口只负责加载并落库。
 *
 * @author hyf
 */
public interface IndexService {

    /**
     * 索引不存在则按 mapping 创建,已存在则跳过.
     *
     * @return true=本次新建; false=已存在跳过
     */
    boolean createIndexIfAbsent();

    /**
     * 删除并重建索引(全量同步前调用,保证 mapping 最新).
     *
     * @return true=重建成功
     */
    boolean recreateIndex();

    /**
     * 索引是否存在.
     *
     * @return true=存在
     */
    boolean exists();
}
