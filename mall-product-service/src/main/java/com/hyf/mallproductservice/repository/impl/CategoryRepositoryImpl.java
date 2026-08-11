package com.hyf.mallproductservice.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hyf.mallproductservice.repository.CategoryRepository;
import com.hyf.mallproductservice.dataobject.CategoryDO;
import com.hyf.mallproductservice.mapper.CategoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryMapper categoryMapper;

    public CategoryRepositoryImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<CategoryDO> findAllActive() {
        LambdaQueryWrapper<CategoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryDO::getStatus, 1)
                .orderByAsc(CategoryDO::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    public List<CategoryDO> findTopCategories() {
        LambdaQueryWrapper<CategoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryDO::getParentId, 0L)
                .eq(CategoryDO::getStatus, 1)
                .orderByAsc(CategoryDO::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    public List<CategoryDO> findChildren(Long parentId) {
        LambdaQueryWrapper<CategoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryDO::getParentId, parentId)
                .eq(CategoryDO::getStatus, 1)
                .orderByAsc(CategoryDO::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }
}
