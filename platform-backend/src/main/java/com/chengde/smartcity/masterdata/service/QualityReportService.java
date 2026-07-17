package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovQualityIssue;
import com.chengde.smartcity.masterdata.entity.GovQualityReport;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskRun;
import com.chengde.smartcity.masterdata.mapper.GovQualityIssueMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityReportMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QualityReportService {

    private static final Logger log = LoggerFactory.getLogger(QualityReportService.class);

    private final GovQualityReportMapper reportMapper;
    private final GovQualityTaskRunMapper runMapper;
    private final GovQualityIssueMapper issueMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QualityReportService(GovQualityReportMapper reportMapper,
                                GovQualityTaskRunMapper runMapper,
                                GovQualityIssueMapper issueMapper) {
        this.reportMapper = reportMapper;
        this.runMapper = runMapper;
        this.issueMapper = issueMapper;
    }

    public List<GovQualityReport> list() {
        return reportMapper.selectList(new LambdaQueryWrapper<GovQualityReport>()
                .orderByDesc(GovQualityReport::getId));
    }

    public Map<String, Object> detail(Long id) {
        GovQualityReport report = require(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", report);
        List<GovQualityTaskRun> recent = recentScoredRuns(20);
        out.put("recentRuns", recent);
        out.put("avgRunScore", avgScore(recent));
        out.put("runCount", recent.size());
        return out;
    }

    public Map<String, Object> drill(Long id) {
        GovQualityReport report = require(id);
        List<GovQualityTaskRun> runs = recentScoredRuns(10);
        List<Long> runIds = new ArrayList<>();
        for (GovQualityTaskRun r : runs) {
            runIds.add(r.getId());
        }
        List<GovQualityIssue> issues = new ArrayList<>();
        if (!runIds.isEmpty()) {
            issues = issueMapper.selectList(new LambdaQueryWrapper<GovQualityIssue>()
                    .in(GovQualityIssue::getRunId, runIds)
                    .orderByDesc(GovQualityIssue::getId)
                    .last("LIMIT 200"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reportId", report.getId());
        out.put("reportCode", report.getReportCode());
        out.put("score", report.getScore());
        out.put("runs", runs);
        out.put("issues", issues);
        out.put("issueCount", issues.size());
        return out;
    }

    public List<Map<String, Object>> trend(Integer days) {
        int n = days == null || days < 1 ? 14 : Math.min(days, 90);
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime from = LocalDateTime.of(day, LocalTime.MIN);
            LocalDateTime to = LocalDateTime.of(day, LocalTime.MAX);

            List<GovQualityReport> dayReports = reportMapper.selectList(new LambdaQueryWrapper<GovQualityReport>()
                    .ge(GovQualityReport::getCreatedAt, from)
                    .le(GovQualityReport::getCreatedAt, to));
            BigDecimal reportAvg = avgReportScore(dayReports);

            List<GovQualityTaskRun> dayRuns = runMapper.selectList(new LambdaQueryWrapper<GovQualityTaskRun>()
                    .ge(GovQualityTaskRun::getStartedAt, from)
                    .le(GovQualityTaskRun::getStartedAt, to)
                    .isNotNull(GovQualityTaskRun::getScore));
            BigDecimal runAvg = avgScore(dayRuns);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day.toString());
            point.put("reportCount", dayReports.size());
            point.put("reportAvgScore", reportAvg);
            point.put("runCount", dayRuns.size());
            point.put("runAvgScore", runAvg);
            trend.add(point);
        }
        return trend;
    }

    public Map<String, Object> exportJson(Long id) {
        Map<String, Object> detail = detail(id);
        Map<String, Object> drill = drill(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("detail", detail);
        payload.put("drill", drill);
        try {
            GovQualityReport report = require(id);
            report.setExportPayload(objectMapper.writeValueAsString(payload));
            reportMapper.updateById(report);
        } catch (Exception e) {
            log.warn("export payload persist failed: {}", e.getMessage());
        }
        return payload;
    }

    private GovQualityReport require(Long id) {
        GovQualityReport r = reportMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "质量报告不存在: " + id);
        }
        return r;
    }

    private List<GovQualityTaskRun> recentScoredRuns(int limit) {
        return runMapper.selectList(new LambdaQueryWrapper<GovQualityTaskRun>()
                .isNotNull(GovQualityTaskRun::getScore)
                .orderByDesc(GovQualityTaskRun::getId)
                .last("LIMIT " + limit));
    }

    private static BigDecimal avgScore(List<GovQualityTaskRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (GovQualityTaskRun r : runs) {
            if (r.getScore() != null) {
                sum = sum.add(r.getScore());
                n++;
            }
        }
        if (n == 0) return null;
        return sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal avgReportScore(List<GovQualityReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (GovQualityReport r : reports) {
            if (r.getScore() != null) {
                sum = sum.add(r.getScore());
                n++;
            }
        }
        if (n == 0) return null;
        return sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }
}
