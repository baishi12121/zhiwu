package com.hyf.mallproductservice.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hyf.mallproductservice.repository.BannerRepository;
import com.hyf.mallproductservice.dataobject.BannerDO;
import com.hyf.mallproductservice.mapper.BannerMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class BannerRepositoryImpl implements BannerRepository {

    private final BannerMapper bannerMapper;

    public BannerRepositoryImpl(BannerMapper bannerMapper) {
        this.bannerMapper = bannerMapper;
    }

    @Override
    public List<BannerDO> findByDistributionSite(Integer distributionSite) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<BannerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BannerDO::getDistributionSite, distributionSite)
                .eq(BannerDO::getStatus, 1)
                .and(w -> w.isNull(BannerDO::getStartTime).or().le(BannerDO::getStartTime, now))
                .and(w -> w.isNull(BannerDO::getEndTime).or().ge(BannerDO::getEndTime, now))
                .orderByAsc(BannerDO::getSortOrder);
        return bannerMapper.selectList(wrapper);
    }
}
