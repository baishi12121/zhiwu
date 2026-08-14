package com.hyf.malladminservice.controller;

import com.hyf.malladminservice.dto.request.OrderShipRequest;
import com.hyf.malladminservice.entity.AdminOrder;
import com.hyf.malladminservice.entity.LogisticsCompany;
import com.hyf.malladminservice.service.AdminOrderService;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理后台 - 订单管理 Controller。
 */
@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping
    public Result<PageResult<AdminOrder>> list(
            @RequestParam(required = false) Integer orderState,
            @RequestParam(required = false) Integer orderSource,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            PageQuery pageQuery) {
        return Result.success(adminOrderService.listOrders(pageQuery, orderState, orderSource, keyword, start, end));
    }

    @GetMapping("/{id}")
    public Result<AdminOrder> detail(@PathVariable Long id) {
        return Result.success(adminOrderService.getDetail(id));
    }

    @PutMapping("/{id}/ship")
    public Result<AdminOrder> ship(@PathVariable Long id, @Valid @RequestBody OrderShipRequest req) {
        return Result.success(adminOrderService.ship(id, req));
    }

    @GetMapping("/logistics/companies")
    public Result<List<LogisticsCompany>> logisticsCompanies() {
        return Result.success(adminOrderService.listLogisticsCompanies());
    }
}
