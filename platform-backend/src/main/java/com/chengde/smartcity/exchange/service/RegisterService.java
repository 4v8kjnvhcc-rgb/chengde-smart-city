package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
import com.chengde.smartcity.exchange.entity.IngColumnLineage;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngDict;
import com.chengde.smartcity.exchange.entity.IngDictItem;
import com.chengde.smartcity.exchange.entity.IngIngestTask;
import com.chengde.smartcity.exchange.entity.IngLineageEdge;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.entity.IngResourceRegistry;
import com.chengde.smartcity.exchange.mapper.IngAssetTagMapper;
import com.chengde.smartcity.exchange.mapper.IngColumnLineageMapper;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngDictItemMapper;
import com.chengde.smartcity.exchange.mapper.IngDictMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestTaskMapper;
import com.chengde.smartcity.exchange.mapper.IngLineageEdgeMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.exchange.mapper.IngResourceRegistryMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterService {

    private final IngDataTableMapper tableMapper;
    private final IngDataColumnMapper columnMapper;
    private final IngLineageEdgeMapper lineageMapper;
    private final IngColumnLineageMapper columnLineageMapper;
    private final IngAssetTagMapper tagMapper;
    private final IngProjectMapper projectMapper;
    private final IngIngestTaskMapper taskMapper;
    private final IngResourceRegistryMapper registryMapper;
    private final IngDictMapper dictMapper;
    private final IngDictItemMapper dictItemMapper;

    public RegisterService(IngDataTableMapper tableMapper, IngDataColumnMapper columnMapper,
                           IngLineageEdgeMapper lineageMapper, IngColumnLineageMapper columnLineageMapper,
                           IngAssetTagMapper tagMapper, IngProjectMapper projectMapper,
                           IngIngestTaskMapper taskMapper, IngResourceRegistryMapper registryMapper,
                           IngDictMapper dictMapper, IngDictItemMapper dictItemMapper) {
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
        this.lineageMapper = lineageMapper;
        this.columnLineageMapper = columnLineageMapper;
        this.tagMapper = tagMapper;
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.registryMapper = registryMapper;
        this.dictMapper = dictMapper;
        this.dictItemMapper = dictItemMapper;
    }

    public List<IngDataTable> listTables(Long sourceId) {
        LambdaQueryWrapper<IngDataTable> q = new LambdaQueryWrapper<IngDataTable>().orderByAsc(IngDataTable::getId);
        if (sourceId != null) {
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
        String tableName = required(body.get("tableName"), "tableName").toString();
        IngDataTable t = new IngDataTable();
        t.setSourceId(sourceId);
        t.setTableCode("TBL_" + System.currentTimeMillis());
        t.setTableName(tableName);
        t.setModelingMode(str(body.get("modelingMode"), "FORWARD"));
        t.setColumnCount(0);
        t.setStatus("ACTIVE");
        tableMapper.insert(t);
        seedDemoColumns(t.getId(), tableName);
        t.setColumnCount(3);
        tableMapper.updateById(t);
        return t.getId();
    }

    @Transactional
    public Long createColumn(UserPrincipal operator, Long tableId, Map<String, Object> body) {
        IngDataTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "表不存在");
        }
        IngDataColumn col = new IngDataColumn();
        col.setTableId(tableId);
        col.setColumnCode(required(body.get("columnCode"), "columnCode").toString());
        col.setColumnName(required(body.get("columnName"), "columnName").toString());
        col.setDataType(str(body.get("dataType"), "VARCHAR(64)"));
        col.setNullableFlag(intVal(body.get("nullableFlag"), 1));
        col.setSemanticDesc(str(body.get("semanticDesc"), null));
        col.setLengthVal(intVal(body.get("lengthVal"), null));
        col.setComponentType(str(body.get("componentType"), "INPUT"));
        col.setRequiredTip(str(body.get("requiredTip"), null));
        col.setBuiltInFlag(0);
        int maxSort = columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId)).stream()
                .mapToInt(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()).max().orElse(0);
        col.setSortOrder(maxSort + 1);
        columnMapper.insert(col);
        table.setColumnCount(maxSort + 1);
        tableMapper.updateById(table);
        return col.getId();
    }

    @Transactional
    public void updateColumn(UserPrincipal operator, Long columnId, Map<String, Object> body) {
        IngDataColumn col = columnMapper.selectById(columnId);
        if (col == null) {
            throw new BusinessException(404, "数据项不存在");
        }
        if (col.getBuiltInFlag() != null && col.getBuiltInFlag() == 1) {
            throw new BusinessException(400, "系统内置属性不可编辑");
        }
        if (body.containsKey("columnName")) col.setColumnName(body.get("columnName").toString());
        if (body.containsKey("dataType")) col.setDataType(body.get("dataType").toString());
        if (body.containsKey("nullableFlag")) col.setNullableFlag(intVal(body.get("nullableFlag"), col.getNullableFlag()));
        if (body.containsKey("semanticDesc")) col.setSemanticDesc(str(body.get("semanticDesc"), null));
        if (body.containsKey("lengthVal")) col.setLengthVal(intVal(body.get("lengthVal"), null));
        if (body.containsKey("componentType")) col.setComponentType(body.get("componentType").toString());
        if (body.containsKey("requiredTip")) col.setRequiredTip(str(body.get("requiredTip"), null));
        columnMapper.updateById(col);
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
        List<IngLineageEdge> edges = lineageMapper.selectList(
                new LambdaQueryWrapper<IngLineageEdge>().orderByAsc(IngLineageEdge::getSortOrder));
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (IngLineageEdge e : edges) {
            nodeMap.putIfAbsent(e.getFromNode(), node("SOURCE", e.getFromNode(), e.getFromLabel()));
            nodeMap.putIfAbsent(e.getToNode(), node(inferType(e.getToNode()), e.getToNode(), e.getToLabel()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", new ArrayList<>(nodeMap.values()));
        out.put("edges", edges);
        out.put("projectScope", projectScope == null ? "ALL" : projectScope);
        return out;
    }

    public Map<String, Object> lineageDrill(String nodeId) {
        List<IngLineageEdge> all = lineageMapper.selectList(null);
        List<IngLineageEdge> upstream = all.stream().filter(e -> nodeId.equals(e.getToNode())).collect(Collectors.toList());
        List<IngLineageEdge> downstream = all.stream().filter(e -> nodeId.equals(e.getFromNode())).collect(Collectors.toList());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("focusNode", nodeId);
        out.put("upstream", upstream);
        out.put("downstream", downstream);
        Set<String> related = new java.util.HashSet<>();
        related.add(nodeId);
        upstream.forEach(e -> related.add(e.getFromNode()));
        downstream.forEach(e -> related.add(e.getToNode()));
        List<Map<String, Object>> nodes = related.stream().map(id -> {
            IngLineageEdge edge = all.stream().filter(e -> id.equals(e.getFromNode()) || id.equals(e.getToNode())).findFirst().orElse(null);
            String label = id;
            if (edge != null) {
                if (id.equals(edge.getFromNode())) label = edge.getFromLabel();
                else if (id.equals(edge.getToNode())) label = edge.getToLabel();
            }
            return node(inferType(id), id, label);
        }).collect(Collectors.toList());
        out.put("nodes", nodes);
        List<IngLineageEdge> localEdges = new ArrayList<>();
        localEdges.addAll(upstream);
        localEdges.addAll(downstream);
        out.put("edges", localEdges);
        return out;
    }

    public List<IngColumnLineage> fieldLineage(String tableNode) {
        return columnLineageMapper.selectList(new LambdaQueryWrapper<IngColumnLineage>()
                .eq(IngColumnLineage::getTableNode, tableNode).orderByAsc(IngColumnLineage::getSortOrder));
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
        return tagMapper.selectList(new LambdaQueryWrapper<IngAssetTag>().orderByAsc(IngAssetTag::getId));
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
        tagMapper.insert(tag);
        return tag.getId();
    }

    @Transactional
    public void updateTag(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngAssetTag tag = tagMapper.selectById(id);
        if (tag == null) throw new BusinessException(404, "标签不存在");
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

    public List<IngDict> listDicts(String keyword) {
        LambdaQueryWrapper<IngDict> q = new LambdaQueryWrapper<IngDict>().orderByAsc(IngDict::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(IngDict::getDictCode, keyword).or().like(IngDict::getDictName, keyword));
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
        if (body.containsKey("dictName")) d.setDictName(body.get("dictName").toString());
        if (body.containsKey("dictType")) d.setDictType(body.get("dictType").toString());
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
        item.setItemKey(required(body.get("itemKey"), "itemKey").toString());
        item.setItemValue(required(body.get("itemValue"), "itemValue").toString());
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
                dict.setDictType("STANDARD");
                dict.setItemCount(0);
                dict.setStatus("ACTIVE");
                dictMapper.insert(dict);
            }
            currentDictId = dict.getId();
            Map<String, Object> itemBody = new HashMap<>();
            itemBody.put("itemKey", p[2].trim());
            itemBody.put("itemValue", p[3].trim());
            createDictItem(operator, currentDictId, itemBody);
            rows++;
        }
        return Map.of("importedRows", rows);
    }

    public String dictTemplateCsv() {
        return "dict_code,dict_name,item_key,item_value\nDICT_GENDER,性别,M,男\nDICT_GENDER,性别,F,女\n";
    }

    public String exportDictCsv(List<Long> dictIds) {
        StringBuilder sb = new StringBuilder("dict_code,dict_name,item_key,item_value\n");
        for (Long dictId : dictIds) {
            IngDict d = dictMapper.selectById(dictId);
            if (d == null) continue;
            for (IngDictItem item : listDictItems(dictId)) {
                sb.append(d.getDictCode()).append(',')
                        .append(d.getDictName()).append(',')
                        .append(item.getItemKey()).append(',')
                        .append(item.getItemValue()).append('\n');
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

    private void seedDemoColumns(Long tableId, String tableName) {
        String[][] cols = tableName.contains("人口")
                ? new String[][]{{"ID_NO", "证件号码", "VARCHAR(32)"}, {"PERSON_NAME", "姓名", "VARCHAR(64)"}, {"BIRTH_DATE", "出生日期", "DATE"}}
                : new String[][]{{"CODE", "编码", "VARCHAR(64)"}, {"NAME", "名称", "VARCHAR(256)"}, {"UPDATED_AT", "更新时间", "DATETIME"}};
        int i = 1;
        for (String[] c : cols) {
            IngDataColumn col = new IngDataColumn();
            col.setTableId(tableId);
            col.setColumnCode(c[0]);
            col.setColumnName(c[1]);
            col.setDataType(c[2]);
            col.setNullableFlag(c[0].equals("CODE") || c[0].equals("ID_NO") ? 0 : 1);
            col.setSortOrder(i++);
            col.setBuiltInFlag(c[0].equals("CODE") || c[0].equals("ID_NO") ? 1 : 0);
            col.setComponentType("INPUT");
            columnMapper.insert(col);
        }
    }

    private Map<String, Object> node(String type, String id, String label) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("label", label);
        return n;
    }

    private String inferType(String nodeId) {
        if (nodeId.startsWith("src")) return "SOURCE";
        if (nodeId.startsWith("cat")) return "CATALOG";
        return "TABLE";
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
