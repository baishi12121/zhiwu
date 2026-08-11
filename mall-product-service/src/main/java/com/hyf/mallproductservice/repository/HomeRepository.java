package com.hyf.mallproductservice.repository;

import com.hyf.mallproductservice.dataobject.HomeHotDO;
import com.hyf.mallproductservice.dataobject.HotActivityDO;
import com.hyf.mallproductservice.dataobject.HotSubtypeDO;
import com.hyf.mallproductservice.dataobject.HotSubtypeProductDO;

import java.util.List;

/**
 * 首页聚合仓储接口（热门推荐卡、热门活动等）.
 *
 * @author hyf
 */
public interface HomeRepository {

    /** 查询启用的首页热门推荐卡（按排序） */
    List<HomeHotDO> findActiveHotCards();

    /** 根据 activityKey 查询活动 */
    HotActivityDO findActivityByKey(String activityKey);

    /** 根据活动ID查询子类Tab */
    List<HotSubtypeDO> findSubtypesByActivityId(Long activityId);

    /** 根据子类ID查询关联的商品ID列表（按排序） */
    List<HotSubtypeProductDO> findProductsBySubtypeId(Long subtypeId);
}
