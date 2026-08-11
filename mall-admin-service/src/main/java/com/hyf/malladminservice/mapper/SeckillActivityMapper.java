package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hyf.malladminservice.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 秒杀活动主表 Mapper。
 *
 * @author hyf
 */
@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    @Select("""
            SELECT sa.*,
                   COALESCE(COUNT(si.id), 0) AS item_count
            FROM seckill_activity sa
            LEFT JOIN seckill_item si ON si.activity_id = sa.id
            WHERE (#{enabled} IS NULL OR sa.enabled = #{enabled})
            GROUP BY sa.id, sa.name, sa.start_time, sa.end_time, sa.enabled, sa.remark, sa.create_time, sa.update_time
            ORDER BY sa.start_time DESC
            """)
    IPage<SeckillActivity> selectPageWithItemCount(IPage<SeckillActivity> page, @Param("enabled") Integer enabled);
}
