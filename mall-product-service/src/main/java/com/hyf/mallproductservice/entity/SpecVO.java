package com.hyf.mallproductservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 规格组 VO — 包含规格值和每个值对应的 SKU 可售状态.
 *
 * @author hyf
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecVO {

    private String name;
    private List<SpecValueVO> values;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecValueVO {
        private String name;
        private Boolean available;
        private String desc;
        private String picture;
    }
}
