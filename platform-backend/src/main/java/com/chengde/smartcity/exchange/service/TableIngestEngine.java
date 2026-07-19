package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 库表接入统一引擎：单表（分表/增量）、多表批量、条件 SQL → Kettle 落 ODS。
 */
@Service
public class TableIngestEngine {

    private static final Logger log = LoggerFactory.getLogger(TableIngestEngine.class);
    private static final Pattern PARAM = Pattern.compile("\\$\\{([A-Za-z0-9_]+)\\}");

    private final IngIngestTaskMapper taskMapper;
    private final IngDataTableMapper dataTableMapper;
    private final IngDataColumnMapper dataColumnMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final KettleCollectService kettleCollectService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TableIngestEngine(IngIngestTaskMapper taskMapper, IngDataTableMapper dataTableMapper,
                             IngDataColumnMapper dataColumnMapper, IngDataSourceMapper dataSourceMapper,
                             KettleCollectService kettleCollectService) {
        this.taskMapper = taskMapper;
        this.dataTableMapper = dataTableMapper;
        this.dataColumnMapper = dataColumnMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.kettleCollectService = kettleCollectService;
    }

    public Map<String, Object> runJob(UserPrincipal operator, Long taskId) {
        IngIngestTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "接入任务不存在");
        }
        if ("RUNNING".equals(task.getStatus())) {
            LocalDateTime started = task.getLastRunAt();
            boolean stale = started == null || started.isBefore(LocalDateTime.now().minusMinutes(5));
            if (!stale) {
                throw new BusinessException(409, "任务正在执行中，请稍候；若长时间无响应可点「重置」");
            }
            task.setStatus("FAILED");
            task.setLastRunMessage("上次执行超时，已自动重置后重新执行");
            task.setErrorDetail("stale RUNNING before re-run");
            task.setLastRunAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        JsonNode cfg = readConfig(task.getConfigJson());
        String mode = str(task.getAccessMode(), text(cfg, "accessMode", "SINGLE")).toUpperCase(Locale.ROOT);
        try {
            return switch (mode) {
                case "MULTI" -> runMulti(operator, task, cfg);
                case "SQL" -> runSql(operator, task, cfg);
                default -> runSingle(operator, task, cfg);
            };
        } catch (BusinessException e) {
            // failTask 已写 FAILED；若仍卡在 RUNNING 则兜底
            IngIngestTask latest = taskMapper.selectById(taskId);
            if (latest != null && "RUNNING".equals(latest.getStatus())) {
                latest.setStatus("FAILED");
                latest.setLastRunMessage("汇聚失败");
                latest.setErrorDetail(e.getMessage() == null ? "error" : e.getMessage().substring(0, Math.min(1000, e.getMessage().length())));
                latest.setLastRunAt(LocalDateTime.now());
                taskMapper.updateById(latest);
            }
            throw e;
        } catch (Exception e) {
            log.warn("runJob failed id={}: {}", taskId, e.getMessage());
            IngIngestTask latest = taskMapper.selectById(taskId);
            if (latest != null) {
                latest.setStatus("FAILED");
                latest.setLastRunAt(LocalDateTime.now());
                latest.setLastRunMessage("汇聚失败");
                latest.setErrorDetail(e.getMessage() == null ? "error" : e.getMessage().substring(0, Math.min(1000, e.getMessage().length())));
                taskMapper.updateById(latest);
            }
            throw new BusinessException(502, e.getMessage());
        }
    }

    public Map<String, Object> runJobBySystem(Long taskId) {
        return runJob(null, taskId);
    }

    public Map<String, Object> preview(Map<String, Object> body) {
        JsonNode cfg = objectMapper.valueToTree(body == null ? Map.of() : body);
        String mode = text(cfg, "accessMode", "SINGLE").toUpperCase(Locale.ROOT);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accessMode", mode);
        if ("SQL".equals(mode)) {
            JsonNode sql = cfg.path("sql");
            String resolved = resolveSqlParams(text(sql, "selectSql", ""), sql.path("paramBindings"));
            kettleCollectService.validateSelectSql(resolved);
            out.put("resolvedSql", resolved);
            out.put("targetTable", kettleCollectService.sanitizeOdsName(text(sql, "targetTable", "ods_sql_result")));
            return out;
        }
        if ("MULTI".equals(mode)) {
            List<Long> ids = readLongList(cfg.path("multi").path("tableIds"));
            List<Map<String, Object>> items = new ArrayList<>();
            for (Long id : ids) {
                IngDataTable t = dataTableMapper.selectById(id);
                if (t == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tableId", id);
                m.put("sourceTable", t.getSourceTable());
                m.put("targetTable", suggestOds(t, text(cfg.path("multi"), "targetTableRule", "")));
                items.add(m);
            }
            out.put("tables", items);
            return out;
        }
        JsonNode single = cfg.path("single");
        Long tableId = longNode(single, "tableId");
        if (tableId == null) {
            throw new BusinessException(400, "请选择源表");
        }
        IngDataTable table = dataTableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "登记表不存在");
        }
        String physical = resolvePhysicalSource(table, single);
        String target = text(single, "targetTable", "");
        if (target.isBlank()) {
            target = suggestOds(table, "");
        } else {
            target = kettleCollectService.sanitizeOdsName(target);
        }
        List<CollectCopyRequest.FieldPair> fields = buildFields(tableId, cfg.path("mapping"));
        String writeMode = text(cfg, "writeMode", str(null, "FULL"));
        if (writeMode.isBlank()) writeMode = "FULL";
        String where = null;
        String watermarkNext = null;
        if ("INCREMENTAL".equalsIgnoreCase(writeMode) || "INCREMENTAL".equalsIgnoreCase(text(cfg, "writeMode", ""))) {
            String incCol = text(single, "incrementColumn", "");
            if (incCol.isBlank()) {
                throw new BusinessException(400, "增量接入需配置增量列");
            }
            String wm = text(cfg, "watermarkValue", "");
            if (wm.isBlank()) {
                where = "`" + sanitizeIdent(incCol) + "` IS NOT NULL";
            } else {
                where = "`" + sanitizeIdent(incCol) + "` > '" + escapeLiteral(wm) + "'";
            }
            watermarkNext = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        String selectSql = kettleCollectService.buildMappedSelectSql(physical, fields, where);
        out.put("physicalSourceTable", physical);
        out.put("targetTable", target);
        out.put("selectSql", selectSql);
        out.put("fieldCount", fields.size());
        out.put("writeMode", writeMode);
        out.put("watermarkNext", watermarkNext);
        return out;
    }

    public List<Map<String, Object>> suggestMapping(Long tableId, String mode) {
        List<IngDataColumn> columns = dataColumnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId).orderByAsc(IngDataColumn::getSortOrder));
        List<Map<String, Object>> pairs = new ArrayList<>();
        for (IngDataColumn c : columns) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("source", c.getColumnCode());
            p.put("target", c.getColumnCode());
            p.put("dataType", c.getDataType());
            p.put("length", c.getLengthVal());
            p.put("columnName", c.getColumnName());
            pairs.add(p);
        }
        return pairs;
    }

    private Map<String, Object> runSingle(UserPrincipal operator, IngIngestTask task, JsonNode cfg) {
        JsonNode single = cfg.path("single");
        Long tableId = longNode(single, "tableId");
        if (tableId == null) {
            tableId = task.getTableId();
        }
        if (tableId == null) {
            throw new BusinessException(400, "请配置源表");
        }
        IngDataTable table = dataTableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "登记表不存在");
        }
        String physical = resolvePhysicalSource(table, single);
        String target = text(single, "targetTable", task.getTargetTable() == null ? "" : task.getTargetTable());
        if (target.isBlank()) {
            target = suggestOds(table, "");
        }
        List<CollectCopyRequest.FieldPair> fields = buildFields(tableId, cfg.path("mapping"));
        String writeMode = firstNonBlank(task.getWriteMode(), text(cfg, "writeMode", "FULL"));
        boolean truncate = !"INCREMENTAL".equalsIgnoreCase(writeMode);
        String where = null;
        String watermarkNext = null;
        if (!truncate) {
            String incCol = text(single, "incrementColumn", "");
            if (incCol.isBlank()) {
                throw new BusinessException(400, "增量接入需配置增量列");
            }
            String wm = firstNonBlank(task.getWatermarkValue(), text(cfg, "watermarkValue", ""));
            if (wm.isBlank()) {
                where = "`" + sanitizeIdent(incCol) + "` IS NOT NULL";
            } else {
                where = "`" + sanitizeIdent(incCol) + "` > '" + escapeLiteral(wm) + "'";
            }
            watermarkNext = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        String selectSql = kettleCollectService.buildMappedSelectSql(physical, fields, where);
        CollectCopyRequest req = new CollectCopyRequest();
        req.setSourceId(table.getSourceId());
        req.setTableId(tableId);
        req.setPhysicalSourceTable(physical);
        req.setSelectSql(selectSql);
        req.setOdsTable(target);
        req.setTruncate(truncate);
        req.setWatermarkAfterSuccess(watermarkNext);
        req.setLedger(new CollectCopyRequest.IngIngestTaskLedger(task.getId()));
        req.getFields().addAll(fields);
        Map<String, Object> result = kettleCollectService.executeCopy(operator, req);
        result.put("accessMode", "SINGLE");
        result.put("physicalSourceTable", physical);
        return result;
    }

    private Map<String, Object> runMulti(UserPrincipal operator, IngIngestTask task, JsonNode cfg) {
        JsonNode multi = cfg.path("multi");
        List<Long> tableIds = readLongList(multi.path("tableIds"));
        List<Long> excludes = readLongList(multi.path("excludeTableIds"));
        tableIds.removeIf(excludes::contains);
        if (tableIds.isEmpty()) {
            throw new BusinessException(400, "请至少选择一张表");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        long totalRows = 0;
        for (Long tableId : tableIds) {
            IngDataTable table = dataTableMapper.selectById(tableId);
            if (table == null) {
                errors.add("表ID=" + tableId + " 不存在");
                continue;
            }
            // 多表必须按各自登记字段映射，禁止复用首表 mapping（否则 Carte SELECT 列不存在会 FAILED）
            List<CollectCopyRequest.FieldPair> fields = buildFields(tableId, null);
            if (fields.isEmpty()) {
                errors.add(table.getTableName() + " 无登记字段，已跳过");
                continue;
            }
            if (table.getSourceTable() == null || table.getSourceTable().isBlank()) {
                errors.add(table.getTableName() + " 缺少源物理表名，已跳过");
                continue;
            }
            String target = suggestOds(table, text(multi, "targetTableRule", ""));
            CollectCopyRequest req = new CollectCopyRequest();
            req.setSourceId(table.getSourceId());
            req.setTableId(tableId);
            req.setPhysicalSourceTable(table.getSourceTable());
            req.setOdsTable(target);
            req.setTruncate(true);
            req.setLedger(new CollectCopyRequest.IngIngestTaskLedger(task.getId()));
            req.getFields().addAll(fields);
            try {
                if (!"RUNNING".equals(task.getStatus())) {
                    task.setStatus("RUNNING");
                    task.setLastRunAt(LocalDateTime.now());
                    task.setLastRunMessage("多表汇聚中 " + (results.size() + 1) + "/" + tableIds.size()
                            + "：" + table.getTableName());
                    taskMapper.updateById(task);
                } else {
                    task.setLastRunMessage("多表汇聚中 " + (results.size() + 1) + "/" + tableIds.size()
                            + "：" + table.getTableName());
                    taskMapper.updateById(task);
                }
                Map<String, Object> one = kettleCollectService.executeCopy(operator, req);
                if ("ABORTED".equals(String.valueOf(one.get("status")))) {
                    throw new BusinessException(409, "任务已重置，多表执行中止");
                }
                results.add(one);
                Object rows = one.get("collectedRows");
                if (rows instanceof Number n) {
                    totalRows += n.longValue();
                }
            } catch (BusinessException e) {
                if (e.getCode() == 409) {
                    throw e;
                }
                String msg = table.getTableName() + "(" + table.getSourceTable() + ") → " + target + "：" + e.getMessage();
                errors.add(msg);
                log.warn("多表单表失败: {}", msg);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accessMode", "MULTI");
        out.put("taskId", task.getId());
        out.put("tableCount", results.size());
        out.put("collectedRows", totalRows);
        out.put("items", results);
        out.put("errors", errors);
        if (results.isEmpty()) {
            task.setStatus("FAILED");
            task.setCollectedRows(0L);
            task.setLastRunAt(LocalDateTime.now());
            String detail = String.join("；", errors);
            task.setLastRunMessage("汇聚失败");
            task.setErrorDetail(detail.length() > 1000 ? detail.substring(0, 1000) : detail);
            taskMapper.updateById(task);
            throw new BusinessException(502, "多表汇聚全部失败：" + detail);
        }
        if (!errors.isEmpty()) {
            task.setStatus("FAILED");
            task.setCollectedRows(totalRows);
            task.setLastRunAt(LocalDateTime.now());
            String detail = "成功 " + results.size() + " 张，失败 " + errors.size() + " 张。" + String.join("；", errors);
            task.setLastRunMessage("部分成功");
            task.setErrorDetail(detail.length() > 1000 ? detail.substring(0, 1000) : detail);
            taskMapper.updateById(task);
            out.put("status", "PARTIAL");
            out.put("message", detail);
            return out;
        }
        task.setStatus("SUCCESS");
        task.setCollectedRows(totalRows);
        task.setLastRunAt(LocalDateTime.now());
        task.setLastRunMessage("多表汇聚完成 tables=" + results.size() + " rows=" + totalRows);
        task.setErrorDetail(null);
        taskMapper.updateById(task);
        out.put("status", "SUCCESS");
        return out;
    }

    private Map<String, Object> runSql(UserPrincipal operator, IngIngestTask task, JsonNode cfg) {
        JsonNode sql = cfg.path("sql");
        Long sourceId = longNode(sql, "sourceId");
        if (sourceId == null) {
            sourceId = task.getSourceId();
        }
        if (sourceId == null) {
            throw new BusinessException(400, "条件接入需指定数据源");
        }
        IngDataSource ds = dataSourceMapper.selectById(sourceId);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        String rawSql = text(sql, "selectSql", "");
        String resolved = resolveSqlParams(rawSql, sql.path("paramBindings"));
        kettleCollectService.validateSelectSql(resolved);
        String target = text(sql, "targetTable", task.getTargetTable() == null ? "" : task.getTargetTable());
        if (target.isBlank()) {
            target = "ods_sql_" + task.getId();
        }
        List<CollectCopyRequest.FieldPair> fields = readMappingPairs(cfg.path("mapping"));
        if (fields.isEmpty()) {
            throw new BusinessException(400, "条件接入需配置字段映射（可由预览后保存）");
        }
        boolean truncate = !"INCREMENTAL".equalsIgnoreCase(firstNonBlank(task.getWriteMode(), text(cfg, "writeMode", "FULL")));
        CollectCopyRequest req = new CollectCopyRequest();
        req.setSourceId(sourceId);
        req.setSelectSql(resolved);
        req.setOdsTable(target);
        req.setTruncate(truncate);
        req.setLedger(new CollectCopyRequest.IngIngestTaskLedger(task.getId()));
        req.getFields().addAll(fields);
        Map<String, Object> result = kettleCollectService.executeCopy(operator, req);
        result.put("accessMode", "SQL");
        result.put("resolvedSql", resolved);
        return result;
    }

    private String resolvePhysicalSource(IngDataTable table, JsonNode single) {
        String mode = text(single, "sourceTableMode", "FIXED");
        if ("PREFIX_DATE".equalsIgnoreCase(mode)) {
            String prefix = text(single, "tablePrefix", "");
            String pattern = text(single, "datePattern", "yyyyMMdd");
            int offset = single.path("dateOffsetDays").isMissingNode() ? -1 : single.path("dateOffsetDays").asInt(-1);
            LocalDate day = LocalDate.now().plusDays(offset);
            DateTimeFormatter fmt;
            try {
                fmt = DateTimeFormatter.ofPattern(pattern);
            } catch (Exception e) {
                throw new BusinessException(400, "日期格式无效: " + pattern);
            }
            String name = prefix + day.format(fmt);
            if (sanitizeIdent(name).isBlank()) {
                throw new BusinessException(400, "分表物理名无效");
            }
            return name;
        }
        if (table.getSourceTable() == null || table.getSourceTable().isBlank()) {
            throw new BusinessException(400, "登记表缺少源物理表名");
        }
        return table.getSourceTable();
    }

    private List<CollectCopyRequest.FieldPair> buildFields(Long tableId, JsonNode mapping) {
        List<CollectCopyRequest.FieldPair> fromCfg = readMappingPairs(mapping);
        if (!fromCfg.isEmpty()) {
            // 补齐类型
            Map<String, IngDataColumn> byCode = new LinkedHashMap<>();
            for (IngDataColumn c : dataColumnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                    .eq(IngDataColumn::getTableId, tableId))) {
                byCode.put(c.getColumnCode(), c);
            }
            for (CollectCopyRequest.FieldPair p : fromCfg) {
                IngDataColumn c = byCode.get(p.getSource());
                if (c != null) {
                    if (p.getDataType() == null) p.setDataType(c.getDataType());
                    if (p.getLength() == null) p.setLength(c.getLengthVal());
                }
            }
            return fromCfg;
        }
        List<CollectCopyRequest.FieldPair> pairs = new ArrayList<>();
        for (IngDataColumn c : dataColumnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId).orderByAsc(IngDataColumn::getSortOrder))) {
            pairs.add(new CollectCopyRequest.FieldPair(c.getColumnCode(), c.getColumnCode(), c.getDataType(), c.getLengthVal()));
        }
        return pairs;
    }

    private List<CollectCopyRequest.FieldPair> readMappingPairs(JsonNode mapping) {
        List<CollectCopyRequest.FieldPair> pairs = new ArrayList<>();
        if (mapping == null || mapping.isMissingNode()) {
            return pairs;
        }
        JsonNode arr = mapping.path("pairs");
        if (!arr.isArray()) {
            return pairs;
        }
        for (JsonNode n : arr) {
            String source = text(n, "source", "");
            String target = text(n, "target", source);
            if (source.isBlank()) continue;
            pairs.add(new CollectCopyRequest.FieldPair(source, target.isBlank() ? source : target,
                    text(n, "dataType", "VARCHAR"), intNode(n, "length")));
        }
        return pairs;
    }

    private String resolveSqlParams(String sql, JsonNode bindings) {
        if (sql == null) return "";
        Matcher m = PARAM.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String raw = bindings != null && bindings.has(key) ? bindings.get(key).asText("") : "";
            String replacement = resolveParamValue(raw);
            m.appendReplacement(sb, Matcher.quoteReplacement("'" + escapeLiteral(replacement) + "'"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String resolveParamValue(String expr) {
        if (expr == null || expr.isBlank()) {
            return LocalDate.now().minusDays(1).toString();
        }
        if (expr.toUpperCase(Locale.ROOT).startsWith("DATE_OFFSET:")) {
            int days = Integer.parseInt(expr.substring("DATE_OFFSET:".length()).trim());
            return LocalDate.now().plusDays(days).toString();
        }
        return expr;
    }

    private String suggestOds(IngDataTable table, String rule) {
        String source = table.getSourceTable() == null ? table.getTableCode() : table.getSourceTable();
        if (rule != null && rule.contains("{sourceTable}")) {
            return kettleCollectService.sanitizeOdsName(rule.replace("{sourceTable}", source == null ? "t" : source));
        }
        if ("ent_master".equalsIgnoreCase(source) || "TBL_ENTERPRISE".equalsIgnoreCase(table.getTableCode())) {
            return "ods_enterprise_base";
        }
        if ("proj_construction".equalsIgnoreCase(source) || "TBL_PROJECT".equalsIgnoreCase(table.getTableCode())) {
            return "ods_project_base";
        }
        return kettleCollectService.sanitizeOdsName("ods_" + (source == null ? "table" : source));
    }

    private JsonNode readConfig(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(400, "任务配置 JSON 无效");
        }
    }

    private static String text(JsonNode n, String field, String def) {
        if (n == null || n.isMissingNode()) return def;
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return def;
        String s = v.asText();
        return s == null || s.isBlank() ? def : s;
    }

    private static Long longNode(JsonNode n, String field) {
        if (n == null || n.isMissingNode()) return null;
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull() || v.asText("").isBlank()) return null;
        try {
            return v.isNumber() ? v.longValue() : Long.valueOf(v.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer intNode(JsonNode n, String field) {
        if (n == null || n.isMissingNode()) return null;
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        try {
            return v.isNumber() ? v.intValue() : Integer.valueOf(v.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Long> readLongList(JsonNode arr) {
        List<Long> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode n : arr) {
            try {
                out.add(n.isNumber() ? n.longValue() : Long.valueOf(n.asText()));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static String str(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    private static String sanitizeIdent(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_]", "");
    }

    private static String escapeLiteral(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
