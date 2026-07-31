package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.exchange.entity.BizDataAsset;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
import com.chengde.smartcity.exchange.entity.IngAssetTagBinding;
import com.chengde.smartcity.exchange.entity.IngClsAssetMark;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngIngestChannel;
import com.chengde.smartcity.exchange.entity.IngLineageEdge;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.entity.IngResourceRegistry;
import com.chengde.smartcity.exchange.entity.IngSearchDocument;
import com.chengde.smartcity.exchange.entity.IngSearchQueryLog;
import com.chengde.smartcity.exchange.mapper.BizDataAssetMapper;
import com.chengde.smartcity.exchange.mapper.IngAssetTagBindingMapper;
import com.chengde.smartcity.exchange.mapper.IngAssetTagMapper;
import com.chengde.smartcity.exchange.mapper.IngClsAssetMarkMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestChannelMapper;
import com.chengde.smartcity.exchange.mapper.IngLineageEdgeMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.exchange.mapper.IngResourceRegistryMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchDocumentMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchQueryLogMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 全局数据资产视图（M076）：全维度总览、TOP、趋势、血缘快照、可下钻入口。
 * 检索能力复用 asset-search，本模块负责可视化门户与全景聚合。
 */
@Service
public class GlobalAssetViewService {

    private static final Logger log = LoggerFactory.getLogger(GlobalAssetViewService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BizDataAssetMapper assetMapper;
    private final IngIngestChannelMapper channelMapper;
    private final IngResourceRegistryMapper registryMapper;
    private final IngDataTableMapper tableMapper;
    private final IngDataSourceMapper sourceMapper;
    private final IngProjectMapper projectMapper;
    private final IngAssetTagMapper tagMapper;
    private final IngAssetTagBindingMapper tagBindingMapper;
    private final IngClsAssetMarkMapper markMapper;
    private final IngLineageEdgeMapper edgeMapper;
    private final IngSearchDocumentMapper searchDocMapper;
    private final IngSearchQueryLogMapper searchLogMapper;
    private final ClassifyGradeService classifyGradeService;
    private final MaskPolicyService maskPolicyService;
    private final AssetSearchService assetSearchService;
    private final TagManageService tagManageService;
    private final LineageService lineageService;

    public GlobalAssetViewService(
            BizDataAssetMapper assetMapper,
            IngIngestChannelMapper channelMapper,
            IngResourceRegistryMapper registryMapper,
            IngDataTableMapper tableMapper,
            IngDataSourceMapper sourceMapper,
            IngProjectMapper projectMapper,
            IngAssetTagMapper tagMapper,
            IngAssetTagBindingMapper tagBindingMapper,
            IngClsAssetMarkMapper markMapper,
            IngLineageEdgeMapper edgeMapper,
            IngSearchDocumentMapper searchDocMapper,
            IngSearchQueryLogMapper searchLogMapper,
            ClassifyGradeService classifyGradeService,
            MaskPolicyService maskPolicyService,
            AssetSearchService assetSearchService,
            TagManageService tagManageService,
            LineageService lineageService) {
        this.assetMapper = assetMapper;
        this.channelMapper = channelMapper;
        this.registryMapper = registryMapper;
        this.tableMapper = tableMapper;
        this.sourceMapper = sourceMapper;
        this.projectMapper = projectMapper;
        this.tagMapper = tagMapper;
        this.tagBindingMapper = tagBindingMapper;
        this.markMapper = markMapper;
        this.edgeMapper = edgeMapper;
        this.searchDocMapper = searchDocMapper;
        this.searchLogMapper = searchLogMapper;
        this.classifyGradeService = classifyGradeService;
        this.maskPolicyService = maskPolicyService;
        this.assetSearchService = assetSearchService;
        this.tagManageService = tagManageService;
        this.lineageService = lineageService;
    }

    public Map<String, Object> panorama() {
        Map<String, Object> classify = safeMap(() -> classifyGradeService.overview());
        Map<String, Object> mask = safeMap(() -> maskPolicyService.overview());
        Map<String, Object> search = safeMap(() -> assetSearchService.overview());
        Map<String, Object> tagCov = safeMap(() -> tagManageService.coverageReport());

        long tables = tableMapper.selectCount(null);
        long sources = sourceMapper.selectCount(null);
        long projects = projectMapper.selectCount(null);
        long channels = channelMapper.selectCount(null);
        long assets = assetMapper.selectCount(null);
        long registries = registryMapper.selectCount(
                new LambdaQueryWrapper<IngResourceRegistry>().eq(IngResourceRegistry::getPublishStatus, "PUBLISHED"));
        long tags = tagMapper.selectCount(new LambdaQueryWrapper<IngAssetTag>().eq(IngAssetTag::getStatus, "ACTIVE"));
        long bindings = tagBindingMapper.selectCount(new LambdaQueryWrapper<IngAssetTagBinding>()
                .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
        long edges = edgeMapper.selectCount(null);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("projects", projects);
        kpis.put("dataSources", sources);
        kpis.put("tables", tables);
        kpis.put("channels", channels);
        kpis.put("bizAssets", assets);
        kpis.put("publishedRegistries", registries);
        kpis.put("activeTags", tags);
        kpis.put("tagBindings", bindings);
        kpis.put("lineageEdges", edges);
        kpis.put("classifyCoveragePct", classify.getOrDefault("coveragePct", 0));
        kpis.put("markedAssets", classify.getOrDefault("markedAssets", 0));
        kpis.put("tagCoveragePct", tagCov.getOrDefault("coverageRate", 0));
        kpis.put("taggedTables", tagCov.getOrDefault("taggedTables", 0));
        kpis.put("searchDocs", search.getOrDefault("docCount", 0));
        kpis.put("searchEngine", search.getOrDefault("engine", "database"));
        kpis.put("maskPolicies", mask.getOrDefault("policyActive", 0));
        kpis.put("maskRules", mask.getOrDefault("ruleCount", 0));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kpis", kpis);
        out.put("classify", classify);
        out.put("mask", mask);
        out.put("search", search);
        out.put("tagCoverage", Map.of(
                "coverageRate", tagCov.getOrDefault("coverageRate", 0),
                "hotTags", tagCov.getOrDefault("hotTags", List.of()),
                "untaggedSamples", tagCov.getOrDefault("untaggedSamples", List.of())
        ));
        out.put("drillLinks", drillLinks());
        out.put("generatedAt", LocalDateTime.now().toString());
        // 兼容旧字段
        out.put("totalAssets", assets);
        out.put("ingestChannels", channels);
        out.put("publishedRegistries", registries);
        return out;
    }

    public List<Map<String, Object>> topAssets(Integer limit) {
        int lim = limit == null || limit < 1 ? 10 : Math.min(limit, 50);
        List<IngDataTable> tables = tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>()
                .orderByDesc(IngDataTable::getId).last("LIMIT 500"));
        Map<Long, Long> bindCnt = new HashMap<>();
        for (IngAssetTagBinding b : tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getAssetType, "TABLE")
                .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"))) {
            bindCnt.merge(b.getAssetId(), 1L, Long::sum);
        }
        Map<Long, String> levelByTable = new HashMap<>();
        for (IngClsAssetMark m : markMapper.selectList(new LambdaQueryWrapper<IngClsAssetMark>()
                .eq(IngClsAssetMark::getAssetType, "TABLE")
                .eq(IngClsAssetMark::getStatus, "ACTIVE"))) {
            levelByTable.put(m.getAssetId(), m.getLevelCode());
        }
        Map<Long, Integer> hotByTable = new HashMap<>();
        for (IngSearchDocument d : searchDocMapper.selectList(new LambdaQueryWrapper<IngSearchDocument>()
                .eq(IngSearchDocument::getDocType, "TABLE")
                .eq(IngSearchDocument::getStatus, "ACTIVE"))) {
            if (d.getAssetId() != null) {
                hotByTable.put(d.getAssetId(), d.getHotScore() == null ? 0 : d.getHotScore());
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (IngDataTable t : tables) {
            long bc = bindCnt.getOrDefault(t.getId(), 0L);
            int hot = hotByTable.getOrDefault(t.getId(), 0);
            int cols = t.getColumnCount() == null ? 0 : t.getColumnCount();
            double score = bc * 10 + hot * 5 + cols * 0.5
                    + (t.getSourceRowCount() == null ? 0 : Math.min(t.getSourceRowCount() / 1000.0, 20));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("assetType", "TABLE");
            row.put("assetId", t.getId());
            row.put("assetCode", nullTo(t.getTableCode()));
            row.put("assetName", nullTo(t.getTableName()));
            row.put("physicalTable", nullTo(t.getPhysicalTableName(), t.getSourceTable()));
            row.put("sourceId", t.getSourceId());
            row.put("columnCount", cols);
            row.put("tagBindingCount", bc);
            row.put("levelCode", levelByTable.get(t.getId()));
            row.put("hotScore", hot);
            row.put("rankScore", Math.round(score * 10) / 10.0);
            row.put("status", t.getStatus());
            rows.add(row);
        }
        rows.sort(Comparator.comparingDouble((Map<String, Object> m) -> ((Number) m.get("rankScore")).doubleValue()).reversed());
        if (rows.size() > lim) rows = new ArrayList<>(rows.subList(0, lim));

        // 补充业务资产 TOP（若登记表较少）
        if (rows.size() < lim) {
            for (BizDataAsset a : assetMapper.selectList(new LambdaQueryWrapper<BizDataAsset>().last("LIMIT " + lim))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("assetType", "BIZ_ASSET");
                row.put("assetId", a.getId());
                row.put("assetCode", a.getAssetCode());
                row.put("assetName", a.getAssetName());
                row.put("sourceSystem", a.getSourceSystem());
                row.put("status", a.getStatus());
                row.put("rankScore", 1);
                rows.add(row);
                if (rows.size() >= lim) break;
            }
        }
        return rows;
    }

    public Map<String, Object> trends(Integer days) {
        int d = days == null || days < 1 ? 14 : Math.min(days, 60);
        LocalDate start = LocalDate.now().minusDays(d - 1L);
        Map<String, Long> tableTrend = initDayBuckets(start, d);
        Map<String, Long> searchTrend = initDayBuckets(start, d);
        Map<String, Long> markTrend = initDayBuckets(start, d);

        for (IngDataTable t : tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>()
                .ge(IngDataTable::getCreatedAt, start.atStartOfDay()).last("LIMIT 5000"))) {
            if (t.getCreatedAt() != null) {
                String key = t.getCreatedAt().toLocalDate().format(DAY);
                tableTrend.computeIfPresent(key, (k, v) -> v + 1);
            }
        }
        for (IngSearchQueryLog logRow : searchLogMapper.selectList(new LambdaQueryWrapper<IngSearchQueryLog>()
                .ge(IngSearchQueryLog::getCreatedAt, start.atStartOfDay())
                .eq(IngSearchQueryLog::getActionType, "SEARCH")
                .last("LIMIT 5000"))) {
            if (logRow.getCreatedAt() != null) {
                String key = logRow.getCreatedAt().toLocalDate().format(DAY);
                searchTrend.computeIfPresent(key, (k, v) -> v + 1);
            }
        }
        for (IngClsAssetMark m : markMapper.selectList(new LambdaQueryWrapper<IngClsAssetMark>()
                .eq(IngClsAssetMark::getStatus, "ACTIVE").last("LIMIT 5000"))) {
            if (m.getGradedAt() != null && !m.getGradedAt().toLocalDate().isBefore(start)) {
                String key = m.getGradedAt().toLocalDate().format(DAY);
                markTrend.computeIfPresent(key, (k, v) -> v + 1);
            }
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (String day : tableTrend.keySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("day", day);
            point.put("newTables", tableTrend.get(day));
            point.put("searches", searchTrend.getOrDefault(day, 0L));
            point.put("newMarks", markTrend.getOrDefault(day, 0L));
            series.add(point);
        }
        return Map.of("days", d, "series", series);
    }

    public Map<String, Object> lineageSnapshot(Long projectId) {
        Long pid = projectId;
        if (pid == null) {
            IngProject first = projectMapper.selectOne(new LambdaQueryWrapper<IngProject>().orderByAsc(IngProject::getId).last("LIMIT 1"));
            if (first != null) pid = first.getId();
        }
        if (pid != null) {
            try {
                Map<String, Object> pan = lineageService.panorama(pid, null, null);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("mode", "PROJECT");
                out.put("projectId", pid);
                out.put("nodes", pan.get("nodes"));
                out.put("edges", pan.get("edges"));
                out.put("stats", pan.get("stats") != null ? pan.get("stats") : Map.of(
                        "nodeCount", sizeOf(pan.get("nodes")),
                        "edgeCount", sizeOf(pan.get("edges"))
                ));
                return out;
            } catch (Exception e) {
                log.warn("lineage panorama failed projectId={}: {}", pid, e.getMessage());
            }
        }
        // 全局边快照（无项目时）
        List<IngLineageEdge> edges = edgeMapper.selectList(new LambdaQueryWrapper<IngLineageEdge>()
                .orderByAsc(IngLineageEdge::getSortOrder).last("LIMIT 80"));
        List<Map<String, Object>> edgeViews = new ArrayList<>();
        for (IngLineageEdge e : edges) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("fromNode", e.getFromNode());
            row.put("toNode", e.getToNode());
            row.put("edgeType", e.getEdgeType());
            row.put("label", e.getEdgeType() == null ? "" : e.getEdgeType());
            row.put("fromLabel", e.getFromLabel());
            row.put("toLabel", e.getToLabel());
            edgeViews.add(row);
        }
        long sources = sourceMapper.selectCount(null);
        long tables = tableMapper.selectCount(null);
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(Map.of("id", "agg-source", "label", "数据源×" + sources, "type", "SOURCE"));
        nodes.add(Map.of("id", "agg-table", "label", "登记表×" + tables, "type", "TABLE"));
        nodes.add(Map.of("id", "agg-catalog", "label", "已发布编目", "type", "CATALOG"));
        if (edgeViews.isEmpty()) {
            edgeViews.add(Map.of("fromNode", "agg-source", "toNode", "agg-table", "edgeType", "EXTRACT", "label", "汇聚"));
            edgeViews.add(Map.of("fromNode", "agg-table", "toNode", "agg-catalog", "edgeType", "PUBLISH", "label", "编目"));
        }
        return Map.of("mode", "AGGREGATE", "projectId", pid, "nodes", nodes, "edges", edgeViews,
                "stats", Map.of("nodeCount", nodes.size(), "edgeCount", edgeViews.size()));
    }

    public Map<String, Object> facets() {
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (IngDataSource s : sourceMapper.selectList(new LambdaQueryWrapper<IngDataSource>().last("LIMIT 200"))) {
            long c = tableMapper.selectCount(new LambdaQueryWrapper<IngDataTable>().eq(IngDataTable::getSourceId, s.getId()));
            bySource.put(nullTo(s.getSourceName(), s.getSourceCode()), c);
        }
        Map<String, Long> byDim = new LinkedHashMap<>();
        for (IngAssetTag t : tagMapper.selectList(new LambdaQueryWrapper<IngAssetTag>()
                .eq(IngAssetTag::getStatus, "ACTIVE").eq(IngAssetTag::getTagSource, "CUSTOM"))) {
            String dim = t.getDimType() == null ? "OTHER" : t.getDimType();
            byDim.merge(dim, 1L, Long::sum);
        }
        Map<String, Object> byLevel = new LinkedHashMap<>();
        Object marksByLevel = classifyGradeService.overview().get("marksByLevel");
        if (marksByLevel instanceof Map<?, ?> m) {
            m.forEach((k, v) -> byLevel.put(String.valueOf(k), v));
        }
        List<Map<String, Object>> channelTypes = new ArrayList<>();
        Map<String, Long> ch = new HashMap<>();
        for (IngIngestChannel c : channelMapper.selectList(null)) {
            String typ = c.getChannelType() == null ? "UNKNOWN" : c.getChannelType();
            ch.merge(typ, 1L, Long::sum);
        }
        ch.forEach((k, v) -> channelTypes.add(Map.of("type", k, "count", v)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bySource", bySource);
        out.put("byTagDim", byDim);
        out.put("byLevel", byLevel);
        out.put("byChannelType", channelTypes);
        out.put("projects", projectMapper.selectList(new LambdaQueryWrapper<IngProject>().orderByAsc(IngProject::getId).last("LIMIT 50"))
                .stream().map(p -> Map.of("id", p.getId(), "name", nullTo(p.getProjectName(), p.getProjectCode()))).toList());
        return out;
    }

    public Map<String, Object> assetDetail(String assetType, Long assetId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assetType", assetType);
        out.put("assetId", assetId);
        if ("TABLE".equalsIgnoreCase(assetType)) {
            IngDataTable t = tableMapper.selectById(assetId);
            if (t == null) return Map.of("found", false);
            out.put("found", true);
            out.put("table", t);
            out.put("tags", tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                    .eq(IngAssetTagBinding::getAssetType, "TABLE")
                    .eq(IngAssetTagBinding::getAssetId, assetId)
                    .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED")).stream().map(b -> {
                IngAssetTag tag = tagMapper.selectById(b.getTagId());
                return Map.of("tagId", b.getTagId(), "tagName", tag == null ? "" : tag.getTagName(),
                        "source", b.getSource() == null ? "MANUAL" : b.getSource());
            }).toList());
            IngClsAssetMark mark = markMapper.selectOne(new LambdaQueryWrapper<IngClsAssetMark>()
                    .eq(IngClsAssetMark::getAssetType, "TABLE")
                    .eq(IngClsAssetMark::getAssetId, assetId)
                    .eq(IngClsAssetMark::getStatus, "ACTIVE").last("LIMIT 1"));
            out.put("classify", mark);
            out.put("globalFields", assetSearchService.globalFieldsForTable(assetId));
            out.put("drillLinks", List.of(
                    Map.of("key", "search", "label", "在数据搜索中定位", "module", "asset.search", "query", Map.of("mode", "META", "tableId", assetId)),
                    Map.of("key", "classify", "label", "分级分类标注", "module", "asset.classify"),
                    Map.of("key", "tag", "label", "标签打标", "module", "asset.tag"),
                    Map.of("key", "mask", "label", "脱敏策略", "module", "asset.mask")
            ));
            return out;
        }
        return Map.of("found", false);
    }

    private List<Map<String, Object>> drillLinks() {
        List<Map<String, Object>> links = new ArrayList<>();
        links.add(Map.of("key", "search", "label", "数据搜索门户", "module", "asset.search", "desc", "模糊/精确/元数据/组合检索"));
        links.add(Map.of("key", "classify", "label", "数据分级分类", "module", "asset.classify", "desc", "级别分布与标注"));
        links.add(Map.of("key", "tag", "label", "数据标签管理", "module", "asset.tag", "desc", "标签覆盖与打标"));
        links.add(Map.of("key", "mask", "label", "数据脱敏策略", "module", "asset.mask", "desc", "场景策略与预览"));
        links.add(Map.of("key", "backup", "label", "数据备份", "module", "asset.backup", "desc", "备份策略"));
        links.add(Map.of("key", "archive", "label", "数据归档", "module", "asset.archive", "desc", "归档策略"));
        return links;
    }

    private Map<String, Long> initDayBuckets(LocalDate start, int days) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            map.put(start.plusDays(i).format(DAY), 0L);
        }
        return map;
    }

    private Map<String, Object> safeMap(SupplierMap supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("panorama sub-overview failed: {}", e.getMessage());
            return Map.of();
        }
    }

    @FunctionalInterface
    private interface SupplierMap {
        Map<String, Object> get();
    }

    private static int sizeOf(Object o) {
        if (o instanceof List<?> l) return l.size();
        return 0;
    }

    private static String nullTo(String s) {
        return s == null ? "" : s;
    }

    private static String nullTo(String s, String def) {
        return s == null || s.isBlank() ? (def == null ? "" : def) : s;
    }
}
