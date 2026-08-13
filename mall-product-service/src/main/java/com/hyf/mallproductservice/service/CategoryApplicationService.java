package com.hyf.mallproductservice.service;

import java.util.List;
import java.util.Map;

public interface CategoryApplicationService {

    public List<Map<String, Object>> getTree();
    public List<Map<String, Object>> getTopCategories();

}
