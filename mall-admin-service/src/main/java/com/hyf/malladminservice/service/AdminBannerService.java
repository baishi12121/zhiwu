package com.hyf.malladminservice.service;

import com.hyf.malladminservice.dto.request.BannerSaveRequest;
import com.hyf.malladminservice.entity.AdminBanner;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;

public interface AdminBannerService {

    public PageResult<AdminBanner> list(PageQuery query, Integer distributionSite, Integer status);
    public AdminBanner get(Long id);
    public Long create(BannerSaveRequest req);
    public void update(Long id, BannerSaveRequest req);
    public void updateStatus(Long id, Integer status);
    public void delete(Long id);

}
