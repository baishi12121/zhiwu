package com.hyf.malladminservice.controller;

import com.hyf.malladminservice.dto.request.BannerSaveRequest;
import com.hyf.malladminservice.dto.request.StatusUpdateRequest;
import com.hyf.malladminservice.entity.AdminBanner;
import com.hyf.malladminservice.service.AdminBannerService;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final AdminBannerService bannerService;

    @GetMapping
    public Result<PageResult<AdminBanner>> list(
            @RequestParam(required = false) Integer distributionSite,
            @RequestParam(required = false) Integer status,
            PageQuery pageQuery) {
        return Result.success(bannerService.list(pageQuery, distributionSite, status));
    }

    @GetMapping("/{id}")
    public Result<AdminBanner> get(@PathVariable Long id) {
        return Result.success(bannerService.get(id));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody BannerSaveRequest req) {
        return Result.success(bannerService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BannerSaveRequest req) {
        bannerService.update(id, req);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req) {
        bannerService.updateStatus(id, req.getStatus());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return Result.success();
    }
}
