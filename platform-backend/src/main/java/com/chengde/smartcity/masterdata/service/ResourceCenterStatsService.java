package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.masterdata.entity.RcAssetCatalogEntry;
import com.chengde.smartcity.masterdata.entity.RcBaseLibrary;
import com.chengde.smartcity.masterdata.entity.RcManagedTable;
import com.chengde.smartcity.masterdata.entity.RcPartitionDef;
import com.chengde.smartcity.masterdata.entity.RcPolicyRunLog;
import com.chengde.smartcity.masterdata.entity.RcStoragePolicy;
import com.chengde.smartcity.masterdata.entity.RcThemeLibrary;
import com.chengde.smartcity.masterdata.mapper.RcAssetCatalogEntryMapper;
import com.chengde.smartcity.masterdata.mapper.RcBaseLibraryMapper;
import com.chengde.smartcity.masterdata.mapper.RcManagedTableMapper;
import com.chengde.smartcity.masterdata.mapper.RcPartitionDefMapper;
import com.chengde.smartcity.masterdata.mapper.RcPolicyRunLogMapper;
import com.chengde.smartcity.masterdata.mapper.RcStoragePolicyMapper;
import com.chengde.smartcity.masterdata.mapper.RcThemeLibraryMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 大数据平台资源中心 · 数据库统计分析（V3.0 1.1.2.3.2.5）。
 * 四层能力：汇总聚合 → 深入分析（趋势/回归/离群/集中度） → 可视化数据 → 决策支持建议。
 * 全部指标来自资源中心已登记/纳管的真实台账（rc_*），不生成模拟数据。
 */
@Service
public class ResourceCenterStatsService {

