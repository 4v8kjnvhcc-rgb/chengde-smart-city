package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.integration.jdbc.CredentialCipher;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.GovQualityIssue;
import com.chengde.smartcity.masterdata.entity.GovQualityRuleConfig;
import com.chengde.smartcity.masterdata.entity.GovQualityTask;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskDetail;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskRun;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityIssueMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleConfigMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskDetailMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskRunMapper;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.masterdata.support.LayerJdbcSupport;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 质量任务 JDBC 真实稽核执行。
 */
@Service
public class QualityExecuteService {

    private static final Logger log = LoggerFactory.getLogger(QualityExecuteService.class);
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final ObjectMapper OM = new ObjectMapper();
    private static final long PLATFORM_ODS_ID = -1L;
    private static final long PLATFORM_DWD_ID = -2L;
    private static final long PLATFORM_DWS_ID = -3L;
    private static final long PLATFORM_ADS_ID = -4L;

    private final GovQualityTaskMapper taskMapper;
    private final GovQualityTaskDetailMapper detailMapper;
    private final GovQualityTaskRunMapper runMapper;
    private final GovQualityIssueMapper issueMapper;
    private final GovQualityRuleConfigMapper configMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final GovMetadataRegistryMapper metadataRegistryMapper;
    private final LayerJdbcSupport layerJdbc;
    private final CredentialCipher credentialCipher;
    private final DataSource platformDataSource;

    public QualityExecuteService(GovQualityTaskMapper taskMapper,
                                 GovQualityTaskDetailMapper detailMapper,
                                 GovQualityTaskRunMapper runMapper,
                                 GovQualityIssueMapper issueMapper,
                                 GovQualityRuleConfigMapper configMapper,
                                 IngDataSourceMapper dataSourceMapper,
                                 GovMetadataRegistryMapper metadataRegistryMapper,
                                 LayerJdbcSupport layerJdbc,
                                 CredentialCipher credentialCipher,
                                 @Autowired(required = false) DataSource platformDataSource) {
        this.taskMapper = taskMapper;
        this.detailMapper = detailMapper;
        this.runMapper = runMapper;
        this.issueMapper = issueMapper;
        this.configMapper = configMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.metadataRegistryMapper = metadataRegistryMapper;
        this.layerJdbc = layerJdbc;
        this.credentialCipher = credentialCipher;
        this.platformDataSource = platformDataSource;
    }

