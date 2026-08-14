package com.hyf.malladminservice.service.impl;

import com.hyf.malladminservice.dto.request.OrderShipRequest;
import com.hyf.malladminservice.entity.LogisticsCompany;
import com.hyf.malladminservice.entity.OrderLogisticsTrack;
import com.hyf.malladminservice.entity.OrderStatusLog;
import com.hyf.malladminservice.mapper.AdminOrderMapper;
import com.hyf.malladminservice.mapper.LogisticsCompanyMapper;
import com.hyf.malladminservice.mapper.OrderLogisticsMapper;
import com.hyf.malladminservice.mapper.OrderLogisticsTrackMapper;
import com.hyf.malladminservice.mapper.OrderStatusLogMapper;
import com.hyf.mallcommon.core.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderServiceImplTest {

    @Test
    void shipRejectsOrderThatIsNotWaitingForShipmentWithoutWritingLogistics() {
        AdminOrderMapper orderMapper = mock(AdminOrderMapper.class);
        OrderLogisticsMapper logisticsMapper = mock(OrderLogisticsMapper.class);
        OrderLogisticsTrackMapper trackMapper = mock(OrderLogisticsTrackMapper.class);
        OrderStatusLogMapper statusLogMapper = mock(OrderStatusLogMapper.class);
        LogisticsCompanyMapper companyMapper = mock(LogisticsCompanyMapper.class);

        LogisticsCompany company = new LogisticsCompany();
        company.setId(1L);
        company.setName("顺丰速运");
        when(companyMapper.selectById(1L)).thenReturn(company);
        when(orderMapper.shipOrder(99L)).thenReturn(0);

        AdminOrderServiceImpl service = new AdminOrderServiceImpl(
                orderMapper,
                null,
                statusLogMapper,
                logisticsMapper,
                trackMapper,
                companyMapper
        );
        OrderShipRequest request = new OrderShipRequest();
        request.setCompanyId(1L);
        request.setLogisticsNo("SF1234567890");

        assertThatThrownBy(() -> service.ship(99L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订单非待发货状态");

        verify(logisticsMapper, never()).upsert(any());
        verify(trackMapper, never()).insert(any(OrderLogisticsTrack.class));
        verify(statusLogMapper, never()).insert(any(OrderStatusLog.class));
    }
}