    private static final Logger log = LoggerFactory.getLogger(ResourceCenterStatsService.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 大表判定：行数阈值 */
    private static final long BIG_TABLE_ROWS = 1_000_000L;
    /** 大表判定：容量阈值（1GB） */
    private static final long BIG_TABLE_BYTES = 1024L * 1024L * 1024L;
    /** 索引膨胀判定：索引字节占比 */
    private static final double INDEX_HEAVY_RATIO = 0.5D;
    /** 容量统计陈旧天数 */
    private static final int STALE_STAT_DAYS = 30;

    private final RcBaseLibraryMapper libraryMapper;
    private final RcThemeLibraryMapper themeMapper;
    private final RcManagedTableMapper managedTableMapper;
    private final RcAssetCatalogEntryMapper catalogMapper;
    private final RcStoragePolicyMapper policyMapper;
    private final RcPartitionDefMapper partitionMapper;
    private final RcPolicyRunLogMapper policyRunLogMapper;

    public ResourceCenterStatsService(RcBaseLibraryMapper libraryMapper,
                                      RcThemeLibraryMapper themeMapper,
                                      RcManagedTableMapper managedTableMapper,
                                      RcAssetCatalogEntryMapper catalogMapper,
                                      RcStoragePolicyMapper policyMapper,
                                      RcPartitionDefMapper partitionMapper,
                                      RcPolicyRunLogMapper policyRunLogMapper) {
        this.libraryMapper = libraryMapper;
        this.themeMapper = themeMapper;
        this.managedTableMapper = managedTableMapper;
        this.catalogMapper = catalogMapper;
        this.policyMapper = policyMapper;
        this.partitionMapper = partitionMapper;
        this.policyRunLogMapper = policyRunLogMapper;
    }

    // ==================== 一、汇总聚合与描述性统计 ====================

    public Map<String, Object> summary(String libType, String assetType) {
        List<StatRow> rows = loadRows(libType, assetType);
        List<RcAssetCatalogEntry> catalogEntries = catalogMapper.selectList(null);
        List<RcStoragePolicy> policies = policyMapper.selectList(null);
        List<RcPartitionDef> partitions = partitionMapper.selectList(null);
        List<RcBaseLibrary> libraries = libraryMapper.selectList(null);

        long totalRows = 0;
        long totalBytes = 0;
        long totalIndexBytes = 0;
        for (StatRow r : rows) {
            totalRows += r.rows;
            totalBytes += r.bytes;
            totalIndexBytes += r.indexBytes;
        }
        long libraryRegisteredRows = 0;
        for (RcBaseLibrary l : libraries) {
            libraryRegisteredRows += l.getRecordCount() == null ? 0 : l.getRecordCount();
        }
        long publishedCatalog = catalogEntries.stream()
                .filter(e -> "PUBLISHED".equalsIgnoreCase(nvl(e.getPublishStatus()))).count();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("libraryCount", libraries.size());
        overview.put("themeCount", themeMapper.selectCount(null));
        overview.put("managedTableCount", rows.size());
        overview.put("totalRecords", totalRows);
        overview.put("totalBytes", totalBytes);
        overview.put("indexBytes", totalIndexBytes);
        overview.put("libraryRegisteredRecords", libraryRegisteredRows);
        overview.put("catalogEntryCount", catalogEntries.size());
        overview.put("publishedCatalogCount", publishedCatalog);
        overview.put("policyCount", policies.size());
        overview.put("partitionDefCount", partitions.size());
        overview.put("emptyTableCount", rows.stream().filter(r -> r.rows <= 0).count());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", LocalDateTime.now().format(TS_FMT));
        out.put("scopeHint", "统计口径：资源中心已纳管（启用）表快照 + 库/主题登记台账；容量取最近一次「资源监控管理 · 刷新真实容量」写回值。");
        out.put("filter", Map.of("libType", nvl(libType), "assetType", nvl(assetType)));
        out.put("overview", overview);
        out.put("descriptive", describeAll(rows));
        out.put("byLibType", groupBy(rows, r -> r.libType, totalRows));
        out.put("byAssetType", groupBy(rows, r -> r.assetType, totalRows));
        out.put("byTheme", limit(groupBy(rows, r -> r.themeName, totalRows), 10));
        out.put("byOwnerOrg", limit(groupBy(rows, r -> r.ownerOrg, totalRows), 10));
        out.put("topTables", tableRows(sortedByRows(rows, true), 10, totalRows));
        out.put("bottomTables", tableRows(sortedByRows(rows, false), 10, totalRows));
        out.put("catalogPublish", countBy(catalogEntries, e -> upper(nvl(e.getPublishStatus(), "DRAFT"))));
        out.put("policyAction", countBy(policies, p -> upper(nvl(p.getActionType(), "BACKUP"))));
        return out;
    }

    // ==================== 二、深入分析：趋势 / 回归 / 集中度 / 离群 ====================

    public Map<String, Object> analysis(Integer months) {
        int window = months == null || months < 3 || months > 36 ? 12 : months;
        List<StatRow> rows = loadRows(null, null);
        List<Map<String, Object>> trend = monthlyTrend(rows, window);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", LocalDateTime.now().format(TS_FMT));
        out.put("months", window);
        out.put("trend", trend);
        out.put("recordForecast", linearForecast(trend, "cumulativeRecords", "累计记录数", 3));
        out.put("tableForecast", linearForecast(trend, "cumulativeTables", "累计纳管表数", 3));
        out.put("concentration", concentration(rows));
        out.put("outliers", outliers(rows));
        out.put("jobTrend", jobTrend(window));
        out.put("methodNotes", List.of(
                "趋势：按纳管登记时间（created_at）做月度分桶，记录数/容量取当前快照值累计，反映资产入池节奏。",
                "预测：对累计序列做一元线性回归（最小二乘），R² 越接近 1 拟合越可信；R² < 0.5 时预测仅供参考。",
                "集中度：赫芬达尔指数 HHI 与基尼系数衡量记录量在表间的分布均衡度，HHI > 2500 视为高度集中。",
                "离群：均值 + 3σ 与四分位距 IQR（Q3 + 1.5×IQR）双判据取并集，并单列空表与索引膨胀表。",
                "作业趋势：来自数据库存储管理的策略执行日志（rc_policy_run_log），台账处理不计为落盘成功。"));
        return out;
    }

    // ==================== 三、决策支持 ====================

    public Map<String, Object> decisions() {
        List<StatRow> rows = loadRows(null, null);
        List<RcAssetCatalogEntry> catalogEntries = catalogMapper.selectList(null);
        List<RcStoragePolicy> policies = policyMapper.selectList(null);
        List<RcPartitionDef> partitions = partitionMapper.selectList(null);

        Set<String> partitionedTables = new HashSet<>();
        for (RcPartitionDef p : partitions) {
            if (p.getTableName() != null && !p.getTableName().isBlank()) {
                partitionedTables.add(p.getTableName().toLowerCase(Locale.ROOT));
            }
        }
        Set<Long> policyTableIds = new HashSet<>();
        Set<Long> policyThemeIds = new HashSet<>();
        for (RcStoragePolicy p : policies) {
            if (p.getManagedTableId() != null) policyTableIds.add(p.getManagedTableId());
            if (p.getThemeId() != null) policyThemeIds.add(p.getThemeId());
        }
        Set<Long> catalogedTableIds = new HashSet<>();
        for (RcAssetCatalogEntry e : catalogEntries) {
            if (e.getManagedTableId() != null) catalogedTableIds.add(e.getManagedTableId());
        }

        List<StatRow> bigTables = new ArrayList<>();
        List<StatRow> bigNoPartition = new ArrayList<>();
        List<StatRow> bigNoPolicy = new ArrayList<>();
        List<StatRow> emptyTables = new ArrayList<>();
        List<StatRow> notCataloged = new ArrayList<>();
        List<StatRow> indexHeavy = new ArrayList<>();
        List<StatRow> staleStats = new ArrayList<>();
        LocalDateTime staleLine = LocalDateTime.now().minusDays(STALE_STAT_DAYS);
        for (StatRow r : rows) {
            boolean big = r.rows >= BIG_TABLE_ROWS || r.bytes >= BIG_TABLE_BYTES;
            if (big) {
                bigTables.add(r);
                if (!partitionedTables.contains(r.physicalTable.toLowerCase(Locale.ROOT))) bigNoPartition.add(r);
                if (!policyTableIds.contains(r.id) && (r.themeId == null || !policyThemeIds.contains(r.themeId))) {
                    bigNoPolicy.add(r);
                }
            }
            if (r.rows <= 0) emptyTables.add(r);
            if (!catalogedTableIds.contains(r.id)) notCataloged.add(r);
            if (r.bytes > 0 && r.indexBytes > 0 && (double) r.indexBytes / r.bytes > INDEX_HEAVY_RATIO) indexHeavy.add(r);
            if (r.updatedAt == null || r.updatedAt.isBefore(staleLine)) staleStats.add(r);
        }
        long pendingCatalog = catalogEntries.stream()
                .filter(e -> {
                    String ps = upper(nvl(e.getPublishStatus(), "DRAFT"));
                    return "DRAFT".equals(ps) || "PENDING_REVIEW".equals(ps) || "REJECTED".equals(ps);
                }).count();
        List<RcPolicyRunLog> recentRuns = policyRunLogMapper.selectList(new LambdaQueryWrapper<RcPolicyRunLog>()
                .orderByDesc(RcPolicyRunLog::getId).last("LIMIT 50"));
        long abnormalRuns = recentRuns.stream()
                .filter(r -> {
                    String s = upper(nvl(r.getRunStatus(), ""));
                    return "FAILED".equals(s) || "ERROR".equals(s) || "LEDGER".equals(s);
                }).count();
        Map<String, Object> conc = concentration(rows);
        double top1Share = numOf(conc.get("top1Share"));

        List<Map<String, Object>> recs = new ArrayList<>();
        if (!bigNoPartition.isEmpty()) {
            recs.add(recommendation("PARTITION_MISSING", "大表尚未配置分区策略", "HIGH",
                    bigNoPartition.size() + " 张大表未建分区",
                    "行数 ≥ " + BIG_TABLE_ROWS + " 或容量 ≥ 1GB，且在分区设计管理中无对应分区定义，全表扫描与归档窗口风险高。",
                    "前往「分区设计管理」按时间或哈希列建分区并预检 DDL",
                    "/resource-center?tab=partition", sampleNames(bigNoPartition)));
        }
        if (!bigNoPolicy.isEmpty()) {
            recs.add(recommendation("POLICY_MISSING", "大表缺少备份或归档策略", "HIGH",
                    bigNoPolicy.size() + " 张大表无存储策略",
                    "数据库存储管理中未找到绑定该表或其主题的备份/归档/销毁策略，数据丢失后无可恢复产物。",
                    "前往「数据库存储管理」创建备份策略并开启定时调度",
                    "/resource-center?tab=storage", sampleNames(bigNoPolicy)));
        }
        if (!notCataloged.isEmpty()) {
            recs.add(recommendation("CATALOG_MISSING", "已纳管资产尚未编目", "MEDIUM",
                    notCataloged.size() + " 张纳管表无资产目录",
                    "未编目资产无法进入目录门户被检索与申请，纳管投入未转化为共享供给。",
                    "前往「资产目录管理」为这些表编目并提交公开审批",
                    "/resource-center?tab=catalog", sampleNames(notCataloged)));
        }
        if (pendingCatalog > 0) {
            recs.add(recommendation("CATALOG_PENDING", "资产目录发布流程存在积压", "MEDIUM",
                    pendingCatalog + " 条目录处于草稿/待审/驳回",
                    "目录停留在未发布状态，门户侧不可见，影响共享交换与供需匹配时效。",
                    "前往「资产目录管理」推进提交与审批",
                    "/resource-center?tab=catalog", List.of()));
        }
        if (!emptyTables.isEmpty()) {
            recs.add(recommendation("EMPTY_TABLE", "存在零记录纳管表", "MEDIUM",
                    emptyTables.size() + " 张纳管表记录数为 0",
                    "可能是采集通道未跑通、治理任务未落库，或容量统计从未刷新过。",
                    "先在「资源监控管理」刷新真实容量确认，再回采集汇聚核查任务",
                    "/resource-center?tab=monitor", sampleNames(emptyTables)));
        }
        if (!indexHeavy.isEmpty()) {
            recs.add(recommendation("INDEX_HEAVY", "索引空间占比偏高", "LOW",
                    indexHeavy.size() + " 张表索引占比超 50%",
                    "索引字节超过表总容量一半，通常意味着存在冗余索引，写入放大与存储成本上升。",
                    "结合「数据库检索查询」的实际访问方式复核索引必要性",
                    "/resource-center?tab=search", sampleNames(indexHeavy)));
        }
        if (!staleStats.isEmpty()) {
            recs.add(recommendation("STALE_STATS", "容量统计信息陈旧", "LOW",
                    staleStats.size() + " 张表超过 " + STALE_STAT_DAYS + " 天未刷新",
                    "行数与容量为历史快照，据此做的分析结论可能滞后于真实库表状态。",
                    "前往「资源监控管理」执行刷新真实容量",
                    "/resource-center?tab=monitor", sampleNames(staleStats)));
        }
        if (abnormalRuns > 0) {
            recs.add(recommendation("POLICY_RUN_ABNORMAL", "存储策略执行存在异常或台账处理", "MEDIUM",
                    "近 50 次执行中 " + abnormalRuns + " 次失败或仅登记台账",
                    "销毁类动作按台账处理不做物理删除属预期；失败记录需人工介入，否则保留期策略形同虚设。",
                    "前往「数据库存储管理 · 执行策略监控」查看执行日志",
                    "/resource-center?tab=storage", List.of()));
        }
        if (top1Share >= 60 && rows.size() >= 3) {
            recs.add(recommendation("CONCENTRATION_HIGH", "数据量高度集中于单表", "MEDIUM",
                    "记录量最大的表占比 " + round2(top1Share) + "%",
                    "单表承载绝大多数记录，查询与备份窗口集中在一个对象上，扩展性与容灾能力受限。",
                    "评估按时间分区或拆分主题库，前往「分区设计管理」",
                    "/resource-center?tab=partition", sampleNames(limitRows(sortedByRows(rows, true), 3))));
        }

        int score = 100;
        for (Map<String, Object> r : recs) {
            String level = String.valueOf(r.get("level"));
            score -= "HIGH".equals(level) ? 15 : "MEDIUM".equals(level) ? 8 : 4;
        }
        if (score < 10) score = 10;
        String scoreLevel = score >= 85 ? "OK" : score >= 60 ? "WARN" : "CRITICAL";

        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("managedTableCount", rows.size());
        coverage.put("bigTableCount", bigTables.size());
        coverage.put("catalogCoverage", ratio(rows.size() - notCataloged.size(), rows.size()));
        coverage.put("policyCoverage", ratio(bigTables.size() - bigNoPolicy.size(), bigTables.size()));
        coverage.put("partitionCoverage", ratio(bigTables.size() - bigNoPartition.size(), bigTables.size()));
        coverage.put("statFreshness", ratio(rows.size() - staleStats.size(), rows.size()));

        log.debug("资源中心统计分析决策：纳管表 {} 张，识别待办 {} 项", rows.size(), recs.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", LocalDateTime.now().format(TS_FMT));
        out.put("healthScore", score);
        out.put("healthLevel", scoreLevel);
        out.put("summaryText", recs.isEmpty()
                ? "当前纳管资产在分区、备份、编目与统计新鲜度四项检查上均未发现待办事项。"
                : "共识别 " + recs.size() + " 项待办，其中高优先级 "
                        + recs.stream().filter(r -> "HIGH".equals(r.get("level"))).count() + " 项。");
        out.put("coverage", coverage);
        out.put("recommendations", recs);
        return out;
    }

    // ==================== 内部计算 ====================

    private List<StatRow> loadRows(String libType, String assetType) {
        List<RcManagedTable> tables = managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getStatus, "ACTIVE"));
        Map<Long, RcBaseLibrary> libs = new HashMap<>();
        for (RcBaseLibrary l : libraryMapper.selectList(null)) {
            libs.put(l.getId(), l);
        }
        Map<Long, RcThemeLibrary> themes = new HashMap<>();
        for (RcThemeLibrary t : themeMapper.selectList(null)) {
            themes.put(t.getId(), t);
        }
        String wantLib = libType == null || libType.isBlank() ? null : upper(libType);
        String wantAsset = assetType == null || assetType.isBlank() ? null : upper(assetType);

        List<StatRow> out = new ArrayList<>();
        for (RcManagedTable t : tables) {
            StatRow r = new StatRow();
            r.id = t.getId();
            r.themeId = t.getThemeId();
            r.physicalTable = nvl(t.getPhysicalTable(), "-");
            r.assetType = upper(nvl(t.getAssetType(), "BASE"));
            RcBaseLibrary lib = t.getLibId() == null ? null : libs.get(t.getLibId());
            RcThemeLibrary theme = t.getThemeId() == null ? null : themes.get(t.getThemeId());
            r.libName = lib == null ? null : lib.getLibName();
            r.libType = lib != null && lib.getLibType() != null && !lib.getLibType().isBlank()
                    ? upper(lib.getLibType())
                    : (theme != null && theme.getLibraryKind() != null && !theme.getLibraryKind().isBlank()
                    ? upper(theme.getLibraryKind()) : r.assetType);
            r.themeName = theme != null ? nvl(theme.getThemeName(), "未归属主题") : "未归属主题";
            r.ownerOrg = theme != null && theme.getOwnerOrg() != null && !theme.getOwnerOrg().isBlank()
                    ? theme.getOwnerOrg()
                    : (lib != null && lib.getOwnerOrg() != null && !lib.getOwnerOrg().isBlank() ? lib.getOwnerOrg() : "未填报责任单位");
            r.rows = t.getRecordCount() == null ? 0 : t.getRecordCount();
            long data = t.getDataBytes() == null ? 0 : t.getDataBytes();
            r.indexBytes = t.getIndexBytes() == null ? 0 : t.getIndexBytes();
            r.bytes = data + r.indexBytes;
            r.createdAt = t.getCreatedAt();
            r.updatedAt = t.getUpdatedAt();
            if (wantLib != null && !wantLib.equals(r.libType)) continue;
            if (wantAsset != null && !wantAsset.equals(r.assetType)) continue;
            out.add(r);
        }
        return out;
    }

