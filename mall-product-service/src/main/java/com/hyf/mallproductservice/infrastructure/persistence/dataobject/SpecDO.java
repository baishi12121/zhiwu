package com.hyf.mallproductservice.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 规格组 DO — 对应 {@code spec} 表.
 *
 * @author hyf
 */
@Data
@TableName("spec")
public class SpecDO {

    private Long id;
    private Long productId;
    /** 规格组名（如"颜色"） */
    private String name;
    /** 决定 spec_list 顺序 */
    private Integer sortOrder;
}
