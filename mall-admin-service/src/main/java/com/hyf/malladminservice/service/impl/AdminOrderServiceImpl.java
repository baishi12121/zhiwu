package com.hyf.malladminservice.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.malladminservice.dto.request.OrderShipRequest;
import com.hyf.malladminservice.entity.AdminOrder;
import com.hyf.malladminservice.entity.LogisticsCompany;
import com.hyf.malladminservice.entity.OrderLogistics;
import com.hyf.malladminservice.entity.OrderLogisticsTrack;
import com.hyf.malladminservice.entity.OrderStatusLog;
import com.hyf.malladminservice.mapper.AdminOrderItemMapper;
import com.hyf.malladminservice.mapper.AdminOrderMapper;
import com.hyf.malladminservice.mapper.LogisticsCompanyMapper;
import com.hyf.malladminservice.mapper.OrderLogisticsMapper;
import com.hyf.malladminservice.mapper.OrderLogisticsTrackMapper;
import com.hyf.malladminservice.mapper.OrderStatusLogMapper;
import com.hyf.malladminservice.service.AdminOrderService;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台订单业务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final AdminOrderMapper orderMapper;
    private final AdminOrderItemMapper itemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final OrderLogisticsMapper logisticsMapper;
    private final OrderLogisticsTrackMapper trackMapper;
    private final LogisticsCompanyMapper companyMapper;

    @Override
    public PageResult<AdminOrder> listOrders(PageQuery query,
                                             Integer orderState,
                                             Integer orderSource,
                                             String keyword,
                                             LocalDate start,
                                             LocalDate end) {
        IPage<AdminOrder> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<AdminOrder> result = orderMapper.selectAdminPage(
                page,
                orderState,
                orderSource,
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                start,
                end
        );
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPage(), query.getPageSize());
    }

    @Override
    public AdminOrder getDetail(Long id) {
        AdminOrder order = orderMapper.selectDetailById(id);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        order.setItems(itemMapper.listByOrderId(id));
        order.setStatusLogs(statusLogMapper.listByOrderId(id));
        OrderLogistics logistics = logisticsMapper.selectByOrderId(id);
        if (logistics != null) {
            logistics.setTrack(trackMapper.listByLogisticsId(logistics.getId()));
        }
        order.setLogistics(logistics);
        return order;
    }

    @Override
    @Transactional
    public AdminOrder ship(Long orderId, OrderShipRequest req) {
        LogisticsCompany company = companyMapper.selectById(req.getCompanyId());
        if (company == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物流公司不存在");
        }

        int affected = orderMapper.shipOrder(orderId);
        if (affected == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "订单非待发货状态，无法发货");
        }

        OrderLogistics logistics = new OrderLogistics();
        logistics.setOrderId(orderId);
        logistics.setCompanyId(req.getCompanyId());
        logistics.setLogisticsNo(req.getLogisticsNo().trim());
        logisticsMapper.upsert(logistics);

        OrderLogisticsTrack track = new OrderLogisticsTrack();
        track.setOrderLogisticsId(logistics.getId());
        track.setContent("您的包裹已由" + company.getName() + "揽收，运单号" + logistics.getLogisticsNo());
        track.setOccurTime(LocalDateTime.now());
        track.setSortOrder(1);
        trackMapper.insert(track);

        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setFromState(2);
        statusLog.setToState(3);
        statusLog.setOperator("ADMIN");
        statusLog.setRemark("管理员发货");
        statusLogMapper.insert(statusLog);

        log.info("[admin-order] 订单发货: orderId={}, company={}, logisticsNo={}",
                orderId, company.getCode(), logistics.getLogisticsNo());
        return getDetail(orderId);
    }

    @Override
    public List<LogisticsCompany> listLogisticsCompanies() {
        return companyMapper.listAll();
    }
}
