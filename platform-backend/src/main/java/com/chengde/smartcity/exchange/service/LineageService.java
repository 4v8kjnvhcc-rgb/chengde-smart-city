package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
import com.chengde.smartcity.exchange.entity.IngAssetTagBinding;
import com.chengde.smartcity.exchange.entity.IngColumnLineage;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngLineageEdge;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.mapper.IngAssetTagBindingMapper;
import com.chengde.smartcity.exchange.mapper.IngAssetTagMapper;
import com.chengde.smartcity.exchange.mapper.IngColumnLineageMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngLineageEdgeMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.service.AccessControlService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class LineageService {

    private final IngProjectMapper projectMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final IngDataTableMapper tableMapper;
    private final IngLineageEdgeMapper edgeMapper;
    private final IngColumnLineageMapper columnLineageMapper;
    private final IngAssetTagBindingMapper tagBindingMapper;
    private final IngAssetTagMapper tagMapper;
    private final AccessControlService accessControlService;

    public LineageService(IngProjectMapper projectMapper,
                          IngDataSourceMapper dataSourceMapper,
                          IngDataTableMapper tableMapper,
                          IngLineageEdgeMapper edgeMapper,
                          IngColumnLineageMapper columnLineageMapper,
                          IngAssetTagBindingMapper tagBindingMapper,
                          IngAssetTagMapper tagMapper,
                          AccessControlService accessControlService) {
        this.projectMapper = projectMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.tableMapper = tableMapper;
        this.edgeMapper = edgeMapper;
        this.columnLineageMapper = columnLineageMapper;
        this.tagBindingMapper = tagBindingMapper;
        this.tagMapper = tagMapper;
        this.accessControlService = accessControlService;
    }

    public Map<String, Object> panorama(UserPrincipal operator, Long projectId, String keyword, Long categoryTagId) {
        IngProject project = resolveProject(operator, projectId);
        return panoramaForProject(project, keyword, categoryTagId);
    }

    /** 兼容内部调用（无操作者时不鉴权，仅按项目） */
    public Map<String, Object> panorama(Long projectId, String keyword, Long categoryTagId) {
        IngProject project = resolveProject(null, projectId);
        return panoramaForProject(project, keyword, categoryTagId);
    }

    private Map<String, Object> panoramaForProject(IngProject project, String keyword, Long categoryTagId) {
        List<IngDataSource> sources = dataSourceMapper.selectList(new LambdaQueryWrapper<IngDataSource>()
                .eq(IngDataSource::getProjectId, project.getId()));
        Map<Long, IngDataSource> sourceById = sources.stream()
                .collect(Collectors.toMap(IngDataSource::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
        List<Long> sourceIds = new ArrayList<>(sourceById.keySet());
        List<IngDataTable> tables = sourceIds.isEmpty() ? List.of()
                : tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>().in(IngDataTable::getSourceId, sourceIds)
                .orderByAsc(IngDataTable::getId));
        Map<Long, IngDataTable> tableById = tables.stream()
                .collect(Collectors.toMap(IngDataTable::getId, t -> t, (a, b) -> a, LinkedHashMap::new));
        Set<String> tableNodes = tables.stream().map(t -> tableNodeId(t.getId())).collect(Collectors.toCollection(LinkedHashSet::new));

        List<IngLineageEdge> allEdges = edgeMapper.selectList(new LambdaQueryWrapper<IngLineageEdge>()
                .orderByAsc(IngLineageEdge::getSortOrder));
        List<IngLineageEdge> edges = allEdges.stream()
                .filter(e -> edgeInProject(e, project.getId(), tableNodes))
                .collect(Collectors.toList());

        Map<Long, List<String>> tagNamesByTable = loadTagNames(tables);
        Set<Long> categoryMatched = categoryTagId == null ? null : tablesWithTag(tables, categoryTagId);

        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> connected = new HashSet<>();
        for (IngLineageEdge e : edges) {
            connected.add(e.getFromNode());
            connected.add(e.getToNode());
        }

        for (IngDataTable t : tables) {
            String nid = tableNodeId(t.getId());
            boolean isolated = !connected.contains(nid);
            boolean matched = matchKeyword(t, sourceById.get(t.getSourceId()), keyword)
                    && (categoryMatched == null || categoryMatched.contains(t.getId()));
            if (keywordNotBlank(keyword) || categoryTagId != null) {
                // 过滤模式：命中表 + 其一阶邻居仍保留在图中；非命中淡化
            }
            nodes.add(tableNode(t, sourceById.get(t.getSourceId()), isolated, matched, tagNamesByTable.get(t.getId())));
        }

        // 边端点若为 ds-* 也加入 SOURCE 节点（便于跨库展示）
        for (IngLineageEdge e : edges) {
            addSourceNodeIfAbsent(nodes, e.getFromNode(), e.getFromLabel(), e.getFromSourceId(), sourceById);
            addSourceNodeIfAbsent(nodes, e.getToNode(), e.getToLabel(), e.getToSourceId(), sourceById);
        }

        long linkedTableCount = nodes.stream()
                .filter(n -> "TABLE".equals(n.get("type")) && !Boolean.TRUE.equals(n.get("isolated")))
                .count();
        long isolatedTableCount = nodes.stream()
                .filter(n -> "TABLE".equals(n.get("type")) && Boolean.TRUE.equals(n.get("isolated")))
                .count();
        List<Map<String, Object>> edgeViewsAll = edges.stream().map(e -> edgeView(e, sourceById)).collect(Collectors.toList());
        long crossDbEdgeCount = edgeViewsAll.stream().filter(e -> Boolean.TRUE.equals(e.get("crossDb"))).count();

        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (!kw.isEmpty() || categoryTagId != null) {
            Set<String> keep = new HashSet<>();
            for (Map<String, Object> n : nodes) {
                if (Boolean.TRUE.equals(n.get("matched")) && "TABLE".equals(n.get("type"))) {
                    keep.add(String.valueOf(n.get("id")));
                }
            }
            for (IngLineageEdge e : edges) {
                if (keep.contains(e.getFromNode()) || keep.contains(e.getToNode())) {
                    keep.add(e.getFromNode());
                    keep.add(e.getToNode());
                }
            }
            nodes = nodes.stream().filter(n -> keep.contains(String.valueOf(n.get("id")))
                    || ("TABLE".equals(n.get("type")) && Boolean.TRUE.equals(n.get("matched")))).collect(Collectors.toList());
            Set<String> nodeIds = nodes.stream().map(n -> String.valueOf(n.get("id"))).collect(Collectors.toSet());
            edges = edges.stream()
                    .filter(e -> nodeIds.contains(e.getFromNode()) && nodeIds.contains(e.getToNode()))
                    .collect(Collectors.toList());
            for (Map<String, Object> n : nodes) {
                if (!Boolean.TRUE.equals(n.get("matched"))) n.put("dimmed", true);
            }
        }

        List<Map<String, Object>> categories = buildCategoryOptions(tables, tagNamesByTable);
        List<Map<String, Object>> edgeViews = edges.stream().map(e -> edgeView(e, sourceById)).collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", project.getId());
        out.put("projectName", project.getProjectName());
        out.put("nodes", nodes);
        out.put("edges", edgeViews);
        out.put("categories", categories);
        out.put("tableCount", tables.size());
        out.put("isolatedCount", isolatedTableCount);
        out.put("linkedTableCount", linkedTableCount);
        out.put("crossDbEdgeCount", crossDbEdgeCount);
        return out;
    }

    public Map<String, Object> drill(UserPrincipal operator, String nodeId) {
        assertNodeAccess(operator, nodeId);
        return drill(nodeId);
    }

    public Map<String, Object> drill(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) throw new BusinessException(400, "nodeId required");
        List<IngLineageEdge> all = edgeMapper.selectList(null);
        List<IngLineageEdge> upstream = all.stream().filter(e -> nodeId.equals(e.getToNode())).collect(Collectors.toList());
        List<IngLineageEdge> downstream = all.stream().filter(e -> nodeId.equals(e.getFromNode())).collect(Collectors.toList());

        Set<String> related = new LinkedHashSet<>();
        related.add(nodeId);
        upstream.forEach(e -> related.add(e.getFromNode()));
        downstream.forEach(e -> related.add(e.getToNode()));

        Map<Long, IngDataSource> sourceById = dataSourceMapper.selectList(null).stream()
                .collect(Collectors.toMap(IngDataSource::getId, s -> s, (a, b) -> a));
        Map<String, Object> focusMeta = tableMetaInternal(nodeId);

        Map<String, Map<String, Object>> nodeById = new LinkedHashMap<>();
        for (String id : related) {
            nodeById.put(id, resolveNode(id, all, sourceById));
        }
        Map<String, Object> focusNodeView = nodeById.get(nodeId);

        List<Map<String, Object>> upstreamEdges = upstream.stream().map(e -> edgeView(e, sourceById)).collect(Collectors.toList());
        List<Map<String, Object>> downstreamEdges = downstream.stream().map(e -> edgeView(e, sourceById)).collect(Collectors.toList());

        // 一阶邻居节点列表，供前端三栏直渲染（附带边属性）
        List<Map<String, Object>> upstreamNodes = new ArrayList<>();
        for (Map<String, Object> e : upstreamEdges) {
            String from = String.valueOf(e.get("fromNode"));
            Map<String, Object> n = new LinkedHashMap<>(nodeById.getOrDefault(from, Map.of("id", from, "label", from)));
            n.put("edgeType", e.get("edgeType"));
            n.put("crossDb", e.get("crossDb"));
            n.put("fieldMapping", e.get("fieldMapping"));
            n.put("fromSourceName", e.get("fromSourceName"));
            n.put("toSourceName", e.get("toSourceName"));
            upstreamNodes.add(n);
        }
        List<Map<String, Object>> downstreamNodes = new ArrayList<>();
        for (Map<String, Object> e : downstreamEdges) {
            String to = String.valueOf(e.get("toNode"));
            Map<String, Object> n = new LinkedHashMap<>(nodeById.getOrDefault(to, Map.of("id", to, "label", to)));
            n.put("edgeType", e.get("edgeType"));
            n.put("crossDb", e.get("crossDb"));
            n.put("fieldMapping", e.get("fieldMapping"));
            n.put("fromSourceName", e.get("fromSourceName"));
            n.put("toSourceName", e.get("toSourceName"));
            downstreamNodes.add(n);
        }

        List<IngLineageEdge> local = new ArrayList<>();
        local.addAll(upstream);
        local.addAll(downstream);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("focusNode", nodeId);
        out.put("focusMeta", focusMeta);
        out.put("focus", focusNodeView);
        out.put("upstream", upstreamEdges);
        out.put("downstream", downstreamEdges);
        out.put("upstreamNodes", upstreamNodes);
        out.put("downstreamNodes", downstreamNodes);
        out.put("nodes", new ArrayList<>(nodeById.values()));
        out.put("edges", local.stream().map(e -> edgeView(e, sourceById)).collect(Collectors.toList()));
        out.put("hasMore", !upstream.isEmpty() || !downstream.isEmpty());
        return out;
    }

    public Map<String, Object> fieldLineage(UserPrincipal operator, String tableNode) {
        assertNodeAccess(operator, tableNode);
        return fieldLineage(tableNode);
    }

    public Map<String, Object> fieldLineage(String tableNode) {
        if (tableNode == null || tableNode.isBlank()) throw new BusinessException(400, "tableNode required");
        List<IngColumnLineage> rows = columnLineageMapper.selectList(new LambdaQueryWrapper<IngColumnLineage>()
                .eq(IngColumnLineage::getTableNode, tableNode)
                .orderByAsc(IngColumnLineage::getSortOrder));
        List<Map<String, Object>> fieldEdges = new ArrayList<>();
        for (IngColumnLineage r : rows) {
            if (r.getUpstreamTable() != null && r.getUpstreamColumn() != null) {
                Map<String, Object> up = new LinkedHashMap<>();
                up.put("from", r.getUpstreamTable() + "." + r.getUpstreamColumn());
                up.put("to", tableNode + "." + r.getColumnCode());
                up.put("direction", "UP");
                fieldEdges.add(up);
            }
            if (r.getDownstreamTable() != null && r.getDownstreamColumn() != null) {
                Map<String, Object> down = new LinkedHashMap<>();
                down.put("from", tableNode + "." + r.getColumnCode());
                down.put("to", r.getDownstreamTable() + "." + r.getDownstreamColumn());
                down.put("direction", "DOWN");
                fieldEdges.add(down);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableNode", tableNode);
        out.put("fields", rows);
        out.put("fieldEdges", fieldEdges);
        out.put("focusMeta", tableMetaInternal(tableNode));
        return out;
    }

    public Map<String, Object> tableMeta(UserPrincipal operator, String tableNode) {
        assertNodeAccess(operator, tableNode);
        return tableMetaInternal(tableNode);
    }

    public Map<String, Object> tableMeta(String tableNode) {
        return tableMetaInternal(tableNode);
    }

    /** 供 M046 等复用：按表解析上下游血缘 */
    public Map<String, Object> lineageForTable(IngDataTable table) {
        String nodeId = tableNodeId(table.getId());
        List<IngLineageEdge> edges = edgeMapper.selectList(new LambdaQueryWrapper<IngLineageEdge>()
                .orderByAsc(IngLineageEdge::getSortOrder));
        List<Map<String, Object>> upstream = new ArrayList<>();
        List<Map<String, Object>> downstream = new ArrayList<>();
        String code = safe(table.getTableCode()).toLowerCase(Locale.ROOT);
        String name = safe(table.getTableName());
        for (IngLineageEdge e : edges) {
            boolean isFocusFrom = nodeId.equals(e.getFromNode())
                    || containsIgnore(e.getFromLabel(), name) || containsIgnore(e.getFromNode(), code);
            boolean isFocusTo = nodeId.equals(e.getToNode())
                    || containsIgnore(e.getToLabel(), name) || containsIgnore(e.getToNode(), code);
            if (isFocusFrom) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("node", e.getToNode());
                m.put("label", e.getToLabel());
                m.put("edgeType", e.getEdgeType());
                downstream.add(m);
            }
            if (isFocusTo) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("node", e.getFromNode());
                m.put("label", e.getFromLabel());
                m.put("edgeType", e.getEdgeType());
                upstream.add(m);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("upstream", upstream);
        out.put("downstream", downstream);
        return out;
    }

    public static String tableNodeId(Long tableId) {
        return "tbl-" + tableId;
    }

    public static Long parseTableId(String nodeId) {
        if (nodeId == null || !nodeId.startsWith("tbl-")) return null;
        try {
            return Long.parseLong(nodeId.substring(4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> tableMetaInternal(String tableNode) {
        Long tid = parseTableId(tableNode);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("nodeId", tableNode);
        if (tid == null) {
            meta.put("found", false);
            return meta;
        }
        IngDataTable t = tableMapper.selectById(tid);
        if (t == null) {
            meta.put("found", false);
            return meta;
        }
        IngDataSource ds = t.getSourceId() == null ? null : dataSourceMapper.selectById(t.getSourceId());
        List<String> tags = loadTagNames(List.of(t)).getOrDefault(t.getId(), List.of());
        meta.put("found", true);
        meta.put("tableId", t.getId());
        meta.put("tableCode", t.getTableCode());
        meta.put("tableName", t.getTableName());
        meta.put("usageDesc", t.getUsageDesc());
        meta.put("categories", tags);
        meta.put("sourceId", t.getSourceId());
        meta.put("sourceName", ds == null ? null : ds.getSourceName());
        meta.put("sourceSchema", t.getSourceSchema());
        meta.put("sourceType", ds == null ? null : ds.getSourceType());
        return meta;
    }

    private IngProject resolveProject(UserPrincipal operator, Long projectId) {
        if (projectId != null) {
            if (operator != null) {
                accessControlService.assertProjectAccess(operator, projectId);
            }
            IngProject p = projectMapper.selectById(projectId);
            if (p == null) throw new BusinessException(404, "项目不存在");
            return p;
        }
        if (operator != null) {
            Set<Long> allowed = accessControlService.effectiveProjectIds(operator);
            if (allowed.isEmpty()) {
                throw new BusinessException(404, "暂无可见登记项目");
            }
            IngProject first = projectMapper.selectOne(new LambdaQueryWrapper<IngProject>()
                    .in(IngProject::getId, allowed)
                    .orderByAsc(IngProject::getId)
                    .last("LIMIT 1"));
            if (first == null) throw new BusinessException(404, "暂无可见登记项目");
            return first;
        }
        IngProject first = projectMapper.selectOne(new LambdaQueryWrapper<IngProject>().orderByAsc(IngProject::getId).last("LIMIT 1"));
        if (first == null) throw new BusinessException(404, "暂无登记项目");
        return first;
    }

    private void assertNodeAccess(UserPrincipal operator, String nodeId) {
        if (operator == null || nodeId == null) return;
        Long tableId = parseTableId(nodeId);
        if (tableId != null) {
            accessControlService.assertTableAccess(operator, tableId);
            return;
        }
        if (nodeId.startsWith("ds-")) {
            try {
                Long sourceId = Long.parseLong(nodeId.substring(3));
                accessControlService.assertSourceAccess(operator, sourceId);
            } catch (NumberFormatException ignored) {
                // 非标准 id，放行给后续业务校验
            }
        }
    }

    private boolean edgeInProject(IngLineageEdge e, Long projectId, Set<String> tableNodes) {
        if (e.getProjectId() != null && Objects.equals(e.getProjectId(), projectId)) return true;
        return tableNodes.contains(e.getFromNode()) || tableNodes.contains(e.getToNode());
    }

    private Map<String, Object> tableNode(IngDataTable t, IngDataSource ds, boolean isolated, boolean matched, List<String> tags) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", tableNodeId(t.getId()));
        n.put("label", t.getTableName());
        n.put("type", "TABLE");
        n.put("tableId", t.getId());
        n.put("tableCode", t.getTableCode());
        n.put("isolated", isolated);
        n.put("matched", matched);
        n.put("sourceId", t.getSourceId());
        n.put("sourceName", ds == null ? null : ds.getSourceName());
        n.put("sourceSchema", t.getSourceSchema());
        n.put("usageDesc", t.getUsageDesc());
        n.put("categories", tags == null ? List.of() : tags);
        return n;
    }

    private void addSourceNodeIfAbsent(List<Map<String, Object>> nodes, String nodeId, String label,
                                       Long sourceId, Map<Long, IngDataSource> sourceById) {
        if (nodeId == null || !nodeId.startsWith("ds-")) return;
        boolean exists = nodes.stream().anyMatch(n -> nodeId.equals(n.get("id")));
        if (exists) return;
        IngDataSource ds = sourceId != null ? sourceById.get(sourceId) : null;
        if (ds == null && nodeId.startsWith("ds-")) {
            try {
                ds = sourceById.get(Long.parseLong(nodeId.substring(3)));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", nodeId);
        n.put("label", ds != null ? ds.getSourceName() : (label == null ? nodeId : label));
        n.put("type", "SOURCE");
        n.put("isolated", false);
        n.put("matched", false);
        n.put("sourceId", ds == null ? sourceId : ds.getId());
        n.put("sourceName", ds == null ? null : ds.getSourceName());
        nodes.add(n);
    }

    private Map<String, Object> resolveNode(String id, List<IngLineageEdge> all, Map<Long, IngDataSource> sourceById) {
        Long tid = parseTableId(id);
        if (tid != null) {
            IngDataTable t = tableMapper.selectById(tid);
            if (t != null) {
                return tableNode(t, sourceById.get(t.getSourceId()), false, true,
                        loadTagNames(List.of(t)).getOrDefault(t.getId(), List.of()));
            }
        }
        if (id.startsWith("ds-")) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", id);
            n.put("type", "SOURCE");
            try {
                Long sid = Long.parseLong(id.substring(3));
                IngDataSource ds = sourceById.get(sid);
                n.put("label", ds != null ? ds.getSourceName() : id);
                n.put("sourceName", ds != null ? ds.getSourceName() : null);
            } catch (NumberFormatException e) {
                n.put("label", id);
            }
            return n;
        }
        IngLineageEdge edge = all.stream().filter(e -> id.equals(e.getFromNode()) || id.equals(e.getToNode())).findFirst().orElse(null);
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("type", id.startsWith("cat-") ? "CATALOG" : "TABLE");
        if (edge != null) {
            n.put("label", id.equals(edge.getFromNode()) ? edge.getFromLabel() : edge.getToLabel());
        } else {
            n.put("label", id);
        }
        return n;
    }

    private Map<String, Object> edgeView(IngLineageEdge e, Map<Long, IngDataSource> sourceById) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("fromNode", e.getFromNode());
        m.put("toNode", e.getToNode());
        m.put("fromLabel", e.getFromLabel());
        m.put("toLabel", e.getToLabel());
        m.put("edgeType", e.getEdgeType());
        m.put("fieldMapping", e.getFieldMapping());
        m.put("projectId", e.getProjectId());
        m.put("fromSourceId", e.getFromSourceId());
        m.put("toSourceId", e.getToSourceId());
        m.put("fromSourceName", e.getFromSourceId() == null ? null
                : (sourceById.get(e.getFromSourceId()) == null ? null : sourceById.get(e.getFromSourceId()).getSourceName()));
        m.put("toSourceName", e.getToSourceId() == null ? null
                : (sourceById.get(e.getToSourceId()) == null ? null : sourceById.get(e.getToSourceId()).getSourceName()));
        m.put("crossDb", e.getFromSourceId() != null && e.getToSourceId() != null
                && !Objects.equals(e.getFromSourceId(), e.getToSourceId()));
        return m;
    }

    private Map<Long, List<String>> loadTagNames(List<IngDataTable> tables) {
        Map<Long, List<String>> out = new HashMap<>();
        if (tables.isEmpty()) return out;
        List<Long> ids = tables.stream().map(IngDataTable::getId).collect(Collectors.toList());
        List<IngAssetTagBinding> binds = tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getAssetType, "TABLE")
                .in(IngAssetTagBinding::getAssetId, ids));
        if (binds.isEmpty()) {
            tables.forEach(t -> out.put(t.getId(), List.of()));
            return out;
        }
        Set<Long> tagIds = binds.stream().map(IngAssetTagBinding::getTagId).collect(Collectors.toSet());
        Map<Long, IngAssetTag> tagById = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(IngAssetTag::getId, t -> t, (a, b) -> a));
        for (IngAssetTagBinding b : binds) {
            IngAssetTag tag = tagById.get(b.getTagId());
            String name = tag == null ? String.valueOf(b.getTagId())
                    : (tag.getStdCode() == null ? tag.getTagName() : tag.getStdCode() + " " + tag.getTagName());
            out.computeIfAbsent(b.getAssetId(), k -> new ArrayList<>()).add(name);
        }
        tables.forEach(t -> out.putIfAbsent(t.getId(), List.of()));
        return out;
    }

    private Set<Long> tablesWithTag(List<IngDataTable> tables, Long tagId) {
        if (tables.isEmpty()) return Set.of();
        List<Long> ids = tables.stream().map(IngDataTable::getId).collect(Collectors.toList());
        return tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                        .eq(IngAssetTagBinding::getAssetType, "TABLE")
                        .eq(IngAssetTagBinding::getTagId, tagId)
                        .in(IngAssetTagBinding::getAssetId, ids))
                .stream().map(IngAssetTagBinding::getAssetId).collect(Collectors.toSet());
    }

    private List<Map<String, Object>> buildCategoryOptions(List<IngDataTable> tables, Map<Long, List<String>> tagNamesByTable) {
        if (tables.isEmpty()) return List.of();
        List<Long> ids = tables.stream().map(IngDataTable::getId).collect(Collectors.toList());
        List<IngAssetTagBinding> binds = tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getAssetType, "TABLE")
                .in(IngAssetTagBinding::getAssetId, ids));
        Map<Long, IngAssetTag> tagById = new HashMap<>();
        if (!binds.isEmpty()) {
            Set<Long> tagIds = binds.stream().map(IngAssetTagBinding::getTagId).collect(Collectors.toSet());
            tagMapper.selectBatchIds(tagIds).forEach(t -> tagById.put(t.getId(), t));
        }
        Map<Long, Map<String, Object>> opts = new LinkedHashMap<>();
        for (IngAssetTagBinding b : binds) {
            IngAssetTag tag = tagById.get(b.getTagId());
            if (tag == null) continue;
            opts.computeIfAbsent(tag.getId(), id -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tagId", tag.getId());
                m.put("tagName", tag.getStdCode() == null ? tag.getTagName() : tag.getStdCode() + " " + tag.getTagName());
                return m;
            });
        }
        return new ArrayList<>(opts.values());
    }

    private boolean matchKeyword(IngDataTable t, IngDataSource ds, String keyword) {
        if (!keywordNotBlank(keyword)) return true;
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        return safe(t.getTableName()).toLowerCase(Locale.ROOT).contains(kw)
                || safe(t.getTableCode()).toLowerCase(Locale.ROOT).contains(kw)
                || (ds != null && safe(ds.getSourceName()).toLowerCase(Locale.ROOT).contains(kw));
    }

    private static boolean keywordNotBlank(String keyword) {
        return keyword != null && !keyword.trim().isEmpty();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static boolean containsIgnore(String hay, String needle) {
        if (hay == null || needle == null || needle.isEmpty()) return false;
        return hay.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