    @Transactional
    public Map<String, Object> executeTask(UserPrincipal operator, Long taskId) {
        GovQualityTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "质量任务不存在: " + taskId);
        }
        List<GovQualityTaskDetail> details = detailMapper.selectList(new LambdaQueryWrapper<GovQualityTaskDetail>()
                .eq(GovQualityTaskDetail::getTaskId, taskId)
                .eq(GovQualityTaskDetail::getStatus, "ENABLED")
                .orderByAsc(GovQualityTaskDetail::getSortOrder)
                .orderByAsc(GovQualityTaskDetail::getId));
        if (details.isEmpty() && task.getRuleId() != null) {
            // 兼容仅关联单规则、无明细的老任务：按 rule-mgmt 配置执行一条
            GovQualityTaskDetail syn = new GovQualityTaskDetail();
            syn.setTaskId(taskId);
            syn.setRuleId(task.getRuleId());
            syn.setStatus("ENABLED");
            details = List.of(syn);
        }
        if (details.isEmpty()) {
            throw new BusinessException(400, "任务未配置稽核明细，请先添加规则");
        }

        GovMetadataRegistry taskEntry = resolveEntry(task.getMetadataEntryCode());
        List<ResolvedCheck> resolved = new ArrayList<>();
        for (GovQualityTaskDetail detail : details) {
            resolved.add(resolveCheck(task, taskEntry, detail));
        }

        LocalDateTime started = LocalDateTime.now();
        GovQualityTaskRun run = new GovQualityTaskRun();
        run.setTaskId(taskId);
        run.setStatus("RUNNING");
        run.setStartedAt(started);
        run.setTotalChecks(0);
        run.setIssueCount(0);
        run.setTriggeredBy(operator != null ? operator.getUsername() : null);
        run.setCreatedAt(started);
        runMapper.insert(run);

        task.setStatus("RUNNING");
        task.setUpdatedAt(started);
        taskMapper.updateById(task);

        int totalChecks = 0;
        int issueRows = 0;
        int failedChecks = 0;
        String lastErr = null;

        try (Connection conn = openConnection(task, taskEntry)) {
            for (ResolvedCheck rc : resolved) {
                totalChecks++;
                GovQualityTaskDetail detail = rc.detail;
                String checkType = rc.checkType;
                String table = rc.table;
                String column = rc.column;
                BigDecimal threshold = rc.threshold;
                String configJson = rc.configJson;

                try {
                    CheckResult cr = runCheck(conn, checkType, table, column, threshold, configJson);
                    if (cr.hasIssue) {
                        failedChecks++;
                        issueRows += cr.issueCount;
                        GovQualityIssue issue = new GovQualityIssue();
                        issue.setRunId(run.getId());
                        issue.setTaskId(taskId);
                        issue.setRuleId(detail.getRuleId());
                        issue.setDetailId(detail.getId());
                        issue.setCheckType(checkType);
                        issue.setTargetTable(table);
                        issue.setTargetColumn(column);
                        issue.setIssueType(cr.issueType);
                        issue.setIssueValue(cr.issueValue);
                        issue.setIssueCount(cr.issueCount);
                        issue.setSampleData(cr.sampleData);
                        issue.setSeverity(cr.severity);
                        issue.setStatus("OPEN");
                        issue.setCreatedAt(LocalDateTime.now());
                        issueMapper.insert(issue);
                    }
                } catch (Exception ex) {
                    failedChecks++;
                    lastErr = ex.getMessage();
                    log.warn("quality check failed task={} detail={} type={}: {}",
                            taskId, detail.getId(), checkType, ex.getMessage());
                    GovQualityIssue issue = new GovQualityIssue();
                    issue.setRunId(run.getId());
                    issue.setTaskId(taskId);
                    issue.setRuleId(detail.getRuleId());
                    issue.setDetailId(detail.getId());
                    issue.setCheckType(checkType);
                    issue.setTargetTable(table);
                    issue.setTargetColumn(column);
                    issue.setIssueType("ERROR");
                    issue.setIssueValue(truncate(ex.getMessage(), 500));
                    issue.setIssueCount(1);
                    issue.setSeverity("HIGH");
                    issue.setStatus("OPEN");
                    issue.setCreatedAt(LocalDateTime.now());
                    issueMapper.insert(issue);
                    issueRows++;
                }
            }
        } catch (Exception e) {
            lastErr = e.getMessage();
            log.error("quality task execute connection failed task={}: {}", taskId, e.getMessage());
            run.setStatus("FAILED");
            run.setEndedAt(LocalDateTime.now());
            run.setTotalChecks(totalChecks);
            run.setIssueCount(issueRows);
            run.setMessage(truncate("执行失败: " + e.getMessage(), 500));
            run.setScore(BigDecimal.ZERO);
            runMapper.updateById(run);
            task.setStatus("FAILED");
            task.setLastRunAt(run.getEndedAt());
            task.setLastScore(BigDecimal.ZERO);
            task.setLastMessage(run.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("taskId", taskId);
            fail.put("runId", run.getId());
            fail.put("status", "FAILED");
            fail.put("score", BigDecimal.ZERO);
            fail.put("message", run.getMessage());
            return fail;
        }

        BigDecimal score;
        if (totalChecks == 0) {
            score = BigDecimal.valueOf(100);
        } else {
            double ratio = 1.0 - ((double) failedChecks / (double) totalChecks);
            score = BigDecimal.valueOf(ratio * 100).setScale(2, RoundingMode.HALF_UP);
        }

        String status = failedChecks == totalChecks && totalChecks > 0 ? "FAILED" : "SUCCESS";
        if (lastErr != null && failedChecks == totalChecks) {
            status = "FAILED";
        }
        LocalDateTime ended = LocalDateTime.now();
        run.setStatus(status);
        run.setEndedAt(ended);
        run.setScore(score);
        run.setTotalChecks(totalChecks);
        run.setIssueCount(issueRows);
        run.setMessage(String.format("检查%d项，失败%d项，问题行%d；score=%s",
                totalChecks, failedChecks, issueRows, score));
        runMapper.updateById(run);

        task.setStatus(status.equals("FAILED") ? "FAILED" : "READY");
        task.setLastRunAt(ended);
        task.setLastScore(score);
        task.setLastMessage(run.getMessage());
        task.setUpdatedAt(ended);
        taskMapper.updateById(task);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", taskId);
        out.put("runId", run.getId());
        out.put("status", status);
        out.put("score", score);
        out.put("totalChecks", totalChecks);
        out.put("failedChecks", failedChecks);
        out.put("issueCount", issueRows);
        out.put("message", run.getMessage());
        return out;
    }

    private GovQualityRuleConfig resolveConfig(GovQualityTaskDetail detail) {
        if (detail.getRuleId() == null) {
            return null;
        }
        return configMapper.selectOne(new LambdaQueryWrapper<GovQualityRuleConfig>()
                .eq(GovQualityRuleConfig::getRuleId, detail.getRuleId())
                .last("LIMIT 1"));
    }

    private CheckResult runCheck(Connection conn, String checkType, String table, String column,
                                 BigDecimal threshold, String configJson) throws Exception {
        String type = checkType == null ? "NULL_CHECK" : checkType.toUpperCase();
        return switch (type) {
            case "NULL_CHECK" -> checkNull(conn, table, column, threshold);
            case "UNIQUENESS" -> checkUnique(conn, table, column, threshold);
            case "ACCURACY" -> checkAccuracy(conn, table, column, threshold, configJson);
            case "RECORD_COUNT" -> checkRecordCount(conn, table, threshold, configJson);
            default -> throw new BusinessException(400, "不支持的检查类型: " + checkType);
        };
    }

    private CheckResult checkNull(Connection conn, String table, String column, BigDecimal threshold) throws Exception {
        requireIdent(table, "targetTable");
        requireIdent(column, "targetColumn");
        long total = queryLong(conn, "SELECT COUNT(*) FROM `" + table + "`");
        long nulls = queryLong(conn, "SELECT COUNT(*) FROM `" + table + "` WHERE `" + column
                + "` IS NULL OR TRIM(CAST(`" + column + "` AS CHAR)) = ''");
        double rate = total == 0 ? 0 : (nulls * 100.0 / total);
        double limit = threshold != null ? threshold.doubleValue() : 0;
        CheckResult cr = new CheckResult();
        if (nulls > 0 && rate > limit) {
            cr.hasIssue = true;
            cr.issueType = "NULL";
            cr.issueCount = (int) Math.min(nulls, Integer.MAX_VALUE);
            cr.issueValue = String.format("空值率=%.2f%% 阈值=%.2f%% 空值数=%d/%d", rate, limit, nulls, total);
            cr.sampleData = sampleNulls(conn, table, column);
            cr.severity = rate > limit * 2 ? "HIGH" : "MEDIUM";
        }
        return cr;
    }

    private CheckResult checkUnique(Connection conn, String table, String column, BigDecimal threshold) throws Exception {
        requireIdent(table, "targetTable");
        requireIdent(column, "targetColumn");
        long total = queryLong(conn, "SELECT COUNT(*) FROM `" + table + "` WHERE `" + column + "` IS NOT NULL");
        long dupGroups = queryLong(conn,
                "SELECT COUNT(*) FROM (SELECT `" + column + "` FROM `" + table
                        + "` WHERE `" + column + "` IS NOT NULL GROUP BY `" + column + "` HAVING COUNT(*) > 1) t");
        long dupRows = queryLong(conn,
                "SELECT COALESCE(SUM(cnt - 1),0) FROM (SELECT COUNT(*) cnt FROM `" + table
                        + "` WHERE `" + column + "` IS NOT NULL GROUP BY `" + column + "` HAVING COUNT(*) > 1) t");
        double rate = total == 0 ? 0 : (dupRows * 100.0 / total);
        double limit = threshold != null ? threshold.doubleValue() : 0;
        CheckResult cr = new CheckResult();
        if (dupGroups > 0 && rate > limit) {
            cr.hasIssue = true;
            cr.issueType = "DUPLICATE";
            cr.issueCount = (int) Math.min(dupRows, Integer.MAX_VALUE);
            cr.issueValue = String.format("重复率=%.2f%% 阈值=%.2f%% 重复组=%d", rate, limit, dupGroups);
            cr.sampleData = sampleDups(conn, table, column);
            cr.severity = "MEDIUM";
        }
        return cr;
    }

    private CheckResult checkAccuracy(Connection conn, String table, String column,
                                      BigDecimal threshold, String configJson) throws Exception {
        requireIdent(table, "targetTable");
        requireIdent(column, "targetColumn");
        JsonNode cfg = parseJson(configJson);
        String pattern = text(cfg, "pattern");
        String min = text(cfg, "min");
        String max = text(cfg, "max");
        List<String> enumVals = new ArrayList<>();
        if (cfg != null && cfg.has("enum") && cfg.get("enum").isArray()) {
            cfg.get("enum").forEach(n -> enumVals.add(n.asText()));
        }

        long total = queryLong(conn, "SELECT COUNT(*) FROM `" + table + "` WHERE `" + column + "` IS NOT NULL");
        long invalid = 0;
        List<String> samples = new ArrayList<>();

        String sql = "SELECT `" + column + "` FROM `" + table + "` WHERE `" + column + "` IS NOT NULL LIMIT 5000";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String val = rs.getString(1);
                if (val == null) continue;
                boolean ok = true;
                if (pattern != null && !pattern.isBlank()) {
                    ok = Pattern.compile(pattern).matcher(val).matches();
                } else if (!enumVals.isEmpty()) {
                    ok = enumVals.contains(val);
                } else if (min != null || max != null) {
                    try {
                        BigDecimal n = new BigDecimal(val.trim());
                        if (min != null && n.compareTo(new BigDecimal(min)) < 0) ok = false;
                        if (max != null && n.compareTo(new BigDecimal(max)) > 0) ok = false;
                    } catch (Exception e) {
                        ok = false;
                    }
                } else {
                    // 无参数时跳过准确性（视为通过）
                    return new CheckResult();
                }
                if (!ok) {
                    invalid++;
                    if (samples.size() < 10) samples.add(val);
                }
            }
        }
        double rate = total == 0 ? 0 : (invalid * 100.0 / Math.min(total, 5000));
        double limit = threshold != null ? threshold.doubleValue() : 0;
        CheckResult cr = new CheckResult();
        if (invalid > 0 && rate > limit) {
            cr.hasIssue = true;
            cr.issueType = "INVALID";
            cr.issueCount = (int) Math.min(invalid, Integer.MAX_VALUE);
            cr.issueValue = String.format("不合规率=%.2f%% 阈值=%.2f%% 样本不合规=%d", rate, limit, invalid);
            cr.sampleData = String.join(" | ", samples);
            cr.severity = "MEDIUM";
        }
        return cr;
    }

    private CheckResult checkRecordCount(Connection conn, String table, BigDecimal threshold,
                                         String configJson) throws Exception {
        requireIdent(table, "targetTable");
        long count = queryLong(conn, "SELECT COUNT(*) FROM `" + table + "`");
        JsonNode cfg = parseJson(configJson);
        Long min = longVal(cfg, "min");
        Long max = longVal(cfg, "max");
        if (min == null && max == null && threshold != null) {
            // threshold 作为期望行数（偏差>0 即告警）；若为百分数含义则以等于为期望
            long expected = threshold.longValue();
            min = expected;
            max = expected;
        }
        CheckResult cr = new CheckResult();
        boolean bad = false;
        if (min != null && count < min) bad = true;
        if (max != null && count > max) bad = true;
        if (bad) {
            cr.hasIssue = true;
            cr.issueType = "COUNT";
            cr.issueCount = 1;
            cr.issueValue = String.format("实际行数=%d 期望范围=[%s,%s]", count,
                    min == null ? "-" : min, max == null ? "-" : max);
            cr.severity = "MEDIUM";
        }
        return cr;
    }

    private String sampleNulls(Connection conn, String table, String column) throws Exception {
        String sql = "SELECT * FROM `" + table + "` WHERE `" + column
                + "` IS NULL OR TRIM(CAST(`" + column + "` AS CHAR)) = '' LIMIT 5";
        return sampleRows(conn, sql);
    }

    private String sampleDups(Connection conn, String table, String column) throws Exception {
        String sql = "SELECT `" + column + "`, COUNT(*) c FROM `" + table
                + "` WHERE `" + column + "` IS NOT NULL GROUP BY `" + column
                + "` HAVING COUNT(*) > 1 ORDER BY c DESC LIMIT 5";
        StringBuilder sb = new StringBuilder();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(rs.getString(1)).append("×").append(rs.getLong(2));
            }
        }
        return sb.toString();
    }

    private String sampleRows(Connection conn, String sql) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            int n = 0;
            while (rs.next() && n < 5) {
                if (sb.length() > 0) sb.append(" || ");
                StringBuilder row = new StringBuilder("{");
                for (int i = 1; i <= Math.min(cols, 6); i++) {
                    if (i > 1) row.append(',');
                    row.append(rs.getMetaData().getColumnLabel(i)).append('=')
                            .append(truncate(String.valueOf(rs.getObject(i)), 40));
                }
                row.append('}');
                sb.append(row);
                n++;
            }
        }
        return truncate(sb.toString(), 1000);
    }

    private ResolvedCheck resolveCheck(GovQualityTask task, GovMetadataRegistry taskEntry, GovQualityTaskDetail detail) {
        GovQualityRuleConfig cfg = resolveConfig(detail);
        GovMetadataRegistry entry = firstNonNull(
                resolveEntry(cfg != null ? cfg.getMetadataEntryCode() : null),
                taskEntry);
        String checkType = firstNonBlank(detail.getCheckType(), cfg != null ? cfg.getCheckType() : null, "NULL_CHECK");
        String table = firstNonBlank(
                detail.getTargetTable(),
                cfg != null ? cfg.getTargetTable() : null,
                entry != null ? entry.getPhysicalTableName() : null);
        String column = firstNonBlank(detail.getTargetColumn(), cfg != null ? cfg.getTargetColumn() : null);
        if (table == null || table.isBlank()) {
            throw new BusinessException(400, "稽核目标表未配置：请在规则或任务明细中选择表（可关联元数据 entry_code）");
        }
        if (!"RECORD_COUNT".equalsIgnoreCase(checkType) && (column == null || column.isBlank())) {
            throw new BusinessException(400, "稽核目标字段未配置：检查类型 " + checkType + " 需要字段");
        }
        ResolvedCheck rc = new ResolvedCheck();
        rc.detail = detail;
        rc.checkType = checkType;
        rc.table = table.trim();
        rc.column = column == null ? null : column.trim();
        rc.threshold = cfg != null ? cfg.getThreshold() : null;
        rc.configJson = cfg != null ? cfg.getConfigJson() : null;
        return rc;
    }

    private Connection openConnection(GovQualityTask task, GovMetadataRegistry taskEntry) throws Exception {
        Long dsId = task.getDatasourceId();
        if (dsId == null && taskEntry != null && taskEntry.getDataSourceId() != null) {
            dsId = taskEntry.getDataSourceId();
        }
        String catalogHint = null;
        if (taskEntry != null) {
            catalogHint = firstNonBlank(taskEntry.getDatabaseName(), taskEntry.getSchemaName());
        }
        if (isPlatformLayerId(dsId)) {
            return layerJdbc.open(platformLayerDatabase(dsId));
        }
        if (dsId != null) {
            IngDataSource ds = dataSourceMapper.selectById(dsId);
            if (ds == null) {
                throw new BusinessException(400, "数据源不存在: " + dsId);
            }
            String cfg = ds.getConnConfigJson();
            if (cfg == null || cfg.isBlank()) {
                throw new BusinessException(400, "数据源未配置连接信息");
            }
            JsonNode n = OM.readTree(cfg);
            String host = text(n, "host");
            String port = text(n, "port");
            String database = text(n, "database");
            String username = text(n, "username");
            String password = text(n, "password");
            // 兼容：连接配置优先存 passwordCipher（密文），执行时也要解密后再连库
            if (password == null || password.isBlank()) {
                String cipher = text(n, "passwordCipher");
                if (cipher != null && !cipher.isBlank()) {
                    password = credentialCipher.decrypt(cipher);
                }
            }
            if (host == null || database == null) {
                throw new BusinessException(400, "数据源缺少 host/database");
            }
            String url = "jdbc:mysql://" + host + ":" + (port == null ? "3306" : port) + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
            return DriverManager.getConnection(url, username == null ? "" : username, password == null ? "" : password);
        }
        if (platformDataSource != null) {
            Connection conn = platformDataSource.getConnection();
            if (catalogHint != null && isKnownLayerDb(catalogHint)) {
                conn.close();
                return layerJdbc.open(catalogHint);
            }
            return conn;
        }
        throw new BusinessException(500, "无可用 JDBC 数据源，请为任务选择平台分层库或登记数据源");
    }

    private GovMetadataRegistry resolveEntry(String entryCode) {
        if (entryCode == null || entryCode.isBlank()) {
            return null;
        }
        return metadataRegistryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, entryCode.trim())
                .last("LIMIT 1"));
    }

    private static void applyCatalog(Connection conn, String catalog) throws Exception {
        if (catalog == null || catalog.isBlank()) {
            return;
        }
        requireIdent(catalog, "database");
        conn.setCatalog(catalog);
    }

    private static boolean isPlatformLayerId(Long id) {
        return id != null && (id == PLATFORM_ODS_ID || id == PLATFORM_DWD_ID
                || id == PLATFORM_DWS_ID || id == PLATFORM_ADS_ID);
    }

    private static String platformLayerDatabase(Long id) {
        if (id == PLATFORM_ODS_ID) {
            return DataLayerSupport.ODS;
        }
        if (id == PLATFORM_DWD_ID) {
            return DataLayerSupport.DWD;
        }
        if (id == PLATFORM_DWS_ID) {
            return DataLayerSupport.DWS;
        }
        if (id == PLATFORM_ADS_ID) {
            return DataLayerSupport.ADS;
        }
        throw new BusinessException(400, "非平台分层数据源");
    }

    private static boolean isKnownLayerDb(String db) {
        return DataLayerSupport.ODS.equalsIgnoreCase(db)
                || DataLayerSupport.DWD.equalsIgnoreCase(db)
                || DataLayerSupport.DWS.equalsIgnoreCase(db)
                || DataLayerSupport.ADS.equalsIgnoreCase(db)
                || DataLayerSupport.CONTROL.equalsIgnoreCase(db);
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    private static long queryLong(Connection conn, String sql) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    private static void requireIdent(String name, String field) {
        if (name == null || name.isBlank() || !IDENT.matcher(name).matches()) {
            throw new BusinessException(400, field + " 非法或为空: " + name);
        }
    }

    private static JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return OM.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        String s = n.get(field).asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static Long longVal(JsonNode n, String field) {
        String s = text(n, field);
        if (s == null) return null;
        try {
            return Long.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static class ResolvedCheck {
        GovQualityTaskDetail detail;
        String checkType;
        String table;
        String column;
        BigDecimal threshold;
        String configJson;
    }

    private static class CheckResult {
        boolean hasIssue;
        String issueType;
        String issueValue;
        int issueCount = 1;
        String sampleData;
        String severity = "MEDIUM";
    }
}
