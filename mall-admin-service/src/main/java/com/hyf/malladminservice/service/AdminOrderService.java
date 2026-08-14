package com.hyf.malladminservice.service;

import com.hyf.malladminservice.dto.request.OrderShipRequest;
import com.hyf.malladminservice.entity.AdminOrder;
import com.hyf.malladminservice.entity.LogisticsCompany;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理后台订单业务接口。
 */
public interface AdminOrderService {

    PageResult<AdminOrder> listOrders(PageQuery query,
                                      Integer orderState,
                                      Integer orderSource,
                                      String keyword,
                                      LocalDate start,
                                      LocalDate end);

    AdminOrder getDetail(Long id);

    AdminOrder ship(Long orderId, OrderShipRequest req);

    List<LogisticsCompany> listLogisticsCompanies();
}
