package com.hyf.mallproductservice.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hyf.mallproductservice.repository.HomeRepository;
import com.hyf.mallproductservice.dataobject.HomeHotDO;
import com.hyf.mallproductservice.dataobject.HotActivityDO;
import com.hyf.mallproductservice.dataobject.HotSubtypeDO;
import com.hyf.mallproductservice.dataobject.HotSubtypeProductDO;
import com.hyf.mallproductservice.mapper.HomeHotMapper;
import com.hyf.mallproductservice.mapper.HotActivityMapper;
import com.hyf.mallproductservice.mapper.HotSubtypeMapper;
import com.hyf.mallproductservice.mapper.HotSubtypeProductMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HomeRepositoryImpl implements HomeRepository {

    private final HomeHotMapper homeHotMapper;
    private final HotActivityMapper hotActivityMapper;
    private final HotSubtypeMapper hotSubtypeMapper;
    private final HotSubtypeProductMapper hotSubtypeProductMapper;

    public HomeRepositoryImpl(HomeHotMapper homeHotMapper,
                              HotActivityMapper hotActivityMapper,
                              HotSubtypeMapper hotSubtypeMapper,
                              HotSubtypeProductMapper hotSubtypeProductMapper) {
        this.homeHotMapper = homeHotMapper;
        this.hotActivityMapper = hotActivityMapper;
        this.hotSubtypeMapper = hotSubtypeMapper;
        this.hotSubtypeProductMapper = hotSubtypeProductMapper;
    }

    @Override
    public List<HomeHotDO> findActiveHotCards() {
        LambdaQueryWrapper<HomeHotDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HomeHotDO::getStatus, 1)
                .orderByAsc(HomeHotDO::getSortOrder);
        return homeHotMapper.selectList(wrapper);
    }

    @Override
    public HotActivityDO findActivityByKey(String activityKey) {
        LambdaQueryWrapper<HotActivityDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotActivityDO::getActivityKey, activityKey)
                .eq(HotActivityDO::getStatus, 1);
        return hotActivityMapper.selectOne(wrapper);
    }

    @Override
    public List<HotSubtypeDO> findSubtypesByActivityId(Long activityId) {
        LambdaQueryWrapper<HotSubtypeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotSubtypeDO::getActivityId, activityId)
                .orderByAsc(HotSubtypeDO::getSortOrder);
        return hotSubtypeMapper.selectList(wrapper);
    }

    @Override
    public List<HotSubtypeProductDO> findProductsBySubtypeId(Long subtypeId) {
        LambdaQueryWrapper<HotSubtypeProductDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotSubtypeProductDO::getSubtypeId, subtypeId)
                .orderByAsc(HotSubtypeProductDO::getSortOrder);
        return hotSubtypeProductMapper.selectList(wrapper);
    }
}
