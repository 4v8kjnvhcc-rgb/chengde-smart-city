package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngBizSystem;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngDict;
import com.chengde.smartcity.exchange.entity.IngDictColumnLink;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.mapper.IngBizSystemMapper;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngDictColumnLinkMapper;
import com.chengde.smartcity.exchange.mapper.IngDictMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.chengde.smartcity.system.service.AccessControlService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 数据资产图谱（鱼骨）：项目 → 系统 → 数据库 → 表 → 数据项 →（可选）数据字典。
 */
@Service
public class AssetFishboneService {

    public static final String ROOT_ORG_NAME = "承德市高新区";

    private final SysOrgMapper orgMapper;
    private final IngProjectMapper projectMapper;
    private final IngBizSystemMapper systemMapper;
    private final IngDataSourceMapper sourceMapper;
    private final IngDataTableMapper tableMapper;
    private final IngDataColumnMapper columnMapper;
    private final IngDictMapper dictMapper;
    private final IngDictColumnLinkMapper dictColumnLinkMapper;
    private final AccessControlService accessControlService;

    public AssetFishboneService(SysOrgMapper orgMapper,
                                IngProjectMapper projectMapper,
                                IngBizSystemMapper systemMapper,
                                IngDataSourceMapper sourceMapper,
                                IngDataTableMapper tableMapper,
                                IngDataColumnMapper columnMapper,
                                IngDictMapper dictMapper,
                                IngDictColumnLinkMapper dictColumnLinkMapper,
                                AccessControlService accessControlService) {
        this.orgMapper = orgMapper;
        this.projectMapper = projectMapper;
        this.systemMapper = systemMapper;
        this.sourceMapper = sourceMapper;
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
        this.dictMapper = dictMapper;
        this.dictColumnLinkMapper = dictColumnLinkMapper;
        this.accessControlService = accessControlService;
    }

    public Map<String, Object> overview(UserPrincipal operator, Long orgId) {
        boolean platformView = operator.isSystemAdmin() || operator.isPlatformAdmin();
        Map<String, Object> out = new LinkedHashMap<>();
        SysOrg root = resolveRootOrg();
        out.put("mode", platformView ? "PLATFORM" : "DEPT");
        out.put("rootOrg", orgBrief(root));

        if (platformView) {
            List<SysOrg> children = listChildOrgs(root == null ? null : root.getId());
            out.put("orgs", children.stream().map(this::orgBrief).collect(Collectors.toList()));
            Long targetOrgId = orgId;
            if (targetOrgId == null) {
                out.put("selectedOrgId", null);
                out.put("tree", List.of());
                return out;
            }
            assertPlatformOrgAccessible(targetOrgId, root, children);
            out.put("selectedOrgId", targetOrgId);
            SysOrg selected = orgMapper.selectById(targetOrgId);
            out.put("selectedOrg", orgBrief(selected != null ? selected : root));
            out.put("tree", buildTreeForOrg(operator, targetOrgId));
            return out;
        }

        Long deptOrgId = operator.getOrgId();
        SysOrg dept = deptOrgId == null ? null : orgMapper.selectById(deptOrgId);
        out.put("orgs", List.of());
        out.put("selectedOrgId", deptOrgId);
        out.put("selectedOrg", orgBrief(dept != null ? dept : root));
        out.put("tree", buildTreeForOrg(operator, deptOrgId));
        return out;
    }

    private void assertPlatformOrgAccessible(Long orgId, SysOrg root, List<SysOrg> children) {
        if (root != null && Objects.equals(root.getId(), orgId)) {
            return;
        }
        boolean ok = children.stream().anyMatch(o -> Objects.equals(o.getId(), orgId));
        if (!ok) {
            // 允许任意存在机构（兼容更深层级）
            SysOrg org = orgMapper.selectById(orgId);
            if (org == null || (org.getStatus() != null && org.getStatus() == 0)) {
                throw new BusinessException(404, "组织机构不存在");
            }
        }
    }