    private List<Map<String, Object>> describeAll(List<StatRow> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Double> rowValues = new ArrayList<>();
        List<Double> byteValues = new ArrayList<>();
        List<String> widthLabels = new ArrayList<>();
        List<Double> widthValues = new ArrayList<>();
        for (StatRow r : rows) {
            labels.add(r.physicalTable);
            rowValues.add((double) r.rows);
            byteValues.add((double) r.bytes);
            if (r.rows > 0 && r.bytes > 0) {
                widthLabels.add(r.physicalTable);
                widthValues.add((double) r.bytes / r.rows);
            }
        }
        out.add(describe("recordCount", "纳管表记录数", "行", labels, rowValues));
        out.add(describe("totalBytes", "纳管表占用容量", "字节", labels, byteValues));
        out.add(describe("avgRowBytes", "单行平均字节", "字节/行", widthLabels, widthValues));
        return out;
    }

    private Map<String, Object> describe(String key, String name, String unit,
                                         List<String> labels, List<Double> values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metricKey", key);
        m.put("metricName", name);
        m.put("unit", unit);
        m.put("count", values.size());
        if (values.isEmpty()) {
            m.put("sum", 0);
            m.put("avg", 0);
            m.put("median", 0);
            m.put("p90", 0);
            m.put("max", 0);
            m.put("min", 0);
            m.put("stdDev", 0);
            m.put("cv", 0);
            m.put("maxObject", null);
            m.put("minObject", null);
            return m;
        }
        double sum = 0;
        int maxIdx = 0;
        int minIdx = 0;
        for (int i = 0; i < values.size(); i++) {
            sum += values.get(i);
            if (values.get(i) > values.get(maxIdx)) maxIdx = i;
            if (values.get(i) < values.get(minIdx)) minIdx = i;
        }
        double avg = sum / values.size();
        double variance = 0;
        for (Double v : values) {
            variance += (v - avg) * (v - avg);
        }
        variance /= values.size();
        double stdDev = Math.sqrt(variance);
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        m.put("sum", round2(sum));
        m.put("avg", round2(avg));
        m.put("median", round2(percentile(sorted, 50)));
        m.put("p90", round2(percentile(sorted, 90)));
        m.put("max", round2(values.get(maxIdx)));
        m.put("min", round2(values.get(minIdx)));
        m.put("stdDev", round2(stdDev));
        m.put("cv", avg == 0 ? 0 : round2(stdDev / avg * 100));
        m.put("maxObject", labels.get(maxIdx));
        m.put("minObject", labels.get(minIdx));
        return m;
    }

