package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngCategoryNode;
import com.chengde.smartcity.exchange.mapper.IngCategoryNodeMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestCatalogService {

    private final IngCategoryNodeMapper categoryMapper;

    public IngestCatalogService(IngCategoryNodeMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<IngCategoryNode> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<IngCategoryNode>()
                .orderByAsc(IngCategoryNode::getSortOrder).orderByAsc(IngCategoryNode::getId));
    }

    @Transactional
    public Long createCategory(UserPrincipal operator, Map<String, Object> body) {
        IngCategoryNode node = new IngCategoryNode();
        node.setNodeCode(str(body.get("nodeCode"), "CAT_" + System.currentTimeMillis()));
        node.setNodeName(required(body.get("nodeName"), "nodeName").toString());
        Object parentId = body.get("parentId");
        node.setParentId(parentId == null ? 0L : Long.valueOf(String.valueOf(parentId)));
        node.setSecretLevel(str(body.get("secretLevel"), "INTERNAL"));
        node.setSortOrder(0);
        categoryMapper.insert(node);
        return node.getId();
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }
}
