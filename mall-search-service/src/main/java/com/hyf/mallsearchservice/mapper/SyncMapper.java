package com.hyf.mallsearchservice.mapper;

import com.hyf.mallsearchservice.dataobject.ProductImageSyncDO;
import com.hyf.mallsearchservice.dataobject.ProductSyncDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * Product data mapper used by MySQL to Elasticsearch sync.
 */
@Mapper
public interface SyncMapper {

    @Select("SELECT COUNT(*) FROM product")
    long count();

    @Select(PRODUCT_SELECT +
            "WHERE p.id = #{id}")
    ProductSyncDO selectById(@Param("id") Long id);

    @Select(PRODUCT_SELECT +
            "WHERE p.id > #{cursor} " +
            "ORDER BY p.id ASC " +
            "LIMIT #{size}")
    List<ProductSyncDO> selectPage(@Param("cursor") long cursor, @Param("size") int size);

    @Select("<script>" +
            "SELECT product_id, image_url, sort_order FROM product_image " +
            "WHERE image_type = 1 AND product_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "ORDER BY product_id, sort_order" +
            "</script>")
    List<ProductImageSyncDO> selectMainImages(@Param("ids") Collection<Long> ids);

    String PRODUCT_SELECT = "SELECT p.id, p.category_id, p.brand_id, p.spu_code, p.name, p.subtitle, p.description, " +
            "       p.price, p.old_price, p.discount, p.inventory, p.sales_count, p.comment_count, p.collect_count, " +
            "       p.is_pre_sale, p.status, p.create_time, p.update_time, " +
            "       b.name AS brand_name, c.name AS category_name " +
            "FROM product p " +
            "LEFT JOIN brand b ON p.brand_id = b.id " +
            "LEFT JOIN category c ON p.category_id = c.id ";
}