    private List<Map<String, Object>> groupBy(List<StatRow> rows, KeyFn fn, long totalRows) {
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (StatRow r : rows) {
            String key = fn.apply(r);
            if (key == null || key.isBlank()) key = "UNKNOWN";
            long[] v = agg.computeIfAbsent(key, k -> new long[3]);
            v[0] += 1;
            v[1] += r.rows;
            v[2] += r.bytes;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", e.getKey());
            m.put("tableCount", e.getValue()[0]);
            m.put("recordCount", e.getValue()[1]);
            m.put("bytes", e.getValue()[2]);
            m.put("share", ratio(e.getValue()[1], totalRows));
            out.add(m);
        }
        out.sort((a, b) -> Long.compare(numLong(b.get("recordCount")), numLong(a.get("recordCount"))));
        return out;
    }

    private List<Map<String, Object>> countBy(List<RcAssetCatalogEntry> entries, CatalogKeyFn fn) {
        Map<String, Long> agg = new LinkedHashMap<>();
        for (RcAssetCatalogEntry e : entries) {
            agg.merge(fn.apply(e), 1L, Long::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : agg.entrySet()) {
            out.add(Map.of("code", e.getKey(), "count", e.getValue()));
        }
        return out;
    }

    private List<Map<String, Object>> countBy(List<RcStoragePolicy> policies, PolicyKeyFn fn) {
        Map<String, Long> agg = new LinkedHashMap<>();
        for (RcStoragePolicy p : policies) {
            agg.merge(fn.apply(p), 1L, Long::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : agg.entrySet()) {
            out.add(Map.of("code", e.getKey(), "count", e.getValue()));
        }
        return out;
    }

    private List<Map<String, Object>> monthlyTrend(List<StatRow> rows, int months) {
        LocalDate now = LocalDate.now().withDayOfMonth(1);
        LocalDate start = now.minusMonths(months - 1L);
        List<String> periods = new ArrayList<>();
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            String p = start.plusMonths(i).format(MONTH_FMT);
            periods.add(p);
            buckets.put(p, new long[3]);
        }
        long baseTables = 0;
        long baseRecords = 0;
        long baseBytes = 0;
        for (StatRow r : rows) {
            LocalDate created = r.createdAt == null ? null : r.createdAt.toLocalDate().withDayOfMonth(1);
            if (created == null || created.isBefore(start)) {
                baseTables += 1;
                baseRecords += r.rows;
                baseBytes += r.bytes;
                continue;
            }
            String key = created.isAfter(now) ? now.format(MONTH_FMT) : created.format(MONTH_FMT);
            long[] v = buckets.get(key);
            if (v == null) continue;
            v[0] += 1;
            v[1] += r.rows;
            v[2] += r.bytes;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        long cumTables = baseTables;
        long cumRecords = baseRecords;
        long cumBytes = baseBytes;
        for (String p : periods) {
            long[] v = buckets.get(p);
            cumTables += v[0];
            cumRecords += v[1];
            cumBytes += v[2];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", p);
            m.put("newTables", v[0]);
            m.put("newRecords", v[1]);
            m.put("newBytes", v[2]);
            m.put("cumulativeTables", cumTables);
            m.put("cumulativeRecords", cumRecords);
            m.put("cumulativeBytes", cumBytes);
            out.add(m);
        }
        return out;
    }

    /** 一元线性回归（最小二乘）+ 未来 n 期外推 */
    private Map<String, Object> linearForecast(List<Map<String, Object>> trend, String field,
                                               String fieldName, int aheadPeriods) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metricKey", field);
        m.put("metricName", fieldName);
        int n = trend.size();
        if (n < 3) {
            m.put("available", false);
            m.put("hint", "样本不足 3 期，暂不做回归预测。");
            m.put("forecast", List.of());
            return m;
        }
        double sumX = 0;
        double sumY = 0;
        double sumXy = 0;
        double sumXx = 0;
        for (int i = 0; i < n; i++) {
            double y = numOf(trend.get(i).get(field));
            sumX += i;
            sumY += y;
            sumXy += i * y;
            sumXx += (double) i * i;
        }
        double denominator = n * sumXx - sumX * sumX;
        double slope = denominator == 0 ? 0 : (n * sumXy - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;
        double meanY = sumY / n;
        double ssTot = 0;
        double ssRes = 0;
        for (int i = 0; i < n; i++) {
            double y = numOf(trend.get(i).get(field));
            double fit = intercept + slope * i;
            ssTot += (y - meanY) * (y - meanY);
            ssRes += (y - fit) * (y - fit);
        }
        double r2 = ssTot == 0 ? 1 : 1 - ssRes / ssTot;
        double first = numOf(trend.get(0).get(field));
        double last = numOf(trend.get(n - 1).get(field));
        double growthRate = first <= 0 ? 0 : (Math.pow(last / first, 1.0 / (n - 1)) - 1) * 100;

        List<Map<String, Object>> forecast = new ArrayList<>();
        LocalDate lastPeriod = LocalDate.parse(trend.get(n - 1).get("period") + "-01");
        for (int i = 1; i <= aheadPeriods; i++) {
            double v = intercept + slope * (n - 1 + i);
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("period", lastPeriod.plusMonths(i).format(MONTH_FMT));
            f.put("value", Math.max(0, round2(v)));
            forecast.add(f);
        }
        String reliability = r2 >= 0.8 ? "HIGH" : r2 >= 0.5 ? "MEDIUM" : "LOW";
        m.put("available", true);
        m.put("slopePerMonth", round2(slope));
        m.put("intercept", round2(intercept));
        m.put("r2", round2(r2));
        m.put("reliability", reliability);
        m.put("monthlyGrowthRate", round2(growthRate));
        m.put("forecast", forecast);
        m.put("hint", "LOW".equals(reliability)
                ? "序列波动较大（R² < 0.5），线性外推仅作参考，建议积累更多月度样本。"
                : "按最近 " + n + " 期线性拟合外推未来 " + aheadPeriods + " 个月。");
        return m;
    }

    private Map<String, Object> concentration(List<StatRow> rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<StatRow> sorted = sortedByRows(rows, true);
        long total = 0;
        for (StatRow r : rows) {
            total += r.rows;
        }
        m.put("tableCount", rows.size());
        m.put("totalRecords", total);
        if (rows.isEmpty() || total <= 0) {
            m.put("available", false);
            m.put("top1Share", 0);
            m.put("top3Share", 0);
            m.put("top5Share", 0);
            m.put("hhi", 0);
            m.put("gini", 0);
            m.put("level", "UNKNOWN");
            m.put("hint", "尚无记录量数据，请先在「资源监控管理」刷新真实容量。");
            return m;
        }
        double hhi = 0;
        for (StatRow r : rows) {
            double share = (double) r.rows / total;
            hhi += share * share;
        }
        hhi *= 10000;
        long top1 = sorted.isEmpty() ? 0 : sorted.get(0).rows;
        long top3 = 0;
        long top5 = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < 3) top3 += sorted.get(i).rows;
            if (i < 5) top5 += sorted.get(i).rows;
        }
        List<Long> asc = new ArrayList<>();
        for (StatRow r : rows) {
            asc.add(r.rows);
        }
        asc.sort(Comparator.naturalOrder());
        double giniNumerator = 0;
        for (int i = 0; i < asc.size(); i++) {
            giniNumerator += (2.0 * (i + 1) - asc.size() - 1) * asc.get(i);
        }
        double gini = giniNumerator / ((double) asc.size() * total);
        String level = hhi >= 2500 ? "HIGH" : hhi >= 1500 ? "MEDIUM" : "LOW";
        m.put("available", true);
        m.put("top1Share", ratio(top1, total));
        m.put("top3Share", ratio(top3, total));
        m.put("top5Share", ratio(top5, total));
        m.put("topTable", sorted.isEmpty() ? null : sorted.get(0).physicalTable);
        m.put("hhi", round2(hhi));
        m.put("gini", round2(gini));
        m.put("level", level);
        m.put("hint", "HIGH".equals(level)
                ? "记录量高度集中，建议对头部表做分区或主题拆分。"
                : "MEDIUM".equals(level) ? "记录量存在一定集中，关注头部表增长。" : "记录量在纳管表间分布相对均衡。");
        return m;
    }

