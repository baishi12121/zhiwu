package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malladminservice.entity.SeckillItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 秒杀活动商品项 Mapper。
 *
 * <p>{@link #listByActivity} 用一次 join 把商品名 / SKU 编码 / 原价一次性带出来，
 * 避免管理后台列表页 N+1 查询。
 *
 * @author hyf
 */
@Mapper
public interface SeckillItemMapper extends BaseMapper<SeckillItem> {

    /**
     * 列出活动下的所有商品项（含商品名 / SKU 编码 / 原价）。
     *
     * @param activityId 活动 ID
     * @return 商品项列表，按 sort_order 升序
     */
    @Select("""
            SELECT si.*,
                   p.name       AS spu_name,
                   ps.sku_code  AS sku_code,
                   ps.price     AS original_price
            FROM seckill_item si
            LEFT JOIN product      p  ON p.id  = si.spu_id
            LEFT JOIN product_sku  ps ON ps.id = si.sku_id
            WHERE si.activity_id = #{activityId}
            ORDER BY si.sort_order ASC, si.id ASC
            """)
    List<SeckillItem> listByActivity(@Param("activityId") Long activityId);
}
