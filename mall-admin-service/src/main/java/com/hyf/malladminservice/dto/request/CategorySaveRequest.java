package com.hyf.malladminservice.dto.request;

import lombok.Data;

@Data
public class CategorySaveRequest {

    private Long parentId;
    private String name;
    private String icon;
    private String picture;
    private Integer sortOrder;
    private Integer status;
}
