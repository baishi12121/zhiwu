package com.hyf.malladminservice.service.impl;


import com.hyf.malladminservice.service.AdminAuthService;
import com.hyf.malladminservice.service.AdminBannerService;
import com.hyf.malladminservice.service.AdminProductService;
import com.hyf.malladminservice.service.AdminSalesService;
import com.hyf.malladminservice.service.AdminSeckillService;
import com.hyf.malladminservice.service.AdminUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.malladminservice.dto.request.BannerSaveRequest;
import com.hyf.malladminservice.entity.AdminBanner;
import com.hyf.malladminservice.mapper.AdminBannerMapper;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminBannerServiceImpl implements AdminBannerService {

    private final AdminBannerMapper bannerMapper;

    public PageResult<AdminBanner> list(PageQuery query, Integer distributionSite, Integer status) {
        LambdaQueryWrapper<AdminBanner> wrapper = new LambdaQueryWrapper<>();
        if (distributionSite != null) {
            wrapper.eq(AdminBanner::getDistributionSite, distributionSite);
        }
        if (status != null) {
            wrapper.eq(AdminBanner::getStatus, status);
        }
        wrapper.orderByAsc(AdminBanner::getDistributionSite)
                .orderByAsc(AdminBanner::getSortOrder)
                .orderByDesc(AdminBanner::getId);

        IPage<AdminBanner> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<AdminBanner> result = bannerMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPage(), query.getPageSize());
    }

    public AdminBanner get(Long id) {
        AdminBanner banner = bannerMapper.selectById(id);
        if (banner == null) {
            throw new BizException(ResultCode.NOT_FOUND, "banner不存在");
        }
        return banner;
    }

    @Transactional
    public Long create(BannerSaveRequest req) {
        AdminBanner banner = new AdminBanner();
        BeanUtils.copyProperties(req, banner);
        applyDefaults(banner);
        bannerMapper.insert(banner);
        return banner.getId();
    }

    @Transactional
    public void update(Long id, BannerSaveRequest req) {
        AdminBanner banner = get(id);
        BeanUtils.copyProperties(req, banner, "id", "createTime", "updateTime");
        applyDefaults(banner);
        bannerMapper.updateById(banner);
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminBanner banner = get(id);
        banner.setStatus(status);
        bannerMapper.updateById(banner);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        bannerMapper.deleteById(id);
    }

    private void applyDefaults(AdminBanner banner) {
        if (banner.getType() == null) {
            banner.setType(1);
        }
        if (banner.getDistributionSite() == null) {
            banner.setDistributionSite(1);
        }
        if (banner.getSortOrder() == null) {
            banner.setSortOrder(0);
        }
        if (banner.getStatus() == null) {
            banner.setStatus(1);
        }
    }
}
