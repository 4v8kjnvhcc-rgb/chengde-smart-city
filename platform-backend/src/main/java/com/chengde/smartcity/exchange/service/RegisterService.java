package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
import com.chengde.smartcity.exchange.entity.IngAssetTagBinding;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngDict;
import com.chengde.smartcity.exchange.entity.IngDictItem;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.entity.IngResourceRegistry;
import com.chengde.smartcity.exchange.mapper.IngAssetTagBindingMapper;
import com.chengde.smartcity.exchange.mapper.IngAssetTagMapper;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngDictItemMapper;
import com.chengde.smartcity.exchange.mapper.IngDictMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.exchange.mapper.IngResourceRegistryMapper;
import com.chengde.smartcity.integration.jdbc.JdbcProbeService;
import com.chengde.smartcity.masterdata.service.MetadataSubsystemService;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.AccessControlService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterService {

    private final IngDataTableMapper tableMapper;
    private final IngDataColumnMapper columnMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final IngAssetTagMapper tagMapper;
    private final IngAssetTagBindingMapper tagBindingMapper;
    private final IngProjectMapper projectMapper;
    private final IngIngestTaskMapper taskMapper;
    private final IngResourceRegistryMapper registryMapper;
    private final IngDictMapper dictMapper;
    private final IngDictItemMapper dictItemMapper;
    private final LineageService lineageService;
    private final AccessControlService accessControlService;
    private final JdbcProbeService jdbcProbeService;
    private final MetadataSubsystemService metadataSubsystemService;

    public RegisterService(IngDataTableMapper tableMapper, IngDataColumnMapper columnMapper,
                           IngDataSourceMapper dataSourceMapper,
                           IngAssetTagMapper tagMapper, IngAssetTagBindingMapper tagBindingMapper,
                           IngProjectMapper projectMapper,
                           IngIngestTaskMapper taskMapper, IngResourceRegistryMapper registryMapper,
                           IngDictMapper dictMapper, IngDictItemMapper dictItemMapper,
                           LineageService lineageService,
                           AccessControlService accessControlService,
                           JdbcProbeService jdbcProbeService,
                           MetadataSubsystemService metadataSubsystemService) {
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.tagMapper = tagMapper;
        this.tagBindingMapper = tagBindingMapper;
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.registryMapper = registryMapper;
        this.dictMapper = dictMapper;
        this.dictItemMapper = dictItemMapper;
        this.lineageService = lineageService;
        this.accessControlService = accessControlService;
        this.jdbcProbeService = jdbcProbeService;
        this.metadataSubsystemService = metadataSubsystemService;
    }

    public List<IngDataTable> listTables(UserPrincipal operator, Long sourceId) {
        Set<Long> allowed = accessControlService.effectiveTableIds(operator);
        if (allowed.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<IngDataTable> q = new LambdaQueryWrapper<IngDataTable>()
                .in(IngDataTable::getId, allowed)
                .orderByAsc(IngDataTable::getId);
        if (sourceId != null) {
            accessControlService.assertSourceAccess(operator, sourceId);
            q.eq(IngDataTable::getSourceId, sourceId);
        }
        return tableMapper.selectList(q);
    }

    public List<IngDataColumn> listColumns(Long tableId) {
        return columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId).orderByAsc(IngDataColumn::getSortOrder));
    }

    @Transactional
    public Long createTable(UserPrincipal operator, Map<String, Object> body) {
        Long sourceId = Long.valueOf(String.valueOf(required(body.get("sourceId"), "sourceId")));
        accessControlService.assertSourceAccess(operator, sourceId);
        IngDataSource ds = dataSourceMapper.selectById(sourceId);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        String modelingMode = str(body.get("modelingMode"), "FORWARD").toUpperCase(Locale.ROOT);
        String tableNameRaw = required(body.get("tableName"), "tableName").toString().trim();
        String tableName = "FORWARD".equals(modelingMode)
                ? JdbcProbeService.sanitizeIdent(tableNameRaw)
                : tableNameRaw;
        if (tableName.isBlank()) {
            throw new BusinessException(400, "表名不能为空");
        }

        IngDataTable dup = tableMapper.selectOne(new LambdaQueryWrapper<IngDataTable>()
                .eq(IngDataTable::getSourceId, sourceId)
                .and(w -> w.eq(IngDataTable::getTableName, tableName)
                        .or().eq(IngDataTable::getPhysicalTableName, tableName)
                        .or().eq(IngDataTable::getSourceTable, tableName))
                .last("LIMIT 1"));
        if (dup != null) {
            throw new BusinessException(409, "该数据源下已登记同名表：" + tableName);
        }

        String tableCode = str(body.get("tableCode"), null);
        if (tableCode == null || tableCode.isBlank()) {
            tableCode = "TBL_" + tableName.toUpperCase(Locale.ROOT);
            IngDataTable byCode = tableMapper.selectOne(new LambdaQueryWrapper<IngDataTable>()
                    .eq(IngDataTable::getTableCode, tableCode).last("LIMIT 1"));
            if (byCode != null) {
                tableCode = "TBL_" + tableName.toUpperCase(Locale.ROOT) + "_" + System.currentTimeMillis();
            }
        }

        IngDataTable t = new IngDataTable();
        t.setSourceId(sourceId);
        t.setTableCode(tableCode);
        t.setTableName(tableName);
        t.setPhysicalTableName(tableName);
        t.setSourceTable(tableName);
        t.setSourceSchema(ds.getSourceSchema());
        t.setModelingMode(modelingMode);
        t.setColumnCount(0);
        t.setStatus("ACTIVE");
        t.setCollectStatus("PENDING");
        t.setCreatedAt(LocalDateTime.now());
        try {
            tableMapper.insert(t);
        } catch (Exception e) {
            throw new BusinessException(500, "表登记写入失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }

        Long cnt = tableMapper.selectCount(new LambdaQueryWrapper<IngDataTable>().eq(IngDataTable::getSourceId, sourceId));
        ds.setTableCount(cnt == null ? 0 : cnt.intValue());
        dataSourceMapper.updateById(ds);
        return t.getId();
    }

    @Transactional
    public Long createColumn(UserPrincipal operator, Long tableId, Map<String, Object> body) {
        IngDataTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "表不存在");
        }
        accessControlService.assertSourceAccess(operator, table.getSourceId());
        String columnCode = JdbcProbeService.sanitizeIdent(required(body.get("columnCode"), "columnCode").toString());
        IngDataColumn exist = columnMapper.selectOne(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId)
                .eq(IngDataColumn::getColumnCode, columnCode)
                .last("LIMIT 1"));
        if (exist != null) {
            throw new BusinessException(409, "字段编码已存在：" + columnCode);
        }
        IngDataColumn col = new IngDataColumn();
        col.setTableId(tableId);
        col.setColumnCode(columnCode);
        col.setColumnName(required(body.get("columnName"), "columnName").toString().trim());
        col.setDataType(str(body.get("dataType"), "VARCHAR(64)"));
        col.setNullableFlag(intVal(body.get("nullableFlag"), 1));
        col.setSemanticDesc(str(body.get("semanticDesc"), null));
        col.setLengthVal(intVal(body.get("lengthVal"), null));
        col.setComponentType(str(body.get("componentType"), "INPUT"));
        col.setRequiredTip(str(body.get("requiredTip"), null));
        col.setBuiltInFlag(Integer.valueOf(1).equals(intVal(body.get("builtInFlag"), 0)) ? 1 : 0);
        int maxSort = columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId)).stream()
                .mapToInt(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()).max().orElse(0);
        col.setSortOrder(maxSort + 1);
        try {
            columnMapper.insert(col);
        } catch (Exception e) {
            throw new BusinessException(500, "字段登记写入失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        long colCnt = columnMapper.selectCount(new LambdaQueryWrapper<IngDataColumn>().eq(IngDataColumn::getTableId, tableId));
        table.setColumnCount((int) colCnt);
        tableMapper.updateById(table);
        return col.getId();
    }

    /**
     * 正向建模完成：在源库创建物理表（JDBC 数据源），元数据已在 createTable/createColumn 落库。
     */
    @Transactional
    public Map<String, Object> finalizeForwardTable(UserPrincipal operator, Long tableId) {
        IngDataTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "表不存在");
        }
        accessControlService.assertSourceAccess(operator, table.getSourceId());
        if (!"FORWARD".equalsIgnoreCase(table.getModelingMode())) {
            throw new BusinessException(400, "仅正向建模表可执行物理建表");
        }
        List<IngDataColumn> columns = listColumns(tableId);
        if (columns.isEmpty()) {
            throw new BusinessException(400, "请至少登记一个字段后再完成建表");
        }
        IngDataSource ds = dataSourceMapper.selectById(table.getSourceId());
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableId", tableId);
        out.put("tableName", table.getTableName());
        out.put("columnCount", columns.size());
        out.put("sourceType", ds.getSourceType());

        String type = ds.getSourceType() == null ? "" : ds.getSourceType().toUpperCase(Locale.ROOT);
        if (!"MYSQL".equals(type) && !"ORACLE".equals(type)) {
            out.put("physicalCreated", false);
            out.put("message", "当前数据源类型仅完成平台元数据登记；物理建表目前支持 MySQL/Oracle");
            table.setColumnCount(columns.size());
            table.setCollectStatus("PENDING");
            tableMapper.updateById(table);
            return out;
        }

        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        String physical = JdbcProbeService.sanitizeIdent(
                table.getPhysicalTableName() != null ? table.getPhysicalTableName() : table.getTableName());
        if (jdbcProbeService.tableExists(conn, physical)) {
            throw new BusinessException(409, "源库已存在同名物理表：" + physical + "，请更换表名或改用逆向登记");
        }
        String ddl = buildForwardCreateDdl(type, physical, columns);
        jdbcProbeService.executeDdl(conn, ddl);

        table.setPhysicalTableName(physical);
        table.setSourceTable(physical);
        table.setSourceSchema(conn.database);
        table.setDdlSql(ddl);
        table.setColumnCount(columns.size());
        table.setCollectStatus("PENDING");
        tableMapper.updateById(table);

        out.put("physicalCreated", true);
        out.put("physicalTable", physical);
        out.put("schema", conn.database);
        out.put("ddlSql", ddl);
        out.put("message", "已在源库创建物理表，并完成平台登记");
        return out;
    }

    private String buildForwardCreateDdl(String sourceType, String table, List<IngDataColumn> columns) {
        StringBuilder ddl = new StringBuilder();
        if ("ORACLE".equals(sourceType)) {
            ddl.append("CREATE TABLE ").append(table).append(" (\n");
            for (int i = 0; i < columns.size(); i++) {
                IngDataColumn c = columns.get(i);
                String code = JdbcProbeService.sanitizeIdent(c.getColumnCode());
                ddl.append("  ").append(code).append(' ').append(mapOracleType(c.getDataType()));
                if (c.getNullableFlag() != null && c.getNullableFlag() == 0) {
                    ddl.append(" NOT NULL");
                }
                if (i < columns.size() - 1) ddl.append(',');
                ddl.append('\n');
            }
            ddl.append(')');
            return ddl.toString();
        }
        // MySQL / Postgres 风格（平台主路径 MySQL）
        String quote = "`";
        ddl.append("CREATE TABLE ").append(quote).append(table).append(quote).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            IngDataColumn c = columns.get(i);
            String code = JdbcProbeService.sanitizeIdent(c.getColumnCode());
            ddl.append("  ").append(quote).append(code).append(quote).append(' ')
                    .append(mapMysqlType(c.getDataType(), c.getLengthVal()));
            if (c.getNullableFlag() != null && c.getNullableFlag() == 0) {
                ddl.append(" NOT NULL");
            }
            if (c.getColumnName() != null && !c.getColumnName().isBlank()) {
                ddl.append(" COMMENT '").append(c.getColumnName().replace("'", "")).append('\'');
            }
            if (i < columns.size() - 1) ddl.append(',');
            ddl.append('\n');
        }
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正向建模登记表'");
        return ddl.toString();
    }

    private String mapMysqlType(String dataType, Integer length) {
        String t = dataType == null ? "" : dataType.toUpperCase(Locale.ROOT);
        int len = length == null || length <= 0 ? 64 : Math.min(length, 4000);
        if (t.contains("TEXT") || t.contains("BLOB") || t.contains("CLOB") || t.contains("JSON")) return "TEXT";
        if (t.contains("CHAR")) {
            Matcher m = Pattern.compile("\\((\\d+)\\)").matcher(t);
            if (m.find()) return "VARCHAR(" + m.group(1) + ")";
            return "VARCHAR(" + len + ")";
        }
        if (t.contains("BIGINT")) return "BIGINT";
        if (t.contains("INT")) return "INT";
        if (t.contains("DECIMAL") || t.contains("NUMERIC")) return "DECIMAL(18,2)";
        if (t.contains("DOUBLE") || t.contains("FLOAT")) return "DOUBLE";
        if (t.contains("DATETIME") || t.contains("TIMESTAMP")) return "DATETIME";
        if (t.contains("DATE")) return "DATE";
        if (t.contains("BOOL")) return "TINYINT(1)";
        return t.isBlank() ? "VARCHAR(64)" : dataType;
    }

    private String mapOracleType(String dataType) {
        String t = dataType == null ? "" : dataType.toUpperCase(Locale.ROOT);
        if (t.contains("TEXT") || t.contains("CLOB")) return "CLOB";
        if (t.contains("CHAR")) return t.contains("(") ? t.replace("VARCHAR", "VARCHAR2") : "VARCHAR2(64)";
        if (t.contains("INT") || t.contains("BIGINT")) return "NUMBER(19)";
        if (t.contains("DECIMAL") || t.contains("NUMERIC")) return "NUMBER(18,2)";
        if (t.contains("DATETIME") || t.contains("TIMESTAMP")) return "TIMESTAMP";
        if (t.contains("DATE")) return "DATE";
        return "VARCHAR2(256)";
    }

    @Transactional
    public void updateColumn(UserPrincipal operator, Long columnId, Map<String, Object> body) {
        IngDataColumn col = columnMapper.selectById(columnId);
        if (col == null) {
            throw new BusinessException(404, "数据项不存在");
        }
        IngDataTable table = tableMapper.selectById(col.getTableId());
        if (table != null) {
            accessControlService.assertSourceAccess(operator, table.getSourceId());
        }
        if (col.getBuiltInFlag() != null && col.getBuiltInFlag() == 1) {
            throw new BusinessException(400, "系统内置属性不可编辑");
        }
        Integer newBuiltIn = body.containsKey("builtInFlag")
                ? intVal(body.get("builtInFlag"), 0)
                : (col.getBuiltInFlag() == null ? 0 : col.getBuiltInFlag());
        boolean nowBuiltIn = newBuiltIn != null && newBuiltIn == 1;
        if (body.containsKey("columnName")) col.setColumnName(body.get("columnName").toString());
        if (body.containsKey("dataType")) col.setDataType(body.get("dataType").toString());
        if (body.containsKey("nullableFlag")) col.setNullableFlag(intVal(body.get("nullableFlag"), col.getNullableFlag()));
        if (body.containsKey("semanticDesc")) col.setSemanticDesc(str(body.get("semanticDesc"), null));
        if (body.containsKey("lengthVal")) col.setLengthVal(intVal(body.get("lengthVal"), null));
        if (body.containsKey("componentType")) col.setComponentType(body.get("componentType").toString());
        if (body.containsKey("requiredTip")) col.setRequiredTip(str(body.get("requiredTip"), null));
        col.setBuiltInFlag(nowBuiltIn ? 1 : 0);
        columnMapper.updateById(col);
        // 覆盖元数据维护中对应属性，原信息不可恢复
        metadataSubsystemService.overwriteColumnMetadataFromIngest(
                col.getTableId(),
                col.getColumnCode(),
                col.getColumnName(),
                col.getDataType(),
                col.getSemanticDesc());
    }

    @Transactional
    public Map<String, Object> importMetadata(UserPrincipal operator, Map<String, Object> body) {
        Long sourceId = Long.valueOf(String.valueOf(required(body.get("sourceId"), "sourceId")));
        String csv = required(body.get("csvText"), "csvText").toString();
        String[] lines = csv.split("\\r?\\n");
        int imported = 0;
        Long currentTableId = null;
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#") || line.toLowerCase().startsWith("table_name")) continue;
            String[] parts = line.split(",", -1);
            if (parts.length < 4) continue;
            String tableName = parts[0].trim();
            if (!tableName.isEmpty()) {
                IngDataTable existing = tableMapper.selectOne(new LambdaQueryWrapper<IngDataTable>()
                        .eq(IngDataTable::getSourceId, sourceId).eq(IngDataTable::getTableName, tableName).last("LIMIT 1"));
                if (existing == null) {
                    Map<String, Object> tBody = new HashMap<>();
                    tBody.put("sourceId", sourceId);
                    tBody.put("tableName", tableName);
                    tBody.put("modelingMode", "REVERSE");
                    currentTableId = createTable(operator, tBody);
                } else {
                    currentTableId = existing.getId();
                }
            }
            if (currentTableId == null) continue;
            Map<String, Object> cBody = new HashMap<>();
            cBody.put("columnCode", parts[1].trim());
            cBody.put("columnName", parts[2].trim());
            cBody.put("dataType", parts[3].trim());
            if (parts.length > 4) cBody.put("nullableFlag", "0".equals(parts[4].trim()) ? 0 : 1);
            if (parts.length > 5 && !parts[5].isBlank()) cBody.put("semanticDesc", parts[5].trim());
            createColumn(operator, currentTableId, cBody);
            imported++;
        }
        return Map.of("importedRows", imported);
    }

    public String metadataTemplateCsv() {
        return "table_name,column_code,column_name,data_type,nullable,semantic_desc\n"
                + "示例表,CODE,编码,VARCHAR(64),0,业务主键\n"
                + ",NAME,名称,VARCHAR(256),1,业务名称\n";
    }

    public Map<String, Object> lineageGraph(String projectScope) {
        return lineageService.panorama(null, null, null);
    }

    public Map<String, Object> lineageDrill(String nodeId) {
        return lineageService.drill(nodeId);
    }

    public Map<String, Object> fieldLineage(String tableNode) {
        return lineageService.fieldLineage(tableNode);
    }

    public Map<String, Object> assetReport() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectCount", projectMapper.selectCount(null));
        out.put("tableCount", tableMapper.selectCount(null));
        out.put("taskCount", taskMapper.selectCount(null));
        out.put("scriptCount", taskMapper.selectCount(new LambdaQueryWrapper<IngIngestTask>().eq(IngIngestTask::getStatus, "ACTIVE")));
        out.put("workflowCount", registryMapper.selectCount(null));
        out.put("storageGb", tableMapper.selectCount(null) * 1.2);
        out.put("publishedRegistries", registryMapper.selectCount(
                new LambdaQueryWrapper<IngResourceRegistry>().eq(IngResourceRegistry::getPublishStatus, "PUBLISHED")));
        List<Map<String, Object>> topProjects = projectMapper.selectList(
                new LambdaQueryWrapper<IngProject>().last("LIMIT 5")).stream()
                .map(p -> Map.<String, Object>of("projectName", p.getProjectName(), "tableCount", 2))
                .collect(Collectors.toList());
        List<Map<String, Object>> topTables = tableMapper.selectList(
                new LambdaQueryWrapper<IngDataTable>().orderByDesc(IngDataTable::getColumnCount).last("LIMIT 5"))
                .stream()
                .map(t -> Map.<String, Object>of("tableName", t.getTableName(), "columnCount", t.getColumnCount()))
                .collect(Collectors.toList());
        List<Map<String, Object>> topTasks = taskMapper.selectList(
                new LambdaQueryWrapper<IngIngestTask>().last("LIMIT 5")).stream()
                .map(t -> Map.<String, Object>of("taskName", t.getTaskName(), "status", t.getStatus()))
                .collect(Collectors.toList());
        out.put("topProjects", topProjects);
        out.put("topTables", topTables);
        out.put("topTasks", topTasks);
        out.put("tableTrend", List.of(
                Map.of("month", "01月", "count", 2),
                Map.of("month", "02月", "count", 4),
                Map.of("month", "03月", "count", 5),
                Map.of("month", "04月", "count", tableMapper.selectCount(null))
        ));
        out.put("storageTrend", List.of(
                Map.of("month", "01月", "gb", 1.2),
                Map.of("month", "02月", "gb", 2.4),
                Map.of("month", "03月", "gb", 3.6),
                Map.of("month", "04月", "gb", tableMapper.selectCount(null) * 1.2)
        ));
        return out;
    }

    public List<IngAssetTag> listTags() {
        return tagMapper.selectList(new LambdaQueryWrapper<IngAssetTag>()
                .orderByAsc(IngAssetTag::getTagSource)
                .orderByAsc(IngAssetTag::getStdCode)
                .orderByAsc(IngAssetTag::getId));
    }

    /** tree=true 时返回标准类目树；否则平铺全部标签。 */
    public Object listTags(boolean tree) {
        List<IngAssetTag> all = listTags();
        if (!tree) {
            return all;
        }
        List<IngAssetTag> roots = new ArrayList<>();
        Map<Long, IngAssetTag> byId = new LinkedHashMap<>();
        for (IngAssetTag t : all) {
            if (!"STANDARD".equals(t.getTagSource())) {
                continue;
            }
            t.setChildren(new ArrayList<>());
            byId.put(t.getId(), t);
        }
        for (IngAssetTag t : byId.values()) {
            if (t.getParentId() == null || !byId.containsKey(t.getParentId())) {
                roots.add(t);
            } else {
                byId.get(t.getParentId()).getChildren().add(t);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("standardTree", roots);
        out.put("customTags", all.stream().filter(t -> !"STANDARD".equals(t.getTagSource())).toList());
        return out;
    }

    @Transactional
    public Long createTag(UserPrincipal operator, Map<String, Object> body) {
        IngAssetTag tag = new IngAssetTag();
        tag.setTagCode(str(body.get("tagCode"), "TAG_" + System.currentTimeMillis()));
        tag.setTagName(required(body.get("tagName"), "tagName").toString());
        tag.setRuleExpr(str(body.get("ruleExpr"), ""));
        tag.setTagDesc(str(body.get("tagDesc"), ""));
        tag.setHitCount(0);
        tag.setStatus("ACTIVE");
        tag.setTagSource("CUSTOM");
        tag.setLevel(null);
        tag.setParentId(null);
        tag.setStdCode(null);
        tagMapper.insert(tag);
        return tag.getId();
    }

    @Transactional
    public void updateTag(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngAssetTag tag = tagMapper.selectById(id);
        if (tag == null) throw new BusinessException(404, "标签不存在");
        if ("STANDARD".equals(tag.getTagSource())) {
            if (body.containsKey("ruleExpr")) {
                tag.setRuleExpr(body.get("ruleExpr") == null ? "" : body.get("ruleExpr").toString());
            }
            tagMapper.updateById(tag);
            return;
        }
        if (body.containsKey("tagName")) tag.setTagName(body.get("tagName").toString());
        if (body.containsKey("ruleExpr")) tag.setRuleExpr(body.get("ruleExpr").toString());
        if (body.containsKey("tagDesc")) tag.setTagDesc(body.get("tagDesc").toString());
        tagMapper.updateById(tag);
    }

    @Transactional
    public Map<String, Object> matchTags(UserPrincipal operator) {
        List<IngAssetTag> tags = listTags();
        int totalHits = 0;
        for (IngAssetTag tag : tags) {
            int hits = tag.getRuleExpr() == null ? 0 : 32 + tag.getRuleExpr().length();
            tag.setHitCount(hits);
            tagMapper.updateById(tag);
            totalHits += hits;
        }
        return Map.of("matchedTags", tags.size(), "totalHits", totalHits);
    }

    public List<IngAssetTagBinding> listTagBindings(String assetType, Long assetId) {
        if (assetType == null || assetType.isBlank() || assetId == null) {
            throw new BusinessException(400, "assetType/assetId required");
        }
        String type = assetType.trim().toUpperCase();
        List<IngAssetTagBinding> list = tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getAssetType, type)
                .eq(IngAssetTagBinding::getAssetId, assetId)
                .orderByAsc(IngAssetTagBinding::getId));
        enrichBindings(list);
        return list;
    }

    public List<IngAssetTagBinding> listTagBindingsByTag(Long tagId) {
        if (tagId == null) throw new BusinessException(400, "tagId required");
        List<IngAssetTagBinding> list = tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getTagId, tagId)
                .orderByAsc(IngAssetTagBinding::getId));
        enrichBindings(list);
        return list;
    }

    /**
     * 打开「匹配标签」抽屉用：一次返回字段列表 + 表/字段已挂标签，避免 N+1。
     */
    public Map<String, Object> tagMatchContext(Long tableId) {
        if (tableId == null) throw new BusinessException(400, "tableId required");
        if (tableMapper.selectById(tableId) == null) {
            throw new BusinessException(404, "数据表不存在");
        }
        List<IngDataColumn> columns = listColumns(tableId);
        List<Long> tableTagIds = tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                        .eq(IngAssetTagBinding::getAssetType, "TABLE")
                        .eq(IngAssetTagBinding::getAssetId, tableId)
                        .orderByAsc(IngAssetTagBinding::getId))
                .stream().map(IngAssetTagBinding::getTagId).toList();

        Map<Long, List<Long>> columnTagMap = new LinkedHashMap<>();
        for (IngDataColumn col : columns) {
            columnTagMap.put(col.getId(), new ArrayList<>());
        }
        List<Long> colIds = columns.stream().map(IngDataColumn::getId).toList();
        if (!colIds.isEmpty()) {
            List<IngAssetTagBinding> colBinds = tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                    .eq(IngAssetTagBinding::getAssetType, "COLUMN")
                    .in(IngAssetTagBinding::getAssetId, colIds)
                    .orderByAsc(IngAssetTagBinding::getId));
            for (IngAssetTagBinding b : colBinds) {
                columnTagMap.computeIfAbsent(b.getAssetId(), k -> new ArrayList<>()).add(b.getTagId());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableId", tableId);
        out.put("columns", columns);
        out.put("tableTagIds", tableTagIds);
        out.put("columnTagMap", columnTagMap);
        return out;
    }

    /**
     * 根据该标签已手工挂标的表/字段，逆向归纳识别规则建议（不落库，需用户确认后写入 ruleExpr）。
     */
    public Map<String, Object> suggestRuleFromBindings(Long tagId) {
        IngAssetTag tag = tagMapper.selectById(tagId);
        if (tag == null) throw new BusinessException(404, "标签不存在");
        if (tag.getLevel() != null && tag.getLevel() == 1) {
            throw new BusinessException(400, "一级类目仅作分组，请对二级类目或扩展标签生成规则");
        }
        List<IngAssetTagBinding> bindings = listTagBindingsByTag(tagId);
        List<String> tableCodes = new ArrayList<>();
        List<String> tableNames = new ArrayList<>();
        List<String> columnCodes = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        for (IngAssetTagBinding b : bindings) {
            if ("TABLE".equals(b.getAssetType())) {
                IngDataTable t = tableMapper.selectById(b.getAssetId());
                if (t != null) {
                    if (t.getTableCode() != null && !t.getTableCode().isBlank()) tableCodes.add(t.getTableCode().trim());
                    if (t.getTableName() != null && !t.getTableName().isBlank()) tableNames.add(t.getTableName().trim());
                }
            } else if ("COLUMN".equals(b.getAssetType())) {
                IngDataColumn c = columnMapper.selectById(b.getAssetId());
                if (c != null) {
                    if (c.getColumnCode() != null && !c.getColumnCode().isBlank()) columnCodes.add(c.getColumnCode().trim());
                    if (c.getColumnName() != null && !c.getColumnName().isBlank()) columnNames.add(c.getColumnName().trim());
                }
            }
        }
        Set<String> tableTokens = extractTokens(tableCodes, tableNames);
        Set<String> uniqueColCodes = new LinkedHashSet<>(columnCodes);
        List<String> parts = new ArrayList<>();
        if (!tableTokens.isEmpty()) {
            String tableRule = tableTokens.stream()
                    .limit(8)
                    .map(tok -> "table_name LIKE %" + tok + "%")
                    .collect(Collectors.joining(" OR "));
            parts.add(tableRule);
        }
        if (!uniqueColCodes.isEmpty()) {
            String cols = uniqueColCodes.stream().limit(20).collect(Collectors.joining(","));
            parts.add("column_name IN (" + cols + ")");
        }
        String suggested = String.join("; ", parts);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tagId", tagId);
        out.put("tagName", tag.getTagName());
        out.put("stdCode", tag.getStdCode());
        out.put("currentRule", tag.getRuleExpr() == null ? "" : tag.getRuleExpr());
        out.put("suggestedRule", suggested);
        out.put("bindingCount", bindings.size());
        out.put("tableSamples", distinctLimit(mergeLabels(tableCodes, tableNames), 12));
        out.put("columnSamples", distinctLimit(mergeLabels(columnCodes, columnNames), 12));
        out.put("tableTokenCount", tableTokens.size());
        out.put("columnCodeCount", uniqueColCodes.size());
        if (bindings.isEmpty()) {
            out.put("message", "该标签尚无挂标资产，请先在数据资产标签登记中手工匹配");
        } else if (suggested.isBlank()) {
            out.put("message", "已有挂标但未能提取有效关键词，请手工填写规则");
        } else {
            out.put("message", "已根据 " + tableSamplesSize(tableCodes, tableNames) + " 张表、"
                    + uniqueColCodes.size() + " 个字段归纳规则建议，确认后可写入识别规则");
        }
        return out;
    }

    @Transactional
    public Map<String, Object> applySuggestedRule(UserPrincipal operator, Long tagId, Map<String, Object> body) {
        Map<String, Object> suggestion = suggestRuleFromBindings(tagId);
        String rule = str(body.get("ruleExpr"), null);
        if (rule == null || rule.isBlank()) {
            rule = String.valueOf(suggestion.get("suggestedRule"));
        }
        if (rule == null || rule.isBlank()) {
            throw new BusinessException(400, "无可用规则可写入");
        }
        Map<String, Object> update = new HashMap<>();
        update.put("ruleExpr", rule.trim());
        updateTag(operator, tagId, update);
        suggestion.put("appliedRule", rule.trim());
        suggestion.put("currentRule", rule.trim());
        suggestion.put("message", "识别规则已更新");
        return suggestion;
    }

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-Z0-9\\u4e00-\\u9fff]+");
    private static final Set<String> STOP_TOKENS = Set.of(
            "tbl", "table", "col", "column", "data", "info", "base", "master",
            "表", "信息", "数据", "基础", "综合", "其他", "管理");

    private Set<String> extractTokens(List<String> codes, List<String> names) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        List<String> all = new ArrayList<>();
        all.addAll(codes);
        all.addAll(names);
        for (String raw : all) {
            if (raw == null || raw.isBlank()) continue;
            String[] parts = TOKEN_SPLIT.split(raw.trim());
            Set<String> seenInItem = new LinkedHashSet<>();
            for (String p : parts) {
                if (p == null || p.isBlank()) continue;
                String tok = p.toLowerCase(Locale.ROOT);
                if (tok.length() < 2) continue;
                if (tok.matches("\\d+")) continue;
                if (STOP_TOKENS.contains(tok)) continue;
                seenInItem.add(tok);
            }
            // 中文短名整体也可作为 token
            String compact = raw.replaceAll("\\s+", "");
            if (compact.matches("[\\u4e00-\\u9fff]{2,8}")) {
                seenInItem.add(compact);
            }
            for (String tok : seenInItem) {
                freq.merge(tok, 1, Integer::sum);
            }
        }
        if (freq.isEmpty()) return Set.of();
        int threshold = all.size() >= 2 ? 2 : 1;
        Set<String> preferred = freq.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    if (c != 0) return c;
                    return Integer.compare(b.getKey().length(), a.getKey().length());
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!preferred.isEmpty()) return preferred;
        return freq.keySet().stream().limit(8).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> mergeLabels(List<String> codes, List<String> names) {
        List<String> out = new ArrayList<>();
        int n = Math.max(codes.size(), names.size());
        for (int i = 0; i < n; i++) {
            String c = i < codes.size() ? codes.get(i) : null;
            String name = i < names.size() ? names.get(i) : null;
            if (c != null && name != null && !c.equals(name)) out.add(c + "/" + name);
            else if (c != null) out.add(c);
            else if (name != null) out.add(name);
        }
        return out;
    }

    private List<String> distinctLimit(List<String> items, int limit) {
        return items.stream().filter(Objects::nonNull).distinct().limit(limit).toList();
    }

    private int tableSamplesSize(List<String> codes, List<String> names) {
        Set<String> s = new LinkedHashSet<>();
        s.addAll(codes);
        s.addAll(names);
        return s.size();
    }

    @Transactional
    public Long bindTag(UserPrincipal operator, Map<String, Object> body) {
        Long tagId = longVal(body.get("tagId"));
        String assetType = str(body.get("assetType"), "").trim().toUpperCase();
        Long assetId = longVal(body.get("assetId"));
        if (tagId == null || assetId == null || assetType.isBlank()) {
            throw new BusinessException(400, "tagId/assetType/assetId required");
        }
        if (!Set.of("TABLE", "COLUMN").contains(assetType)) {
            throw new BusinessException(400, "assetType 仅支持 TABLE 或 COLUMN");
        }
        IngAssetTag tag = tagMapper.selectById(tagId);
        if (tag == null) throw new BusinessException(404, "标签不存在");
        if (tag.getLevel() != null && tag.getLevel() == 1) {
            throw new BusinessException(400, "请选择二级类目挂标，一级类目仅作分组");
        }
        assertAssetExists(assetType, assetId);
        IngAssetTagBinding exists = tagBindingMapper.selectOne(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getTagId, tagId)
                .eq(IngAssetTagBinding::getAssetType, assetType)
                .eq(IngAssetTagBinding::getAssetId, assetId)
                .last("LIMIT 1"));
        if (exists != null) {
            return exists.getId();
        }
        IngAssetTagBinding b = new IngAssetTagBinding();
        b.setTagId(tagId);
        b.setAssetType(assetType);
        b.setAssetId(assetId);
        b.setCreatedAt(LocalDateTime.now());
        tagBindingMapper.insert(b);
        return b.getId();
    }

    @Transactional
    public void unbindTag(UserPrincipal operator, Map<String, Object> body) {
        Long tagId = longVal(body.get("tagId"));
        String assetType = str(body.get("assetType"), "").trim().toUpperCase();
        Long assetId = longVal(body.get("assetId"));
        if (tagId == null || assetId == null || assetType.isBlank()) {
            throw new BusinessException(400, "tagId/assetType/assetId required");
        }
        tagBindingMapper.delete(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getTagId, tagId)
                .eq(IngAssetTagBinding::getAssetType, assetType)
                .eq(IngAssetTagBinding::getAssetId, assetId));
    }

    private void assertAssetExists(String assetType, Long assetId) {
        if ("TABLE".equals(assetType)) {
            if (tableMapper.selectById(assetId) == null) {
                throw new BusinessException(404, "数据表不存在");
            }
        } else if ("COLUMN".equals(assetType)) {
            if (columnMapper.selectById(assetId) == null) {
                throw new BusinessException(404, "字段/数据项不存在");
            }
        }
    }

    private void enrichBindings(List<IngAssetTagBinding> list) {
        for (IngAssetTagBinding b : list) {
            IngAssetTag tag = tagMapper.selectById(b.getTagId());
            if (tag != null) {
                b.setTagName(tag.getTagName());
                b.setStdCode(tag.getStdCode());
            }
            if ("TABLE".equals(b.getAssetType())) {
                IngDataTable t = tableMapper.selectById(b.getAssetId());
                if (t != null) {
                    b.setAssetLabel(Objects.toString(t.getTableName(), t.getTableCode()));
                }
            } else if ("COLUMN".equals(b.getAssetType())) {
                IngDataColumn c = columnMapper.selectById(b.getAssetId());
                if (c != null) {
                    b.setAssetLabel(Objects.toString(c.getColumnName(), c.getColumnCode()));
                }
            }
        }
    }

    private Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return Long.valueOf(String.valueOf(v));
    }

    public List<IngDict> listDicts(String keyword) {
        LambdaQueryWrapper<IngDict> q = new LambdaQueryWrapper<IngDict>().orderByAsc(IngDict::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(IngDict::getDictName, keyword)
                    .or().like(IngDict::getStandardNo, keyword)
                    .or().like(IngDict::getPublisher, keyword)
                    .or().like(IngDict::getDictCode, keyword));
        }
        List<IngDict> dicts = dictMapper.selectList(q);
        for (IngDict d : dicts) {
            long cnt = dictItemMapper.selectCount(new LambdaQueryWrapper<IngDictItem>().eq(IngDictItem::getDictId, d.getId()));
            d.setItemCount((int) cnt);
        }
        return dicts;
    }

    @Transactional
    public void updateDict(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngDict d = dictMapper.selectById(id);
        if (d == null) throw new BusinessException(404, "字典不存在");
        if (body.containsKey("dictName")) {
            String name = body.get("dictName") == null ? "" : body.get("dictName").toString().trim();
            if (!name.isBlank()) d.setDictName(name);
        }
        if (body.containsKey("dictType")) {
            String dictType = String.valueOf(body.get("dictType")).trim().toUpperCase();
            if (Set.of("GB", "DB", "HB", "TB", "CUSTOM").contains(dictType)) {
                d.setDictType(dictType);
            }
        }
        if (body.containsKey("standardNo")) d.setStandardNo(str(body.get("standardNo"), null));
        if (body.containsKey("publisher")) d.setPublisher(str(body.get("publisher"), null));
        if (body.containsKey("versionNo")) d.setVersionNo(str(body.get("versionNo"), null));
        if (body.containsKey("remark")) d.setRemark(str(body.get("remark"), null));
        dictMapper.updateById(d);
    }

    @Transactional
    public void deleteDicts(UserPrincipal operator, List<Long> ids) {
        for (Long id : ids) {
            dictItemMapper.delete(new LambdaQueryWrapper<IngDictItem>().eq(IngDictItem::getDictId, id));
            dictMapper.deleteById(id);
        }
    }

    public List<IngDictItem> listDictItems(Long dictId) {
        return dictItemMapper.selectList(new LambdaQueryWrapper<IngDictItem>()
                .eq(IngDictItem::getDictId, dictId).orderByAsc(IngDictItem::getSortOrder));
    }

    @Transactional
    public Long createDictItem(UserPrincipal operator, Long dictId, Map<String, Object> body) {
        IngDictItem item = new IngDictItem();
        item.setDictId(dictId);
        item.setItemKey(required(body.get("itemKey"), "代码值").toString());
        item.setItemValue(required(body.get("itemValue"), "中文名称").toString());
        Object usage = body.get("bizUsage");
        if (usage == null) {
            usage = body.get("biz_usage");
        }
        item.setBizUsage(usage == null ? "" : String.valueOf(usage).trim());
        item.setSortOrder(intVal(body.get("sortOrder"), 0));
        item.setStatus("ACTIVE");
        dictItemMapper.insert(item);
        refreshDictItemCount(dictId);
        return item.getId();
    }

    @Transactional
    public void updateDictItem(UserPrincipal operator, Long itemId, Map<String, Object> body) {
        IngDictItem item = dictItemMapper.selectById(itemId);
        if (item == null) throw new BusinessException(404, "字典值不存在");
        if (body.containsKey("itemKey")) item.setItemKey(body.get("itemKey").toString());
        if (body.containsKey("itemValue")) item.setItemValue(body.get("itemValue").toString());
        if (body.containsKey("bizUsage") || body.containsKey("biz_usage")) {
            Object usage = body.containsKey("bizUsage") ? body.get("bizUsage") : body.get("biz_usage");
            item.setBizUsage(usage == null ? "" : String.valueOf(usage).trim());
        }
        if (body.containsKey("sortOrder")) item.setSortOrder(intVal(body.get("sortOrder"), item.getSortOrder()));
        dictItemMapper.updateById(item);
    }

    @Transactional
    public void deleteDictItem(UserPrincipal operator, Long itemId) {
        IngDictItem item = dictItemMapper.selectById(itemId);
        if (item == null) return;
        Long dictId = item.getDictId();
        dictItemMapper.deleteById(itemId);
        refreshDictItemCount(dictId);
    }

    @Transactional
    public Map<String, Object> importDictCsv(UserPrincipal operator, String csvText) {
        int rows = 0;
        Long currentDictId = null;
        for (String line : csvText.split("\\r?\\n")) {
            if (line.isBlank() || line.toLowerCase().startsWith("dict_code")) continue;
            String[] p = line.split(",", -1);
            if (p.length < 4) continue;
            String dictCode = p[0].trim();
            IngDict dict = dictMapper.selectOne(new LambdaQueryWrapper<IngDict>().eq(IngDict::getDictCode, dictCode).last("LIMIT 1"));
            if (dict == null) {
                dict = new IngDict();
                dict.setDictCode(dictCode);
                dict.setDictName(p[1].trim());
                dict.setDictType("CUSTOM");
                dict.setItemCount(0);
                dict.setStatus("ACTIVE");
                dictMapper.insert(dict);
            }
            currentDictId = dict.getId();
            Map<String, Object> itemBody = new HashMap<>();
            itemBody.put("itemKey", p[2].trim());
            itemBody.put("itemValue", p[3].trim());
            if (p.length > 4) itemBody.put("bizUsage", p[4].trim());
            createDictItem(operator, currentDictId, itemBody);
            rows++;
        }
        return Map.of("importedRows", rows);
    }

    public String dictTemplateCsv() {
        return "dict_code,dict_name,item_key,item_value,biz_usage\n"
                + "DICT_GENDER,性别,M,男,用于人员性别采集\n"
                + "DICT_GENDER,性别,F,女,用于人员性别采集\n";
    }

    public String exportDictCsv(List<Long> dictIds) {
        StringBuilder sb = new StringBuilder("dict_code,dict_name,item_key,item_value,biz_usage\n");
        for (Long dictId : dictIds) {
            IngDict d = dictMapper.selectById(dictId);
            if (d == null) continue;
            for (IngDictItem item : listDictItems(dictId)) {
                String usage = item.getBizUsage() == null ? "" : item.getBizUsage().replace(',', '，');
                sb.append(d.getDictCode()).append(',')
                        .append(d.getDictName()).append(',')
                        .append(item.getItemKey()).append(',')
                        .append(item.getItemValue()).append(',')
                        .append(usage).append('\n');
            }
        }
        return sb.toString();
    }

    private void refreshDictItemCount(Long dictId) {
        long cnt = dictItemMapper.selectCount(new LambdaQueryWrapper<IngDictItem>().eq(IngDictItem::getDictId, dictId));
        IngDict d = dictMapper.selectById(dictId);
        if (d != null) {
            d.setItemCount((int) cnt);
            dictMapper.updateById(d);
        }
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private Integer intVal(Object v, Integer def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        return Integer.valueOf(String.valueOf(v));
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }
}
