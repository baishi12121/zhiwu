package com.hyf.mallproductservice.repository;

import com.hyf.mallproductservice.dataobject.BannerDO;

import java.util.List;

/**
 * 轮播仓储接口.
 *
 * @author hyf
 */
public interface BannerRepository {

    /** 根据投放位置查询启用的轮播（按排序） */
    List<BannerDO> findByDistributionSite(Integer distributionSite);
}
