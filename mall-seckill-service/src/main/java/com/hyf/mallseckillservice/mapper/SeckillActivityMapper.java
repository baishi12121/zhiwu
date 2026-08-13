package com.hyf.mallseckillservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallseckillservice.entity.SeckillActivityDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 秒杀活动 Mapper。
 *
 * <p>提供活动基础查询和活跃活动扫描，供入口校验、启动预热和定时刷新使用。</p>
 */
@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivityDO> {

    List<SeckillActivityDO> selectActiveActivities();
}