    private List<Map<String, Object>> buildTreeForOrg(UserPrincipal operator, Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        Set<Long> allowed = accessControlService.effectiveProjectIds(operator);
        List<IngProject> projects = projectMapper.selectList(new LambdaQueryWrapper<IngProject>()
                .eq(IngProject::getBoundOrgId, orgId)
                .orderByAsc(IngProject::getId));
        projects = projects.stream()
                .filter(p -> p.getId() != null && allowed.contains(p.getId()))
                .collect(Collectors.toList());
        if (projects.isEmpty()) {
            return List.of();
        }

        Set<Long> projectIds = projects.stream().map(IngProject::getId).collect(Collectors.toSet());
        List<IngBizSystem> systems = systemMapper.selectList(new LambdaQueryWrapper<IngBizSystem>()
                .in(IngBizSystem::getProjectId, projectIds)
                .orderByAsc(IngBizSystem::getId));
        List<IngDataSource> sources = sourceMapper.selectList(new LambdaQueryWrapper<IngDataSource>()
                .in(IngDataSource::getProjectId, projectIds)
                .orderByAsc(IngDataSource::getId));

        Set<Long> sourceIds = sources.stream().map(IngDataSource::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<IngDataTable> tables = sourceIds.isEmpty() ? List.of()
                : tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>()
                .in(IngDataTable::getSourceId, sourceIds)
                .orderByAsc(IngDataTable::getId));

        Set<Long> tableIds = tables.stream().map(IngDataTable::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<IngDataColumn> columns = tableIds.isEmpty() ? List.of()
                : columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .in(IngDataColumn::getTableId, tableIds)
                .orderByAsc(IngDataColumn::getSortOrder)
                .orderByAsc(IngDataColumn::getId));

        Set<Long> columnIds = columns.stream().map(IngDataColumn::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<IngDictColumnLink> links = columnIds.isEmpty() ? List.of()
                : dictColumnLinkMapper.selectList(new LambdaQueryWrapper<IngDictColumnLink>()
                .in(IngDictColumnLink::getColumnId, columnIds));
        Set<Long> dictIds = links.stream().map(IngDictColumnLink::getDictId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, IngDict> dictById = dictIds.isEmpty() ? Map.of()
                : dictMapper.selectBatchIds(dictIds).stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(IngDict::getId, d -> d, (a, b) -> a));

        Map<Long, List<IngDict>> dictsByColumn = new HashMap<>();
        for (IngDictColumnLink link : links) {
            if (link.getColumnId() == null || link.getDictId() == null) continue;
            IngDict dict = dictById.get(link.getDictId());
            if (dict == null) continue;
            dictsByColumn.computeIfAbsent(link.getColumnId(), k -> new ArrayList<>()).add(dict);
        }

        Map<Long, List<IngBizSystem>> systemsByProject = systems.stream()
                .collect(Collectors.groupingBy(IngBizSystem::getProjectId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<IngDataSource>> sourcesBySystem = sources.stream()
                .filter(s -> s.getSystemId() != null)
                .collect(Collectors.groupingBy(IngDataSource::getSystemId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<IngDataSource>> orphanSourcesByProject = sources.stream()
                .filter(s -> s.getSystemId() == null)
                .collect(Collectors.groupingBy(IngDataSource::getProjectId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<IngDataTable>> tablesBySource = tables.stream()
                .collect(Collectors.groupingBy(IngDataTable::getSourceId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<IngDataColumn>> columnsByTable = columns.stream()
                .collect(Collectors.groupingBy(IngDataColumn::getTableId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> tree = new ArrayList<>();
        for (IngProject project : projects) {
            Map<String, Object> pNode = node("PROJECT", project.getId(),
                    nz(project.getProjectName(), "未命名项目"), project.getProjectCode());
            List<Map<String, Object>> systemChildren = new ArrayList<>();

            for (IngBizSystem system : systemsByProject.getOrDefault(project.getId(), List.of())) {
                systemChildren.add(buildSystemNode(system,
                        sourcesBySystem.getOrDefault(system.getId(), List.of()),
                        tablesBySource, columnsByTable, dictsByColumn));
            }

            List<IngDataSource> orphans = orphanSourcesByProject.getOrDefault(project.getId(), List.of());
            if (!orphans.isEmpty()) {
                Map<String, Object> placeholder = node("SYSTEM", -project.getId(), "未归系统", null);
                List<Map<String, Object>> dbNodes = new ArrayList<>();
                for (IngDataSource source : orphans) {
                    dbNodes.add(buildDatabaseNode(source, tablesBySource, columnsByTable, dictsByColumn));
                }
                placeholder.put("children", dbNodes);
                placeholder.put("childCount", dbNodes.size());
                systemChildren.add(placeholder);
            }

            pNode.put("children", systemChildren);
            pNode.put("childCount", systemChildren.size());
            tree.add(pNode);
        }
        return tree;
    }

    private Map<String, Object> buildSystemNode(IngBizSystem system,
                                                List<IngDataSource> sources,
                                                Map<Long, List<IngDataTable>> tablesBySource,
                                                Map<Long, List<IngDataColumn>> columnsByTable,
                                                Map<Long, List<IngDict>> dictsByColumn) {
        Map<String, Object> sNode = node("SYSTEM", system.getId(),
                nz(system.getSystemName(), "未命名系统"), system.getSystemCode());
        List<Map<String, Object>> dbNodes = new ArrayList<>();
        for (IngDataSource source : sources) {
            dbNodes.add(buildDatabaseNode(source, tablesBySource, columnsByTable, dictsByColumn));
        }
        sNode.put("children", dbNodes);
        sNode.put("childCount", dbNodes.size());
        return sNode;
    }

    private Map<String, Object> buildDatabaseNode(IngDataSource source,
                                                  Map<Long, List<IngDataTable>> tablesBySource,
                                                  Map<Long, List<IngDataColumn>> columnsByTable,
                                                  Map<Long, List<IngDict>> dictsByColumn) {
        Map<String, Object> dNode = node("DATABASE", source.getId(),
                nz(source.getSourceName(), "未命名数据库"), source.getSourceCode());
        List<Map<String, Object>> tableNodes = new ArrayList<>();
        for (IngDataTable table : tablesBySource.getOrDefault(source.getId(), List.of())) {
            tableNodes.add(buildTableNode(table, columnsByTable, dictsByColumn));
        }
        dNode.put("children", tableNodes);
        dNode.put("childCount", tableNodes.size());
        return dNode;
    }

    private Map<String, Object> buildTableNode(IngDataTable table,
                                               Map<Long, List<IngDataColumn>> columnsByTable,
                                               Map<Long, List<IngDict>> dictsByColumn) {
        // 中文侧优先表名称；英文侧优先源表/物理表名，其次表编码
        String tableZh = firstNonBlank(table.getTableName(), "");
        String tableEn = firstNonBlank(table.getSourceTable(), table.getPhysicalTableName(), table.getTableCode());
        Map<String, Object> tNode = node("TABLE", table.getId(),
                bilingualLabel(tableZh, tableEn), table.getTableCode());
        List<Map<String, Object>> colNodes = new ArrayList<>();
        for (IngDataColumn column : columnsByTable.getOrDefault(table.getId(), List.of())) {
            String colEn = nz(column.getColumnCode(), "");
            String colZh = nz(column.getColumnName(), "");
            Map<String, Object> cNode = node("COLUMN", column.getId(),
                    bilingualLabel(colZh, colEn), column.getColumnCode());
            List<IngDict> dicts = dictsByColumn.getOrDefault(column.getId(), List.of());
            List<Map<String, Object>> dictNodes = new ArrayList<>();
            for (IngDict dict : dicts) {
                dictNodes.add(node("DICT", dict.getId(),
                        nz(dict.getDictName(), dict.getDictCode()), dict.getDictCode()));
            }
            for (Map<String, Object> dn : dictNodes) {
                dn.put("children", List.of());
                dn.put("childCount", 0);
            }
            cNode.put("children", dictNodes);
            cNode.put("childCount", dictNodes.size());
            colNodes.add(cNode);
        }
        tNode.put("children", colNodes);
        tNode.put("childCount", colNodes.size());
        return tNode;
    }

    private Map<String, Object> node(String type, Long refId, String label, String code) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", type.toLowerCase() + ":" + refId);
        n.put("type", type);
        n.put("refId", refId);
        n.put("label", label);
        n.put("code", code);
        return n;
    }

    private SysOrg resolveRootOrg() {
        List<SysOrg> byName = orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                .eq(SysOrg::getOrgName, ROOT_ORG_NAME)
                .eq(SysOrg::getStatus, 1)
                .orderByAsc(SysOrg::getId)
                .last("limit 1"));
        if (!byName.isEmpty()) {
            return byName.get(0);
        }
        List<SysOrg> roots = orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                .and(w -> w.isNull(SysOrg::getParentId).or().eq(SysOrg::getParentId, 0L))
                .eq(SysOrg::getStatus, 1)
                .orderByAsc(SysOrg::getSortOrder)
                .orderByAsc(SysOrg::getId));
        return roots.isEmpty() ? null : roots.get(0);
    }

    private List<SysOrg> listChildOrgs(Long rootId) {
        if (rootId == null) {
            return List.of();
        }
        return orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                        .eq(SysOrg::getParentId, rootId)
                        .eq(SysOrg::getStatus, 1)
                        .orderByAsc(SysOrg::getSortOrder)
                        .orderByAsc(SysOrg::getId))
                .stream()
                .sorted(Comparator.comparing(SysOrg::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysOrg::getId))
                .collect(Collectors.toList());
    }

    private Map<String, Object> orgBrief(SysOrg org) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (org == null) {
            m.put("id", null);
            m.put("orgName", ROOT_ORG_NAME);
            m.put("orgCode", null);
            m.put("parentId", null);
            return m;
        }
        m.put("id", org.getId());
        m.put("orgName", org.getOrgName());
        m.put("orgCode", org.getOrgCode());
        m.put("parentId", org.getParentId());
        return m;
    }

    private static String nz(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return "-";
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    /** 中文名 / 英文名；缺一则只显示有值的一侧 */
    private static String bilingualLabel(String zh, String en) {
        String z = zh == null ? "" : zh.trim();
        String e = en == null ? "" : en.trim();
        if (z.isEmpty() && e.isEmpty()) return "-";
        if (z.isEmpty()) return e;
        if (e.isEmpty()) return z;
        if (z.equalsIgnoreCase(e)) return z;
        return z + " / " + e;
    }
}
