package com.hyf.mallproductservice.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hyf.mallproductservice.domain.repository.BannerRepository;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.BannerDO;
import com.hyf.mallproductservice.infrastructure.persistence.mapper.BannerMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BannerRepositoryImpl implements BannerRepository {

    private final BannerMapper bannerMapper;

    public BannerRepositoryImpl(BannerMapper bannerMapper) {
        this.bannerMapper = bannerMapper;
    }

    @Override
    public List<BannerDO> findByDistributionSite(Integer distributionSite) {
        LambdaQueryWrapper<BannerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BannerDO::getDistributionSite, distributionSite)
                .eq(BannerDO::getStatus, 1)
                .orderByAsc(BannerDO::getSortOrder);
        return bannerMapper.selectList(wrapper);
    }
}
