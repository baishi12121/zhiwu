package com.hyf.mallproductservice.application.service;

import com.hyf.mallproductservice.domain.repository.CategoryRepository;
import com.hyf.mallproductservice.domain.repository.ProductRepository;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.CategoryDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.ProductDO;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.ProductImageDO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分类应用服务.
 *
 * @author hyf
 */
@Service
public class CategoryApplicationService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryApplicationService(CategoryRepository categoryRepository,
                                       ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    /** 分类树 */
    public List<Map<String, Object>> getTree() {
        List<CategoryDO> all = categoryRepository.findAllActive();
        Map<Long, List<CategoryDO>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(CategoryDO::getParentId));

        return all.stream()
                .filter(c -> c.getParentId() == 0)
                .map(c -> buildTreeNode(c, childrenMap))
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildTreeNode(CategoryDO c, Map<Long, List<CategoryDO>> childrenMap) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", c.getId().toString());
        node.put("name", c.getName());
        node.put("icon", c.getIcon());
        node.put("picture", c.getPicture());
        node.put("sortOrder", c.getSortOrder());

        List<CategoryDO> children = childrenMap.getOrDefault(c.getId(), List.of());
        node.put("children", children.stream()
                .map(ch -> buildTreeNode(ch, childrenMap))
                .collect(Collectors.toList()));
        return node;
    }

    /** 顶级分类（含二级分类和商品） */
    public List<Map<String, Object>> getTopCategories() {
        List<CategoryDO> topCategories = categoryRepository.findTopCategories();
        List<Map<String, Object>> result = new ArrayList<>();

        for (CategoryDO top : topCategories) {
            Map<String, Object> topNode = new LinkedHashMap<>();
            topNode.put("id", top.getId().toString());
            topNode.put("name", top.getName());
            topNode.put("picture", top.getPicture() != null ? top.getPicture() : "");

            // 分类页轮播图（使用商品前3个主图作为 imageBanners）
            List<String> imageBanners = new ArrayList<>();
            List<CategoryDO> children = categoryRepository.findChildren(top.getId());

            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (CategoryDO child : children) {
                Map<String, Object> childNode = new LinkedHashMap<>();
                childNode.put("id", child.getId().toString());
                childNode.put("name", child.getName());
                childNode.put("picture", child.getPicture() != null ? child.getPicture() : "");

                // 该二级分类下的商品（按销量top6）
                List<ProductDO> products = productRepository.findSimilarProducts(child.getId(), 0L, 6);
                List<Map<String, Object>> goodsItems = products.stream().map(p -> {
                    List<ProductImageDO> mainImages = productRepository.findMainImages(p.getId());
                    String picture = mainImages.isEmpty() ? "" : mainImages.get(0).getImageUrl();

                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("id", p.getId().toString());
                    g.put("name", p.getName());
                    g.put("desc", p.getSubtitle() != null ? p.getSubtitle() : "");
                    g.put("price", p.getPrice());
                    g.put("discount", p.getDiscount() != null ? p.getDiscount() : BigDecimal.ONE);
                    g.put("orderNum", p.getSalesCount());
                    g.put("picture", picture);
                    return g;
                }).collect(Collectors.toList());
                childNode.put("goods", goodsItems);

                // 收集前3张商品图片作为 banner
                for (Map<String, Object> g : goodsItems) {
                    if (imageBanners.size() < 3) {
                        imageBanners.add((String) g.get("picture"));
                    }
                }

                childNodes.add(childNode);
            }
            topNode.put("children", childNodes);
            topNode.put("imageBanners", imageBanners);
            result.add(topNode);
        }

        return result;
    }
}
