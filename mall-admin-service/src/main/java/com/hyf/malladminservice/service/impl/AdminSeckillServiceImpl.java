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
import com.hyf.malladminservice.dto.request.SeckillActivitySaveRequest;
import com.hyf.malladminservice.dto.request.SeckillItemSaveRequest;
import com.hyf.malladminservice.entity.AdminProductSku;
import com.hyf.malladminservice.entity.SeckillActivity;
import com.hyf.malladminservice.entity.SeckillItem;
import com.hyf.malladminservice.mapper.AdminProductSkuMapper;
import com.hyf.malladminservice.mapper.SeckillActivityMapper;
import com.hyf.malladminservice.mapper.SeckillItemMapper;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 秒杀专区业务逻辑。
 *
 * <p>职责：
 * <ul>
 *   <li>秒杀活动 CRUD + 启停；</li>
 *   <li>把商品 SKU 加入秒杀活动（{@code seckill_item}，秒杀价 / 秒杀库存 / 限购数量 / 排序）；</li>
 *   <li>秒杀商品项的修改 / 上下架 / 删除 / 秒杀库存调整。</li>
 * </ul>
 *
 * <p>秒杀库存是独立于 SKU 原库存的配额，由运营从总库存中切出一部分，下单时由秒杀逻辑原子扣减；
 * 本服务只负责运营配置，不涉及下单扣减逻辑。
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSeckillServiceImpl implements AdminSeckillService {

    private final SeckillActivityMapper activityMapper;
    private final SeckillItemMapper itemMapper;
    private final AdminProductSkuMapper skuMapper;

    // ==================== 活动 ====================

    /**
     * 活动分页查询，可按启停状态 / 时间范围筛选。
     */
    public PageResult<SeckillActivity> listActivities(PageQuery query, Integer enabled) {
        IPage<SeckillActivity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<SeckillActivity> result = activityMapper.selectPageWithItemCount(page, enabled);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 活动详情（含商品项列表）。
     *
     * @param id 活动 ID
     * @return 活动实体
     * @throws BizException 活动不存在
     */
    public SeckillActivity getActivity(Long id) {
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        return activity;
    }

    /**
     * 新建活动。
     */
    @Transactional
    public Long createActivity(SeckillActivitySaveRequest req) {
        validateTimeRange(req);
        SeckillActivity activity = new SeckillActivity();
        BeanUtils.copyProperties(req, activity);
        if (activity.getEnabled() == null) {
            activity.setEnabled(1);
        }
        activityMapper.insert(activity);
        log.info("[admin-seckill] 新建活动: id={}, name={}", activity.getId(), activity.getName());
        return activity.getId();
    }

    /**
     * 修改活动。
     */
    @Transactional
    public void updateActivity(Long id, SeckillActivitySaveRequest req) {
        SeckillActivity exist = activityMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        validateTimeRange(req);
        BeanUtils.copyProperties(req, exist);
        activityMapper.updateById(exist);
        log.info("[admin-seckill] 修改活动: id={}", id);
    }

    /**
     * 活动启停。
     */
    @Transactional
    public void updateActivityEnabled(Long id, Integer enabled) {
        SeckillActivity exist = activityMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        exist.setEnabled(enabled);
        activityMapper.updateById(exist);
    }

    /**
     * 删除活动 + 级联商品项。
     */
    @Transactional
    public void deleteActivity(Long id) {
        SeckillActivity exist = activityMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        activityMapper.deleteById(id);
        itemMapper.delete(new LambdaQueryWrapper<SeckillItem>().eq(SeckillItem::getActivityId, id));
        log.info("[admin-seckill] 删除活动: id={}, 清理商品项", id);
    }

    // ==================== 秒杀商品项 ====================

    /**
     * 列出活动下的所有秒杀商品项（含商品名 / SKU 编码 / 原价）。
     */
    public List<SeckillItem> listItems(Long activityId) {
        return itemMapper.listByActivity(activityId);
    }

    /**
     * 把一个 SKU 加入秒杀活动。
     *
     * <p>约束：
     * <ul>
     *   <li>SKU 必须存在；</li>
     *   <li>同一活动同一 SKU 不可重复加入（uk_activity_sku 兜底）；</li>
     *   <li>秒杀库存不能超过 SKU 当前总库存（避免卖超）。</li>
     * </ul>
     */
    @Transactional
    public Long addItem(Long activityId, SeckillItemSaveRequest req) {
        // 1. 校验活动存在
        if (activityMapper.selectById(activityId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "秒杀活动不存在");
        }
        // 2. 校验 SKU 存在
        AdminProductSku sku = skuMapper.selectById(req.getSkuId());
        if (sku == null) {
            throw new BizException(ResultCode.NOT_FOUND, "SKU 不存在");
        }
        if (!sku.getProductId().equals(req.getSpuId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "spuId 与 skuId 不匹配");
        }
        // 3. 校验秒杀库存不超过 SKU 总库存
        if (req.getSeckillStock() != null && sku.getInventory() != null
                && req.getSeckillStock() > sku.getInventory()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "秒杀库存不能超过 SKU 当前库存 " + sku.getInventory());
        }

        SeckillItem item = new SeckillItem();
        BeanUtils.copyProperties(req, item);
        item.setActivityId(activityId);
        if (item.getLimitPerUser() == null) {
            item.setLimitPerUser(1);
        }
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        if (item.getStatus() == null) {
            item.setStatus(1);
        }
        itemMapper.insert(item);
        log.info("[admin-seckill] 加入秒杀: activityId={}, skuId={}, seckillPrice={}, stock={}",
                activityId, req.getSkuId(), req.getSeckillPrice(), req.getSeckillStock());
        return item.getId();
    }

    /**
     * 修改秒杀商品项。
     */
    @Transactional
    public void updateItem(Long itemId, SeckillItemSaveRequest req) {
        SeckillItem exist = itemMapper.selectById(itemId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "秒杀商品项不存在");
        }
        // 库存上限仍以 SKU 当前库存为准
        AdminProductSku sku = skuMapper.selectById(req.getSkuId());
        if (sku == null) {
            throw new BizException(ResultCode.NOT_FOUND, "SKU 不存在");
        }
        if (req.getSeckillStock() != null && sku.getInventory() != null
                && req.getSeckillStock() > sku.getInventory()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "秒杀库存不能超过 SKU 当前库存 " + sku.getInventory());
        }
        BeanUtils.copyProperties(req, exist, "activityId");
        itemMapper.updateById(exist);
        log.info("[admin-seckill] 修改秒杀项: id={}", itemId);
    }

    /**
     * 秒杀商品项上下架。
     */
    @Transactional
    public void updateItemStatus(Long itemId, Integer status) {
        SeckillItem exist = itemMapper.selectById(itemId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "秒杀商品项不存在");
        }
        exist.setStatus(status);
        itemMapper.updateById(exist);
    }

    /**
     * 删除秒杀商品项（移出秒杀专区，不影响商品本身）。
     */
    @Transactional
    public void deleteItem(Long itemId) {
        SeckillItem exist = itemMapper.selectById(itemId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "秒杀商品项不存在");
        }
        itemMapper.deleteById(itemId);
        log.info("[admin-seckill] 移出秒杀: id={}, skuId={}", itemId, exist.getSkuId());
    }

    // ==================== 内部工具 ====================

    /**
     * 校验活动时间范围合法：开始 < 结束。
     */
    private void validateTimeRange(SeckillActivitySaveRequest req) {
        if (req.getStartTime() != null && req.getEndTime() != null
                && !req.getStartTime().isBefore(req.getEndTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "开始时间必须早于结束时间");
        }
    }
}
