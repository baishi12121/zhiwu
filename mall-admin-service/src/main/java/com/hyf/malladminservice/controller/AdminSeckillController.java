package com.hyf.malladminservice.controller;

import com.hyf.malladminservice.dto.request.SeckillActivitySaveRequest;
import com.hyf.malladminservice.dto.request.SeckillItemSaveRequest;
import com.hyf.malladminservice.dto.request.StatusUpdateRequest;
import com.hyf.malladminservice.entity.SeckillActivity;
import com.hyf.malladminservice.entity.SeckillItem;
import com.hyf.malladminservice.service.AdminSeckillService;
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

import java.util.List;

/**
 * 管理后台 - 秒杀专区 Controller。
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET    /admin/seckill/activities          —— 活动分页</li>
 *   <li>GET    /admin/seckill/activities/{id}     —— 活动详情</li>
 *   <li>POST   /admin/seckill/activities          —— 新建活动</li>
 *   <li>PUT    /admin/seckill/activities/{id}     —— 修改活动</li>
 *   <li>PUT    /admin/seckill/activities/{id}/enabled —— 活动启停</li>
 *   <li>DELETE /admin/seckill/activities/{id}     —— 删除活动（级联商品项）</li>
 *   <li>GET    /admin/seckill/activities/{id}/items —— 活动商品列表</li>
 *   <li>POST   /admin/seckill/activities/{id}/items —— SKU 加入秒杀</li>
 *   <li>PUT    /admin/seckill/items/{itemId}      —— 修改秒杀项</li>
 *   <li>PUT    /admin/seckill/items/{itemId}/status —— 秒杀项上下架</li>
 *   <li>DELETE /admin/seckill/items/{itemId}      —— 移出秒杀</li>
 * </ul>
 *
 * @author hyf
 */
@RestController
@RequestMapping("/admin/seckill")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final AdminSeckillService adminSeckillService;

    // ==================== 活动 ====================

    /** 活动分页 */
    @GetMapping("/activities")
    public Result<PageResult<SeckillActivity>> listActivities(
            @RequestParam(required = false) Integer enabled,
            PageQuery pageQuery) {
        return Result.success(adminSeckillService.listActivities(pageQuery, enabled));
    }

    /** 活动详情 */
    @GetMapping("/activities/{id}")
    public Result<SeckillActivity> getActivity(@PathVariable Long id) {
        return Result.success(adminSeckillService.getActivity(id));
    }

    /** 新建活动 */
    @PostMapping("/activities")
    public Result<Long> createActivity(@Valid @RequestBody SeckillActivitySaveRequest req) {
        return Result.success(adminSeckillService.createActivity(req));
    }

    /** 修改活动 */
    @PutMapping("/activities/{id}")
    public Result<Void> updateActivity(@PathVariable Long id, @Valid @RequestBody SeckillActivitySaveRequest req) {
        adminSeckillService.updateActivity(id, req);
        return Result.success();
    }

    /** 活动启停 */
    @PutMapping("/activities/{id}/enabled")
    public Result<Void> updateActivityEnabled(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req) {
        // 复用 StatusUpdateRequest：0 禁用 1 启用（语义对齐 enabled 字段）
        adminSeckillService.updateActivityEnabled(id, req.getStatus());
        return Result.success();
    }

    /** 删除活动 */
    @DeleteMapping("/activities/{id}")
    public Result<Void> deleteActivity(@PathVariable Long id) {
        adminSeckillService.deleteActivity(id);
        return Result.success();
    }

    // ==================== 秒杀商品项 ====================

    /** 活动商品列表 */
    @GetMapping("/activities/{id}/items")
    public Result<List<SeckillItem>> listItems(@PathVariable Long id) {
        return Result.success(adminSeckillService.listItems(id));
    }

    /** SKU 加入秒杀 */
    @PostMapping("/activities/{id}/items")
    public Result<Long> addItem(@PathVariable Long id, @Valid @RequestBody SeckillItemSaveRequest req) {
        return Result.success(adminSeckillService.addItem(id, req));
    }

    /** 修改秒杀项 */
    @PutMapping("/items/{itemId}")
    public Result<Void> updateItem(@PathVariable Long itemId, @Valid @RequestBody SeckillItemSaveRequest req) {
        adminSeckillService.updateItem(itemId, req);
        return Result.success();
    }

    /** 秒杀项上下架 */
    @PutMapping("/items/{itemId}/status")
    public Result<Void> updateItemStatus(@PathVariable Long itemId, @Valid @RequestBody StatusUpdateRequest req) {
        adminSeckillService.updateItemStatus(itemId, req.getStatus());
        return Result.success();
    }

    /** 移出秒杀 */
    @DeleteMapping("/items/{itemId}")
    public Result<Void> deleteItem(@PathVariable Long itemId) {
        adminSeckillService.deleteItem(itemId);
        return Result.success();
    }
}
