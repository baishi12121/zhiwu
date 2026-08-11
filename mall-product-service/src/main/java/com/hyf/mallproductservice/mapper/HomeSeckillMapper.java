package com.hyf.mallproductservice.mapper;

import com.hyf.mallproductservice.dataobject.HomeSeckillItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HomeSeckillMapper {

    @Select("""
            SELECT si.id,
                   si.activity_id      AS activity_id,
                   sa.name             AS activity_name,
                   sa.start_time       AS start_time,
                   sa.end_time         AS end_time,
                   si.spu_id           AS spu_id,
                   si.sku_id           AS sku_id,
                   p.name              AS spu_name,
                   ps.sku_code         AS sku_code,
                   COALESCE(ps.picture, pi.image_url, '') AS picture,
                   ps.price            AS original_price,
                   si.seckill_price    AS seckill_price,
                   si.seckill_stock    AS seckill_stock,
                   si.limit_per_user   AS limit_per_user
            FROM seckill_activity sa
            JOIN seckill_item si ON si.activity_id = sa.id
            JOIN product p ON p.id = si.spu_id
            JOIN product_sku ps ON ps.id = si.sku_id
            LEFT JOIN product_image pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_image pi2
                  WHERE pi2.product_id = p.id AND pi2.image_type = 1
                  ORDER BY pi2.sort_order ASC, pi2.id ASC
                  LIMIT 1
              )
            WHERE sa.enabled = 1
              AND si.status = 1
              AND si.seckill_stock > 0
              AND p.status = 1
              AND ps.status = 1
              AND sa.end_time >= NOW()
            ORDER BY
              CASE WHEN sa.start_time <= NOW() AND sa.end_time >= NOW() THEN 0 ELSE 1 END,
              sa.start_time ASC,
              si.sort_order ASC,
              si.id ASC
            LIMIT 8
            """)
    List<HomeSeckillItemDO> selectActiveHomeItems();
}
