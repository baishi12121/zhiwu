package com.hyf.mallproductservice.domain.repository;

import com.hyf.mallproductservice.infrastructure.persistence.dataobject.CategoryDO;

import java.util.List;

/**
 * 分类仓储接口.
 *
 * @author hyf
 */
public interface CategoryRepository {

    /** 查询所有上线分类（按排序） */
    List<CategoryDO> findAllActive();

    /** 查询顶级分类（parentId=0） */
    List<CategoryDO> findTopCategories();

    /** 根据 parentId 查询子分类 */
    List<CategoryDO> findChildren(Long parentId);
}
