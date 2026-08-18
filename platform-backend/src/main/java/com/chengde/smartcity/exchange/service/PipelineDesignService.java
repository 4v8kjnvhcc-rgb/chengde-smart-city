package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataDefinition;
import com.chengde.smartcity.exchange.entity.IngProbeReport;
import com.chengde.smartcity.exchange.entity.IngReconcileLog;
import com.chengde.smartcity.exchange.mapper.IngDataDefinitionMapper;
import com.chengde.smartcity.exchange.mapper.IngProbeReportMapper;
import com.chengde.smartcity.exchange.mapper.IngReconcileLogMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineDesignService {

    private final IngProbeReportMapper probeMapper;
    private final IngDataDefinitionMapper definitionMapper;
    private final IngReconcileLogMapper reconcileMapper;

    public PipelineDesignService(IngProbeReportMapper probeMapper, IngDataDefinitionMapper definitionMapper,
                                 IngReconcileLogMapper reconcileMapper) {
        this.probeMapper = probeMapper;
        this.definitionMapper = definitionMapper;
        this.reconcileMapper = reconcileMapper;
    }

    public List<IngProbeReport> listProbeReports() {
        return probeMapper.selectList(new LambdaQueryWrapper<IngProbeReport>().orderByDesc(IngProbeReport::getId));
    }

    public List<IngDataDefinition> listDefinitions() {
        return definitionMapper.selectList(new LambdaQueryWrapper<IngDataDefinition>().orderByAsc(IngDataDefinition::getId));
    }

    @Transactional
    public Long saveDefinition(UserPrincipal operator, Map<String, Object> body) {
        Long id = body.get("id") == null || String.valueOf(body.get("id")).isBlank()
                ? null : Long.valueOf(String.valueOf(body.get("id")));
        IngDataDefinition d = id == null ? new IngDataDefinition() : definitionMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "数据定义不存在");
        }
        if (id == null) {
            d.setDefCode(str(body.get("defCode"), "DEF_" + System.currentTimeMillis()));
            d.setStatus("ACTIVE");
        }
        d.setDefName(required(body.get("defName"), "defName").toString());
        d.setBusinessDesc(str(body.get("businessDesc"), ""));
        String techDesc = str(body.get("techDesc"), "");
        if (techDesc.isBlank() && body.get("refTableId") != null) {
            techDesc = "关联登记表#" + body.get("refTableId");
        }
        d.setTechDesc(techDesc);
        if (d.getMetadataJson() == null || d.getMetadataJson().isBlank()) {
            d.setMetadataJson("{\"items\":8,\"quality\":\"L1\",\"lineage\":\"linked\"}");
        }
        if (id == null) {
            definitionMapper.insert(d);
        } else {
            definitionMapper.updateById(d);
        }
        return d.getId();
    }

    @Transactional
    public void deleteDefinition(Long id) {
        IngDataDefinition d = definitionMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "数据定义不存在");
        }
        definitionMapper.deleteById(id);
    }

    @Transactional
    public Long createProbeReport(Map<String, Object> body) {
        IngProbeReport r = new IngProbeReport();
        r.setReportCode(str(body.get("reportCode"), "PRB_" + System.currentTimeMillis()));
        r.setSourceName(str(body.get("sourceName"), "未命名源表"));
        r.setNullRate(new BigDecimal(str(body.get("nullRate"), "0.018")));
        r.setDomainCheck(str(body.get("domainCheck"), "OK"));
        r.setEntityType(str(body.get("entityType"), "人名/地名"));
        r.setMetricsJson(str(body.get("metricsJson"), "{}"));
        r.setStatus(str(body.get("status"), "DONE"));
        r.setCreatedAt(java.time.LocalDateTime.now());
        probeMapper.insert(r);
        return r.getId();
    }

    public List<IngReconcileLog> listReconcileLogs() {
        return reconcileMapper.selectList(new LambdaQueryWrapper<IngReconcileLog>().orderByDesc(IngReconcileLog::getId));
    }

    @Transactional
    public Long appendReconcileLog(Map<String, Object> body) {
        IngReconcileLog log = new IngReconcileLog();
        log.setBatchNo(str(body.get("batchNo"), "RCN_" + System.currentTimeMillis()));
        log.setMatchedPct(new BigDecimal(str(body.get("matchedPct"), "98.5")));
        log.setDiffRows(Integer.parseInt(str(body.get("diffRows"), "0")));
        log.setAlertLevel(str(body.get("alertLevel"), "OK"));
        log.setStatus("OPEN");
        reconcileMapper.insert(log);
        return log.getId();
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
