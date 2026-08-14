package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台订单发货请求。
 */
@Data
public class OrderShipRequest {

    @NotNull(message = "物流公司不能为空")
    private Long companyId;

    @NotBlank(message = "运单号不能为空")
    private String logisticsNo;
}
