package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngBizSystem;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngDict;
import com.chengde.smartcity.exchange.entity.IngDictColumnLink;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.mapper.IngBizSystemMapper;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngDictColumnLinkMapper;
import com.chengde.smartcity.exchange.mapper.IngDictMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictColumnLinkService {

    private final IngDictColumnLinkMapper linkMapper;
    private final IngDictMapper dictMapper;
    private final IngDataColumnMapper columnMapper;
    private final IngDataTableMapper tableMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final IngBizSystemMapper bizSystemMapper;
    private final IngProjectMapper projectMapper;

    public DictColumnLinkService(IngDictColumnLinkMapper linkMapper,
                                 IngDictMapper dictMapper,
                                 IngDataColumnMapper columnMapper,
                                 IngDataTableMapper tableMapper,
                                 IngDataSourceMapper dataSourceMapper,
                                 IngBizSystemMapper bizSystemMapper,
                                 IngProjectMapper projectMapper) {
        this.linkMapper = linkMapper;
        this.dictMapper = dictMapper;
        this.columnMapper = columnMapper;
        this.tableMapper = tableMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.bizSystemMapper = bizSystemMapper;
        this.projectMapper = projectMapper;
    }

    public List<IngDictColumnLink> listByDict(Long dictId) {
        if (dictMapper.selectById(dictId) == null) {
            throw new BusinessException(404, "字典不存在");
        }
        List<IngDictColumnLink> list = linkMapper.selectList(new LambdaQueryWrapper<IngDictColumnLink>()
                .eq(IngDictColumnLink::getDictId, dictId)
                .orderByDesc(IngDictColumnLink::getId));
        for (IngDictColumnLink link : list) {
            enrich(link);
        }
        return list;
    }

    @Transactional
    public Long bind(UserPrincipal operator, Long dictId, Map<String, Object> body) {
        IngDict dict = dictMapper.selectById(dictId);
        if (dict == null) throw new BusinessException(404, "字典不存在");
        Long columnId = Long.valueOf(String.valueOf(required(body.get("columnId"), "columnId")));
        IngDataColumn col = columnMapper.selectById(columnId);
        if (col == null) throw new BusinessException(404, "数据项不存在");
        IngDataTable table = tableMapper.selectById(col.getTableId());
        if (table == null) throw new BusinessException(404, "数据表不存在");
        IngDataSource ds = dataSourceMapper.selectById(table.getSourceId());
        if (ds == null) throw new BusinessException(404, "数据库不存在");

        Long exists = linkMapper.selectCount(new LambdaQueryWrapper<IngDictColumnLink>()
                .eq(IngDictColumnLink::getDictId, dictId)
                .eq(IngDictColumnLink::getColumnId, columnId));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "该数据项已关联");
        }

        IngDictColumnLink link = new IngDictColumnLink();
        link.setDictId(dictId);
        link.setColumnId(columnId);
        link.setTableId(table.getId());
        link.setSourceId(ds.getId());
        link.setSystemId(ds.getSystemId());
        link.setProjectId(ds.getProjectId());
        link.setCreatedBy(operator.getUsername());
        linkMapper.insert(link);
        return link.getId();
    }

    @Transactional
    public void unbind(UserPrincipal operator, Long linkId) {
        IngDictColumnLink link = linkMapper.selectById(linkId);
        if (link == null) throw new BusinessException(404, "关联不存在");
        linkMapper.deleteById(linkId);
    }

    private void enrich(IngDictColumnLink link) {
        IngDataColumn col = columnMapper.selectById(link.getColumnId());
        if (col != null) {
            link.setColumnCode(col.getColumnCode());
            link.setColumnName(col.getColumnName());
        }
        if (link.getTableId() != null) {
            IngDataTable t = tableMapper.selectById(link.getTableId());
            if (t != null) link.setTableName(t.getTableName());
        }
        if (link.getSourceId() != null) {
            IngDataSource ds = dataSourceMapper.selectById(link.getSourceId());
            if (ds != null) link.setSourceName(ds.getSourceName());
        }
        if (link.getSystemId() != null) {
            IngBizSystem s = bizSystemMapper.selectById(link.getSystemId());
            if (s != null) link.setSystemName(s.getSystemName());
        }
        if (link.getProjectId() != null) {
            IngProject p = projectMapper.selectById(link.getProjectId());
            if (p != null) link.setProjectName(p.getProjectName());
        }
    }

    private static Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }
}
