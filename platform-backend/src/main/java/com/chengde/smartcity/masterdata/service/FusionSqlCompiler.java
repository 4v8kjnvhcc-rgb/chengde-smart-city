package com.chengde.smartcity.masterdata.service;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.service.SharePathSupportService.ColumnDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 声明式加工配置 → 白名单 SQL。禁止直接提交任意 SQL。
 */
@Service
public class FusionSqlCompiler {

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern MASK = Pattern.compile(
            "^MASK\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CASE_LEVEL = Pattern.compile(
            "^CASE_LEVEL\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FILTER_NOT_NULL = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*)\\s+IS\\s+NOT\\s+NULL$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FILTER_AND = Pattern.compile("\\s+AND\\s+", Pattern.CASE_INSENSITIVE);

    private final SharePathSupportService shareSupport;

    public FusionSqlCompiler(SharePathSupportService shareSupport) {
        this.shareSupport = shareSupport;
    }

    @SuppressWarnings("unchecked")
    public CompileResult compile(Map<String, Object> fusionSpec) {
        if (fusionSpec == null || fusionSpec.isEmpty()) {
            throw new BusinessException(400, "fusionSpec 必填");
        }
        String sourceTable = requireIdent(str(fusionSpec.get("sourceTable"), null), "sourceTable");
        String targetTable = requireIdent(str(fusionSpec.get("targetTable"), null), "targetTable");
        if (sourceTable.equalsIgnoreCase(targetTable)) {
            throw new BusinessException(400, "目标表不能与源表相同");
        }
        String writeMode = str(fusionSpec.get("writeMode"), "TRUNCATE_INSERT").toUpperCase(Locale.ROOT);
        if (!"TRUNCATE_INSERT".equals(writeMode)) {
            throw new BusinessException(400, "仅支持 writeMode=TRUNCATE_INSERT");
        }

        List<ColumnDef> sourceCols = shareSupport.inspectColumns(sourceTable);
        if (sourceCols.isEmpty()) {
            throw new BusinessException(404, "源表不存在或无字段: " + sourceTable);
        }
        Set<String> allowed = new LinkedHashSet<>();
        Map<String, String> typeByCol = new LinkedHashMap<>();
        for (ColumnDef c : sourceCols) {
            allowed.add(c.name().toLowerCase(Locale.ROOT));
            typeByCol.put(c.name().toLowerCase(Locale.ROOT), c.typeName());
        }

        Object selectObj = fusionSpec.get("select");
        if (!(selectObj instanceof List<?> selectList) || selectList.isEmpty()) {
            throw new BusinessException(400, "fusionSpec.select 至少包含一列映射");
        }

        List<SelectItem> items = new ArrayList<>();
        for (Object raw : selectList) {
            if (!(raw instanceof Map<?, ?> m)) {
                throw new BusinessException(400, "select 项必须是对象");
            }
            String expr = str(m.get("expr"), null);
            String as = str(m.get("as"), null);
            if (expr == null || as == null) {
                throw new BusinessException(400, "select 项需要 expr/as");
            }
            requireIdent(as, "as");
            CompiledExpr compiled = compileExpr(expr, allowed);
            items.add(new SelectItem(compiled.sql(), as, compiled.targetType(typeByCol)));
        }

        String whereSql = compileFilter(str(fusionSpec.get("filterSql"), null), allowed);

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS `").append(targetTable).append("` (\n");
        ddl.append("  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n");
        for (SelectItem item : items) {
            ddl.append("  `").append(item.as()).append("` ").append(item.sqlType()).append(" NULL,\n");
        }
        ddl.append("  fused_at DATETIME NULL\n");
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        StringBuilder insert = new StringBuilder();
        insert.append("INSERT INTO `").append(targetTable).append("` (");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) insert.append(", ");
            insert.append("`").append(items.get(i).as()).append("`");
        }
        insert.append(", fused_at) SELECT ");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) insert.append(", ");
            insert.append(items.get(i).exprSql()).append(" AS `").append(items.get(i).as()).append("`");
        }
        insert.append(", NOW() FROM `").append(sourceTable).append("`");
        if (whereSql != null) {
            insert.append(" WHERE ").append(whereSql);
        }

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("sourceTable", sourceTable);
        preview.put("targetTable", targetTable);
        preview.put("writeMode", writeMode);
        preview.put("columns", items.stream().map(i -> Map.of(
                "as", i.as(),
                "expr", i.exprSql(),
                "sqlType", i.sqlType()
        )).toList());
        preview.put("filterSql", whereSql);
        preview.put("ddlSql", ddl.toString());
        preview.put("insertSql", insert.toString());

        return new CompileResult(sourceTable, targetTable, writeMode, ddl.toString(), insert.toString(), preview);
    }

    private CompiledExpr compileExpr(String expr, Set<String> allowed) {
        String trimmed = expr.trim();
        Matcher mask = MASK.matcher(trimmed);
        if (mask.matches()) {
            String col = mask.group(1);
            requireAllowed(col, allowed);
            int left = Integer.parseInt(mask.group(2));
            int right = Integer.parseInt(mask.group(3));
            if (left < 0 || right < 0 || left + right > 64) {
                throw new BusinessException(400, "MASK 左右保留位数非法");
            }
            String sql = "CONCAT(LEFT(`" + col + "`," + left + "), '****', RIGHT(`" + col + "`," + right + "))";
            return new CompiledExpr(sql, "VARCHAR(64)");
        }
        Matcher level = CASE_LEVEL.matcher(trimmed);
        if (level.matches()) {
            String col = level.group(1);
            requireAllowed(col, allowed);
            String high = level.group(2);
            String mid = level.group(3);
            String sql = "CASE WHEN `" + col + "` >= " + high + " THEN 'HIGH' WHEN `" + col
                    + "` >= " + mid + " THEN 'MEDIUM' ELSE 'LOW' END";
            return new CompiledExpr(sql, "VARCHAR(16)");
        }
        if (IDENT.matcher(trimmed).matches()) {
            requireAllowed(trimmed, allowed);
            return new CompiledExpr("`" + trimmed + "`", null);
        }
        throw new BusinessException(400, "不支持的表达式（仅允许列名/MASK/CASE_LEVEL）: " + expr);
    }

    private String compileFilter(String filterSql, Set<String> allowed) {
        if (filterSql == null || filterSql.isBlank()) {
            return null;
        }
        String[] parts = FILTER_AND.split(filterSql.trim());
        List<String> compiled = new ArrayList<>();
        for (String part : parts) {
            Matcher m = FILTER_NOT_NULL.matcher(part.trim());
            if (!m.matches()) {
                throw new BusinessException(400, "filterSql 仅允许 `col IS NOT NULL` 及 AND 组合");
            }
            String col = m.group(1);
            requireAllowed(col, allowed);
            compiled.add("`" + col + "` IS NOT NULL AND TRIM(CAST(`" + col + "` AS CHAR)) <> ''");
        }
        return String.join(" AND ", compiled);
    }

    private void requireAllowed(String col, Set<String> allowed) {
        if (!allowed.contains(col.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(400, "列不在源表中: " + col);
        }
    }

    private static String requireIdent(String value, String field) {
        if (value == null || !IDENT.matcher(value).matches()) {
            throw new BusinessException(400, field + " 非法: " + value);
        }
        return value;
    }

    private static String str(Object v, String def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        return String.valueOf(v).trim();
    }

    public record CompileResult(String sourceTable, String targetTable, String writeMode,
                                String ddlSql, String insertSql, Map<String, Object> preview) {
    }

    private record SelectItem(String exprSql, String as, String sqlType) {
    }

    private record CompiledExpr(String sql, String forcedType) {
        String targetType(Map<String, String> typeByCol) {
            if (forcedType != null) return forcedType;
            // 原样列：尽量映射常见 MySQL 类型
            String raw = sql.replace("`", "");
            String t = typeByCol.getOrDefault(raw.toLowerCase(Locale.ROOT), "VARCHAR");
            return mapSqlType(t);
        }
    }

    private static String mapSqlType(String typeName) {
        if (typeName == null) return "VARCHAR(256)";
        String t = typeName.toUpperCase(Locale.ROOT);
        if (t.contains("BIGINT")) return "BIGINT";
        if (t.contains("INT")) return "INT";
        if (t.contains("DECIMAL") || t.contains("NUMERIC")) return "DECIMAL(18,2)";
        if (t.contains("DATE") && !t.contains("TIME")) return "DATE";
        if (t.contains("DATETIME") || t.contains("TIMESTAMP")) return "DATETIME";
        if (t.contains("TEXT")) return "TEXT";
        return "VARCHAR(256)";
    }
}
