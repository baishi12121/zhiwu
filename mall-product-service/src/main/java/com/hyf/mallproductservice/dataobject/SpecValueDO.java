package com.hyf.mallproductservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 规格值 DO — 对应 {@code spec_value} 表.
 *
 * @author hyf
 */
@Data
@TableName("spec_value")
public class SpecValueDO {

    private Long id;
    private Long specId;
    /** 规格值名（如"瓷白色"） */
    private String name;
    /** 该规格值的图片（可选） */
    private String picture;
    private Integer sortOrder;
}
