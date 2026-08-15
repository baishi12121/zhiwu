package com.hyf.mallsearchservice.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.List;

/**
 * 商品 ES 文档(SPU 维度),对应索引 {@value #INDEX_NAME}.
 *
 * <p>说明:
 * <ul>
 *   <li>本类是普通 POJO,由 {@code ElasticsearchClient} + Jackson 序列化/反序列化;
 *       不依赖 Spring Data Repository,因此 <b>不加 {@code @Field} 注解</b>
 *       (mapping 由 {@code resources/es/product-index.json} 真实定义,避免双份维护)。</li>
 *   <li>字段名采用 camelCase,与 ES mapping 字段名保持一致,Jackson 默认即可匹配。</li>
 *   <li>{@code createTime}/{@code updateTime} 用 {@code String}(ISO-8601),
 *       避免 elasticsearch-java 自带 ObjectMapper 未注册 JavaTimeModule 导致序列化失败。</li>
 *   <li>{@code suggestion} 用 {@code List<String>},mapping 声明为 {@code completion} 类型,
 *       ES 会把字符串数组当作 completion input 接收。</li>
 * </ul>
 *
 * @author hyf
 */
@Data
@Document(indexName = ProductDoc.INDEX_NAME)
public class ProductDoc {

    /** 索引名常量,供 IndexService / SyncService 复用,避免散落字符串 */
    public static final String INDEX_NAME = "product";

    @Id
    private Long id;

    private String spuCode;
    private String name;
    private String subtitle;
    private String description;

    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;

    private Double price;
    private Double oldPrice;
    private Double discount;

    private Integer inventory;
    private Integer salesCount;
    private Integer commentCount;
    private Integer collectCount;

    /** 0=下架 1=上架 */
    private Integer status;
    /** 0=非预售 1=预售 */
    private Integer isPreSale;

    /** 主图 URL(取 product_image 中 image_type=1 的第一条) */
    private String mainImage;
    /** 主图 URL 列表 */
    private List<String> images;

    /** ISO-8601 字符串,如 "2026-08-15T10:00:00" */
    private String createTime;
    private String updateTime;

    /** 自动补全输入项,来源:name 分词短语 + brandName + categoryName */
    private List<String> suggestion;
}
