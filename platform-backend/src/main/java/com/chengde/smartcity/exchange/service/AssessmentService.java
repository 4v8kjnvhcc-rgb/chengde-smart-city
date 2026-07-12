package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizCatalogItem;
import com.chengde.smartcity.exchange.entity.BizCollectTask;
import com.chengde.smartcity.exchange.entity.BizEvalDataSource;
import com.chengde.smartcity.exchange.entity.BizEvalExecution;
import com.chengde.smartcity.exchange.entity.BizEvalIndicator;
import com.chengde.smartcity.exchange.entity.BizEvalPeriod;
import com.chengde.smartcity.exchange.entity.BizEvalResult;
import com.chengde.smartcity.exchange.mapper.BizCatalogItemMapper;
import com.chengde.smartcity.exchange.mapper.BizCollectTaskMapper;
import com.chengde.smartcity.exchange.mapper.BizEvalDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.BizEvalExecutionMapper;
import com.chengde.smartcity.exchange.mapper.BizEvalIndicatorMapper;
import com.chengde.smartcity.exchange.mapper.BizEvalPeriodMapper;
import com.chengde.smartcity.exchange.mapper.BizEvalResultMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.mapper.AuditLogMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentService {

    private final BizEvalDataSourceMapper dataSourceMapper;
    private final BizEvalPeriodMapper periodMapper;
    private final BizEvalIndicatorMapper indicatorMapper;
    private final BizEvalExecutionMapper executionMapper;
    private final BizEvalResultMapper resultMapper;
    private final AuditLogMapper auditLogMapper;
    private final BizCatalogItemMapper catalogMapper;
    private final BizCollectTaskMapper collectTaskMapper;
    private final AuditService auditService;

    public AssessmentService(BizEvalDataSourceMapper dataSourceMapper, BizEvalPeriodMapper periodMapper,
                               BizEvalIndicatorMapper indicatorMapper, BizEvalExecutionMapper executionMapper,
                               BizEvalResultMapper resultMapper, AuditLogMapper auditLogMapper,
                               BizCatalogItemMapper catalogMapper, BizCollectTaskMapper collectTaskMapper,
                               AuditService auditService) {
        this.dataSourceMapper = dataSourceMapper;
        this.periodMapper = periodMapper;
        this.indicatorMapper = indicatorMapper;
        this.executionMapper = executionMapper;
        this.resultMapper = resultMapper;
        this.auditLogMapper = auditLogMapper;
        this.catalogMapper = catalogMapper;
        this.collectTaskMapper = collectTaskMapper;
        this.auditService = auditService;
    }

    public List<BizEvalDataSource> listDataSources() {
        return dataSourceMapper.selectList(new LambdaQueryWrapper<BizEvalDataSource>().orderByAsc(BizEvalDataSource::getId));
    }

    @Transactional
    public List<BizEvalDataSource> syncDataSources(UserPrincipal operator) {
        long auditCount = auditLogMapper.selectCount(null);
        long catalogCount = catalogMapper.selectCount(null);
        long taskCount = collectTaskMapper.selectCount(null);
        long taskOk = collectTaskMapper.selectCount(
                new LambdaQueryWrapper<BizCollectTask>().eq(BizCollectTask::getStatus, "SUCCESS"));

        updateSourceCount("DS_AUDIT", (int) auditCount);
        updateSourceCount("DS_EXCHANGE", (int) catalogCount);
        updateSourceCount("DS_COLLECT", (int) taskCount);

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "EVAL_DATA_SYNC", "biz_eval_data_source", "ALL",
                "audit=" + auditCount + ",catalog=" + catalogCount + ",tasks=" + taskOk + "/" + taskCount);
        return listDataSources();
    }

    private void updateSourceCount(String code, int count) {
        BizEvalDataSource ds = dataSourceMapper.selectOne(
                new LambdaQueryWrapper<BizEvalDataSource>().eq(BizEvalDataSource::getSourceCode, code));
        if (ds != null) {
            ds.setRecordCount(count);
            ds.setLastSyncAt(LocalDateTime.now());
            dataSourceMapper.updateById(ds);
        }
    }

    public List<BizEvalPeriod> listPeriods() {
        return periodMapper.selectList(new LambdaQueryWrapper<BizEvalPeriod>().orderByDesc(BizEvalPeriod::getId));
    }

    @Transactional
    public Long createPeriod(UserPrincipal operator, Map<String, Object> body) {
        BizEvalPeriod p = new BizEvalPeriod();
        p.setPeriodCode(str(body.get("periodCode"), "P" + System.currentTimeMillis()));
        p.setPeriodName(required(body.get("periodName"), "周期名称"));
        p.setCycleType(str(body.get("cycleType"), "MONTH"));
        p.setStartDate(LocalDate.parse(required(body.get("startDate"), "开始日期")));
        p.setEndDate(LocalDate.parse(required(body.get("endDate"), "结束日期")));
        p.setStatus("DRAFT");
        p.setCreatedBy(operator.getUsername());
        periodMapper.insert(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "EVAL_PERIOD_CREATE", "biz_eval_period", String.valueOf(p.getId()), p.getPeriodName());
        return p.getId();
    }

    @Transactional
    public void activatePeriod(UserPrincipal operator, Long id) {
        BizEvalPeriod p = periodMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "评价周期不存在");
        }
        p.setStatus("ACTIVE");
        periodMapper.updateById(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "EVAL_PERIOD_ACTIVATE", "biz_eval_period", String.valueOf(id), p.getPeriodName());
    }

    public List<BizEvalIndicator> listIndicators() {
        return indicatorMapper.selectList(new LambdaQueryWrapper<BizEvalIndicator>().orderByAsc(BizEvalIndicator::getId));
    }

    @Transactional
    public Long createIndicator(UserPrincipal operator, Map<String, Object> body) {
        BizEvalIndicator ind = new BizEvalIndicator();
        ind.setIndicatorCode(str(body.get("indicatorCode"), "IND_" + System.currentTimeMillis()));
        ind.setIndicatorName(required(body.get("indicatorName"), "指标名称"));
        ind.setIndicatorType(str(body.get("indicatorType"), "B"));
        ind.setWeight(new BigDecimal(str(body.get("weight"), "10")));
        Object dsId = body.get("dataSourceId");
        if (dsId != null) {
            ind.setDataSourceId(Long.valueOf(String.valueOf(dsId)));
        }
        ind.setFormulaDesc(str(body.get("formulaDesc"), ""));
        ind.setStatus("ACTIVE");
        indicatorMapper.insert(ind);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "EVAL_INDICATOR_CREATE", "biz_eval_indicator", String.valueOf(ind.getId()), ind.getIndicatorName());
        return ind.getId();
    }

    public List<BizEvalExecution> listExecutions() {
        return executionMapper.selectList(new LambdaQueryWrapper<BizEvalExecution>().orderByDesc(BizEvalExecution::getId));
    }

    public List<Map<String, Object>> listResults(Long executionId) {
        List<BizEvalResult> rows = resultMapper.selectList(
                new LambdaQueryWrapper<BizEvalResult>().eq(BizEvalResult::getExecutionId, executionId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizEvalResult r : rows) {
            BizEvalIndicator ind = indicatorMapper.selectById(r.getIndicatorId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("indicatorCode", ind != null ? ind.getIndicatorCode() : "");
            m.put("indicatorName", ind != null ? ind.getIndicatorName() : "");
            m.put("indicatorType", ind != null ? ind.getIndicatorType() : "");
            m.put("score", r.getScore());
            m.put("rawValue", r.getRawValue());
            m.put("remark", r.getRemark());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Long runEvaluation(UserPrincipal operator, Map<String, Object> body) {
        Long periodId = Long.valueOf(String.valueOf(required(body.get("periodId"), "评价周期")));
        BizEvalPeriod period = periodMapper.selectById(periodId);
        if (period == null) {
            throw new BusinessException(404, "评价周期不存在");
        }
        syncDataSources(operator);

        BizEvalExecution exec = new BizEvalExecution();
        exec.setPeriodId(periodId);
        exec.setTargetType(str(body.get("targetType"), "DEPT"));
        exec.setTargetName(required(body.get("targetName"), "考核对象"));
        exec.setStatus("COMPLETED");
        exec.setPublished(0);
        exec.setExecutedBy(operator.getUsername());
        exec.setExecutedAt(LocalDateTime.now());
        executionMapper.insert(exec);

        List<BizEvalIndicator> indicators = listIndicators();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;

        for (BizEvalIndicator ind : indicators) {
            if (!"ACTIVE".equals(ind.getStatus())) {
                continue;
            }
            BigDecimal score = computeScore(ind, body);
            BigDecimal weighted = score.multiply(ind.getWeight()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            total = total.add(weighted);
            weightSum = weightSum.add(ind.getWeight());

            BizEvalResult row = new BizEvalResult();
            row.setExecutionId(exec.getId());
            row.setIndicatorId(ind.getId());
            row.setScore(score);
            row.setRawValue(score.toPlainString());
            row.setRemark("A".equals(ind.getIndicatorType()) ? "自动采集计算" : "人工评分");
            resultMapper.insert(row);
        }
        exec.setTotalScore(total.setScale(2, RoundingMode.HALF_UP));
        executionMapper.updateById(exec);

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "EVAL_RUN", "biz_eval_execution", String.valueOf(exec.getId()),
                exec.getTargetName() + " score=" + exec.getTotalScore());
        return exec.getId();
    }

    private BigDecimal computeScore(BizEvalIndicator ind, Map<String, Object> body) {
        if ("B".equals(ind.getIndicatorType())) {
            Object manual = body.get("manualScores");
            if (manual instanceof Map<?, ?> map && map.containsKey(ind.getIndicatorCode())) {
                return new BigDecimal(String.valueOf(map.get(ind.getIndicatorCode())));
            }
            return new BigDecimal("85");
        }
        if ("IND_SHARE_RATE".equals(ind.getIndicatorCode())) {
            long total = catalogMapper.selectCount(null);
            long pub = catalogMapper.selectCount(
                    new LambdaQueryWrapper<BizCatalogItem>().eq(BizCatalogItem::getPublishStatus, "PUBLISHED"));
            if (total == 0) {
                return new BigDecimal("70");
            }
            return new BigDecimal(pub * 100).divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
        }
        if ("IND_COLLECT_OK".equals(ind.getIndicatorCode())) {
            long total = collectTaskMapper.selectCount(null);
            long ok = collectTaskMapper.selectCount(
                    new LambdaQueryWrapper<BizCollectTask>().eq(BizCollectTask::getStatus, "SUCCESS"));
            if (total == 0) {
                return new BigDecimal("75");
            }
            return new BigDecimal(ok * 100).divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
        }
        if ("IND_AUDIT_COVER".equals(ind.getIndicatorCode())) {
            long cnt = auditLogMapper.selectCount(null);
            return BigDecimal.valueOf(Math.min(100, 60 + cnt % 40));
        }
        return new BigDecimal("80");
    }

    @Transactional
    public void publishExecution(UserPrincipal operator, Long id) {
        BizEvalExecution exec = executionMapper.selectById(id);
        if (exec == null) {
            throw new BusinessException(404, "评价执行记录不存在");
        }
        exec.setPublished(1);
        exec.setStatus("PUBLISHED");
        executionMapper.updateById(exec);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "EVAL_PUBLISH", "biz_eval_execution", String.valueOf(id),
                exec.getTargetName() + " total=" + exec.getTotalScore());
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private String required(Object v, String label) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, label + "不能为空");
        }
        return String.valueOf(v);
    }
}
