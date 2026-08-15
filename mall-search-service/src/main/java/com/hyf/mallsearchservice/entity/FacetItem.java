package com.hyf.mallsearchservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聚合项(品牌/分类侧边栏候选项).
 *
 * @param id   品牌/分类 ID
 * @param name 名称(冗余,免前端回查)
 * @param count 该维度下的命中数
 * @author hyf
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacetItem {

    private Long id;
    private String name;
    private Long count;
}