    private List<Map<String, Object>> outliers(List<StatRow> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        List<Double> values = new ArrayList<>();
        for (StatRow r : rows) {
            values.add((double) r.rows);
        }
        double sum = 0;
        for (Double v : values) {
            sum += v;
        }
        double avg = sum / values.size();
        double variance = 0;
        for (Double v : values) {
            variance += (v - avg) * (v - avg);
        }
        double stdDev = Math.sqrt(variance / values.size());
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        double q1 = percentile(sorted, 25);
        double q3 = percentile(sorted, 75);
        double iqrHigh = q3 + 1.5 * (q3 - q1);
        double sigmaHigh = avg + 3 * stdDev;

        for (StatRow r : rows) {
            List<String> reasons = new ArrayList<>();
            String level = "LOW";
            if (stdDev > 0 && r.rows > sigmaHigh) {
                reasons.add("超出均值+3σ");
                level = "HIGH";
            }
            if (q3 > q1 && r.rows > iqrHigh) {
                reasons.add("超出 Q3+1.5×IQR");
                if (!"HIGH".equals(level)) level = "MEDIUM";
            }
            if (r.rows <= 0) {
                reasons.add("零记录");
                if (!"HIGH".equals(level)) level = "MEDIUM";
            }
            if (r.bytes > 0 && r.indexBytes > 0 && (double) r.indexBytes / r.bytes > INDEX_HEAVY_RATIO) {
                reasons.add("索引占比超 50%");
            }
            if (reasons.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("physicalTable", r.physicalTable);
            m.put("themeName", r.themeName);
            m.put("recordCount", r.rows);
            m.put("bytes", r.bytes);
            m.put("indexRatio", r.bytes <= 0 ? 0 : ratio(r.indexBytes, r.bytes));
            m.put("zScore", stdDev == 0 ? 0 : round2((r.rows - avg) / stdDev));
            m.put("level", level);
            m.put("reason", String.join(" · ", reasons));
            out.add(m);
        }
        out.sort((a, b) -> Double.compare(Math.abs(numOf(b.get("zScore"))), Math.abs(numOf(a.get("zScore")))));
        return limit(out, 20);
    }

    private List<Map<String, Object>> jobTrend(int months) {
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1L);
        List<RcPolicyRunLog> logs = policyRunLogMapper.selectList(new LambdaQueryWrapper<RcPolicyRunLog>()
                .ge(RcPolicyRunLog::getCreatedAt, start.atStartOfDay())
                .orderByAsc(RcPolicyRunLog::getCreatedAt));
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            buckets.put(start.plusMonths(i).format(MONTH_FMT), new long[4]);
        }
        for (RcPolicyRunLog l : logs) {
            if (l.getCreatedAt() == null) continue;
            long[] v = buckets.get(l.getCreatedAt().toLocalDate().withDayOfMonth(1).format(MONTH_FMT));
            if (v == null) continue;
            v[0] += 1;
            String s = upper(nvl(l.getRunStatus(), ""));
            if ("SUCCESS".equals(s)) v[1] += 1;
            else if ("LEDGER".equals(s)) v[2] += 1;
            v[3] += l.getRowCount() == null ? 0 : l.getRowCount();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : buckets.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", e.getKey());
            m.put("runCount", e.getValue()[0]);
            m.put("successCount", e.getValue()[1]);
            m.put("ledgerCount", e.getValue()[2]);
            m.put("rowCount", e.getValue()[3]);
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> tableRows(List<StatRow> sorted, int size, long totalRows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < sorted.size() && i < size; i++) {
            StatRow r = sorted.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("physicalTable", r.physicalTable);
            m.put("themeName", r.themeName);
            m.put("libName", r.libName);
            m.put("libType", r.libType);
            m.put("assetType", r.assetType);
            m.put("ownerOrg", r.ownerOrg);
            m.put("recordCount", r.rows);
            m.put("bytes", r.bytes);
            m.put("indexRatio", r.bytes <= 0 ? 0 : ratio(r.indexBytes, r.bytes));
            m.put("share", ratio(r.rows, totalRows));
            m.put("updatedAt", r.updatedAt == null ? null : r.updatedAt.format(TS_FMT));
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> recommendation(String code, String title, String level, String metric,
                                               String basis, String action, String link, List<String> objects) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("title", title);
        m.put("level", level);
        m.put("metric", metric);
        m.put("basis", basis);
        m.put("action", action);
        m.put("link", link);
        m.put("objects", objects);
        return m;
    }

    private List<String> sampleNames(List<StatRow> rows) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rows.size() && i < 5; i++) {
            out.add(rows.get(i).physicalTable);
        }
        return out;
    }

    private List<StatRow> sortedByRows(List<StatRow> rows, boolean desc) {
        List<StatRow> copy = new ArrayList<>(rows);
        copy.sort(desc
                ? (a, b) -> Long.compare(b.rows, a.rows)
                : (a, b) -> Long.compare(a.rows, b.rows));
        return copy;
    }

    private static List<StatRow> limitRows(List<StatRow> rows, int size) {
        return rows.size() <= size ? rows : new ArrayList<>(rows.subList(0, size));
    }

    private static List<Map<String, Object>> limit(List<Map<String, Object>> list, int size) {
        return list.size() <= size ? list : new ArrayList<>(list.subList(0, size));
    }

    private static double percentile(List<Double> sortedAsc, int percent) {
        if (sortedAsc.isEmpty()) return 0;
        if (sortedAsc.size() == 1) return sortedAsc.get(0);
        double pos = (percent / 100.0) * (sortedAsc.size() - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);
        if (lower == upper) return sortedAsc.get(lower);
        double weight = pos - lower;
        return sortedAsc.get(lower) * (1 - weight) + sortedAsc.get(upper) * weight;
    }

    private static double ratio(long part, long total) {
        if (total <= 0) return 0;
        return round2((double) part / total * 100);
    }

    private static double round2(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        return Math.round(v * 100.0) / 100.0;
    }

    private static double numOf(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    private static long numLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    private static String upper(String v) {
        return v == null ? null : v.trim().toUpperCase(Locale.ROOT);
    }

    private static String nvl(String v) {
        return v == null ? "" : v;
    }

    private static String nvl(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private interface KeyFn {
        String apply(StatRow row);
    }

    private interface CatalogKeyFn {
        String apply(RcAssetCatalogEntry entry);
    }

    private interface PolicyKeyFn {
        String apply(RcStoragePolicy policy);
    }

    private static class StatRow {
        private Long id;
        private Long themeId;
        private String physicalTable;
        private String themeName;
        private String libName;
        private String libType;
        private String assetType;
        private String ownerOrg;
        private long rows;
        private long bytes;
        private long indexBytes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
