package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizDataAsset;
import com.chengde.smartcity.exchange.entity.IngBizSystem;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngDict;
import com.chengde.smartcity.exchange.entity.IngGovernPolicy;
import com.chengde.smartcity.exchange.entity.IngGuideStep;
import com.chengde.smartcity.exchange.entity.IngHealthMetric;
import com.chengde.smartcity.exchange.entity.IngIngestChannel;
import com.chengde.smartcity.exchange.entity.IngPipelineJob;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.entity.IngResourceRegistry;
import com.chengde.smartcity.exchange.entity.IngStatsMetric;
import com.chengde.smartcity.exchange.entity.IngUploadRecord;
import com.chengde.smartcity.exchange.mapper.BizDataAssetMapper;
import com.chengde.smartcity.exchange.mapper.IngBizSystemMapper;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngDictMapper;
import com.chengde.smartcity.exchange.mapper.IngGovernPolicyMapper;
import com.chengde.smartcity.exchange.mapper.IngGuideStepMapper;
import com.chengde.smartcity.exchange.mapper.IngHealthMetricMapper;
import com.chengde.smartcity.exchange.mapper.IngIngestChannelMapper;
import com.chengde.smartcity.exchange.mapper.IngPipelineJobMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.exchange.mapper.IngResourceRegistryMapper;
import com.chengde.smartcity.exchange.mapper.IngStatsMetricMapper;
import com.chengde.smartcity.exchange.mapper.IngUploadRecordMapper;
import com.chengde.smartcity.integration.jdbc.CredentialCipher;
import com.chengde.smartcity.integration.jdbc.JdbcProbeService;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.chengde.smartcity.system.service.AccessControlService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionPlatformService {

    private final IngStatsMetricMapper statsMapper;
    private final IngGuideStepMapper guideMapper;
    private final IngProjectMapper projectMapper;
    private final IngBizSystemMapper bizSystemMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final IngDataTableMapper dataTableMapper;
    private final IngDataColumnMapper dataColumnMapper;
    private final IngDictMapper dictMapper;
    private final IngUploadRecordMapper uploadMapper;
    private final IngIngestChannelMapper channelMapper;
    private final IngPipelineJobMapper pipelineMapper;
    private final IngResourceRegistryMapper registryMapper;
    private final IngGovernPolicyMapper policyMapper;
    private final IngHealthMetricMapper healthMapper;
    private final BizDataAssetMapper assetMapper;
    private final AuditService auditService;
    private final StorageIntegrationClient storageClient;
    private final CredentialCipher credentialCipher;
    private final JdbcProbeService jdbcProbeService;
    private final SysOrgMapper orgMapper;
    private final AccessControlService accessControlService;
    private final KettleCollectService kettleCollectService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestionPlatformService(IngStatsMetricMapper statsMapper, IngGuideStepMapper guideMapper,
                                    IngProjectMapper projectMapper, IngBizSystemMapper bizSystemMapper,
                                    IngDataSourceMapper dataSourceMapper,
                                    IngDataTableMapper dataTableMapper, IngDataColumnMapper dataColumnMapper,
                                    IngDictMapper dictMapper, IngUploadRecordMapper uploadMapper,
                                    IngIngestChannelMapper channelMapper, IngPipelineJobMapper pipelineMapper,
                                    IngResourceRegistryMapper registryMapper, IngGovernPolicyMapper policyMapper,
                                    IngHealthMetricMapper healthMapper, BizDataAssetMapper assetMapper,
                                    AuditService auditService, StorageIntegrationClient storageClient,
                                    CredentialCipher credentialCipher, JdbcProbeService jdbcProbeService,
                                    SysOrgMapper orgMapper, AccessControlService accessControlService,
                                    KettleCollectService kettleCollectService) {
        this.statsMapper = statsMapper;
        this.guideMapper = guideMapper;
        this.projectMapper = projectMapper;
        this.bizSystemMapper = bizSystemMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.dataTableMapper = dataTableMapper;
        this.dataColumnMapper = dataColumnMapper;
        this.dictMapper = dictMapper;
        this.uploadMapper = uploadMapper;
        this.channelMapper = channelMapper;
        this.pipelineMapper = pipelineMapper;
        this.registryMapper = registryMapper;
        this.policyMapper = policyMapper;
        this.healthMapper = healthMapper;
        this.assetMapper = assetMapper;
        this.auditService = auditService;
        this.storageClient = storageClient;
        this.credentialCipher = credentialCipher;
        this.jdbcProbeService = jdbcProbeService;
        this.orgMapper = orgMapper;
        this.accessControlService = accessControlService;
        this.kettleCollectService = kettleCollectService;
    }

    public List<IngStatsMetric> baseStats() {
        return statsMapper.selectList(new LambdaQueryWrapper<IngStatsMetric>()
                .eq(IngStatsMetric::getDomainType, "BASE").orderByAsc(IngStatsMetric::getSortOrder));
    }

    public List<IngStatsMetric> domainStats() {
        return statsMapper.selectList(new LambdaQueryWrapper<IngStatsMetric>()
                .eq(IngStatsMetric::getDomainType, "DOMAIN").orderByAsc(IngStatsMetric::getSortOrder));
    }

    public List<IngGuideStep> guideSteps() {
        return guideMapper.selectList(new LambdaQueryWrapper<IngGuideStep>().orderByAsc(IngGuideStep::getStepNo));
    }

    /**
     * 登记概览：按当前用户可见范围统计（与项目/数据源列表权限一致），避免部门管理员看到全平台数字。
     * 字典为平台共享资源，仍为全库数量。
     */
    public Map<String, Object> registerOverview(UserPrincipal operator) {
        Set<Long> projectIds = accessControlService.effectiveProjectIds(operator);
        Set<Long> sourceIds = accessControlService.effectiveSourceIds(operator);
        Set<Long> tableIds = accessControlService.effectiveTableIds(operator);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projects", projectIds.size());
        out.put("dataSources", sourceIds.size());
        out.put("dicts", dictMapper.selectCount(null));
        // 「资产」对齐可见登记表数量（本页语境下的可操作资产）
        out.put("assets", tableIds.size());
        out.put("lineageGraph", Map.of("nodes", List.of(), "edges", List.of()));
        out.put("systemLinks", List.of(
                Map.of("mCode", "M048", "route", "?system=collect&module=m048", "label", "访问控制"),
                Map.of("mCode", "M049", "route", "/system/security", "label", "等保开关")
        ));
        return out;
    }

    public List<IngProject> listProjects(UserPrincipal operator) {
        accessControlService.ensureOrgOtherProject(operator);
        Set<Long> allowed = accessControlService.effectiveProjectIds(operator);
        if (allowed.isEmpty()) {
            return List.of();
        }
        List<IngProject> list = projectMapper.selectList(
                new LambdaQueryWrapper<IngProject>().in(IngProject::getId, allowed).orderByDesc(IngProject::getId));
        fillBoundOrgNames(list);
        return list;
    }

    private void fillBoundOrgNames(List<IngProject> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> orgIds = list.stream()
                .map(IngProject::getBoundOrgId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (orgIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = new HashMap<>();
        for (SysOrg org : orgMapper.selectBatchIds(orgIds)) {
            if (org != null && org.getId() != null) {
                names.put(org.getId(), org.getOrgName());
            }
        }
        for (IngProject p : list) {
            if (p.getBoundOrgId() != null) {
                p.setBoundOrgName(names.getOrDefault(p.getBoundOrgId(), "—"));
            } else {
                p.setBoundOrgName("—");
            }
        }
    }

    @Transactional
    public Long createProject(UserPrincipal operator, Map<String, Object> body) {
        IngProject p = new IngProject();
        p.setProjectCode(str(body.get("projectCode"), "PRJ_" + UUID.randomUUID().toString().substring(0, 8)));
        p.setProjectName(required(body.get("projectName"), "projectName").toString());
        Long boundOrgId = resolveBoundOrgIdForWrite(operator, body, true);
        // 默认系统名可选；系统在项目下单独创建，不再强制首系统
        String systemName = str(body.get("systemName"), "").trim();
        p.setBoundOrgId(boundOrgId);
        p.setSystemName(systemName.isEmpty() ? null : systemName);
        p.setStatus("ACTIVE");
        p.setRegisterStatus(com.chengde.smartcity.exchange.support.RegisterStatuses.DRAFT);
        p.setCreatedBy(operator.getUsername());
        projectMapper.insert(p);
        accessControlService.ensureCreatorProjectGrant(operator, p.getId());
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_PROJECT_CREATE", "ing_project", String.valueOf(p.getId()), p.getProjectName());
        return p.getId();
    }

    @Transactional
    public void updateProject(UserPrincipal operator, Long id, Map<String, Object> body) {
        accessControlService.assertProjectAccess(operator, id);
        IngProject p = projectMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (!com.chengde.smartcity.exchange.support.RegisterStatuses.canEdit(p.getRegisterStatus())) {
            throw new BusinessException(400, "当前登记状态不可编辑");
        }
        if (isBuiltinOtherProject(p.getProjectCode())) {
            // 系统初始化「其他」项目：名称固定，仅允许维护系统/数据源；部门归属不可改
            if (!"其他".equals(p.getProjectName())) {
                p.setProjectName("其他");
            }
            if (body.containsKey("boundOrgId") && body.get("boundOrgId") != null
                    && !String.valueOf(body.get("boundOrgId")).isBlank()) {
                throw new BusinessException(400, "平台默认「其他」项目的部门归属不可修改");
            }
        } else {
            String projectName = str(body.get("projectName"), "").trim();
            if (projectName.isEmpty()) {
                throw new BusinessException(400, "项目名称必填");
            }
            p.setProjectName(projectName);
            Long newOrgId = resolveBoundOrgIdForWrite(operator, body, false);
            if (newOrgId != null) {
                p.setBoundOrgId(newOrgId);
            }
        }
        if (body.containsKey("systemName")) {
            String systemName = body.get("systemName") == null ? "" : String.valueOf(body.get("systemName")).trim();
            p.setSystemName(systemName.isEmpty() ? null : systemName);
        }
        projectMapper.updateById(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_PROJECT_UPDATE", "ing_project", String.valueOf(id), p.getProjectName());
    }

    @Transactional
    public void deleteProject(UserPrincipal operator, Long id) {
        if (!operator.isSystemAdmin() && !operator.getPermissions().contains("exchange:project:delete")) {
            throw new BusinessException(403, "仅系统管理员可删除登记项目");
        }
        IngProject p = projectMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (isBuiltinOtherProject(p.getProjectCode())) {
            throw new BusinessException(400, "平台默认项目「其他」不可删除");
        }
        Long sysCnt = bizSystemMapper.selectCount(new LambdaQueryWrapper<IngBizSystem>()
                .eq(IngBizSystem::getProjectId, id));
        if (sysCnt != null && sysCnt > 0) {
            throw new BusinessException(400, "该项目下已关联系统，请先删除系统后再删除项目");
        }
        projectMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_PROJECT_DELETE", "ing_project", String.valueOf(id), p.getProjectName());
    }

    public List<IngBizSystem> listBizSystems(UserPrincipal operator, Long projectId) {
        if (projectId == null) {
            throw new BusinessException(400, "projectId 必填");
        }
        accessControlService.assertProjectAccess(operator, projectId);
        List<IngBizSystem> list = bizSystemMapper.selectList(new LambdaQueryWrapper<IngBizSystem>()
                .eq(IngBizSystem::getProjectId, projectId)
                .orderByDesc(IngBizSystem::getId));
        for (IngBizSystem s : list) {
            Long cnt = dataSourceMapper.selectCount(new LambdaQueryWrapper<IngDataSource>()
                    .eq(IngDataSource::getSystemId, s.getId()));
            s.setDataSourceCount(cnt == null ? 0 : cnt.intValue());
        }
        return list;
    }

    @Transactional
    public Long createBizSystem(UserPrincipal operator, Map<String, Object> body) {
        Long projectId = Long.valueOf(String.valueOf(required(body.get("projectId"), "projectId")));
        accessControlService.assertProjectAccess(operator, projectId);
        IngProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        String systemName = String.valueOf(required(body.get("systemName"), "系统名称")).trim();
        if (systemName.isBlank()) {
            throw new BusinessException(400, "系统名称不能为空");
        }
        assertBizSystemNameUnique(projectId, systemName, null);
        IngBizSystem s = new IngBizSystem();
        s.setProjectId(projectId);
        s.setSystemCode(str(body.get("systemCode"), "SYS_" + projectId + "_" + System.currentTimeMillis()));
        s.setSystemName(systemName);
        s.setStatus("ACTIVE");
        s.setRegisterStatus(com.chengde.smartcity.exchange.support.RegisterStatuses.DRAFT);
        s.setCreatedBy(operator.getUsername());
        bizSystemMapper.insert(s);
        if (project.getSystemName() == null || project.getSystemName().isBlank()) {
            project.setSystemName(systemName);
            projectMapper.updateById(project);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_SYSTEM_CREATE", "ing_biz_system", String.valueOf(s.getId()), systemName);
        return s.getId();
    }

    @Transactional
    public void updateBizSystem(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngBizSystem s = bizSystemMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(404, "业务系统不存在");
        }
        accessControlService.assertProjectAccess(operator, s.getProjectId());
        if (isBuiltinOtherSystem(s.getSystemCode())) {
            throw new BusinessException(400, "平台默认「其他」系统名称不可修改");
        }
        String systemName = str(body.get("systemName"), "").trim();
        if (systemName.isEmpty()) {
            throw new BusinessException(400, "系统名称不能为空");
        }
        assertBizSystemNameUnique(s.getProjectId(), systemName, s.getId());
        String oldName = s.getSystemName();
        s.setSystemName(systemName);
        bizSystemMapper.updateById(s);
        List<IngDataSource> sources = dataSourceMapper.selectList(new LambdaQueryWrapper<IngDataSource>()
                .eq(IngDataSource::getSystemId, s.getId()));
        for (IngDataSource ds : sources) {
            ds.setSystemName(systemName);
            dataSourceMapper.updateById(ds);
        }
        IngProject project = projectMapper.selectById(s.getProjectId());
        if (project != null && oldName != null && oldName.equals(project.getSystemName())) {
            project.setSystemName(systemName);
            projectMapper.updateById(project);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_SYSTEM_UPDATE", "ing_biz_system", String.valueOf(id), systemName);
    }

    @Transactional
    public void deleteBizSystem(UserPrincipal operator, Long id) {
        IngBizSystem s = bizSystemMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(404, "业务系统不存在");
        }
        accessControlService.assertProjectAccess(operator, s.getProjectId());
        if (isBuiltinOtherSystem(s.getSystemCode())) {
            throw new BusinessException(400, "平台默认「其他」系统不可删除");
        }
        Long cnt = dataSourceMapper.selectCount(new LambdaQueryWrapper<IngDataSource>()
                .eq(IngDataSource::getSystemId, s.getId()));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(400, "请先删除该系统下的数据源后再删除系统");
        }
        String name = s.getSystemName();
        bizSystemMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_SYSTEM_DELETE", "ing_biz_system", String.valueOf(id), name);
    }

    private void assertBizSystemNameUnique(Long projectId, String systemName, Long excludeId) {
        LambdaQueryWrapper<IngBizSystem> q = new LambdaQueryWrapper<IngBizSystem>()
                .eq(IngBizSystem::getProjectId, projectId)
                .eq(IngBizSystem::getSystemName, systemName);
        if (excludeId != null) {
            q.ne(IngBizSystem::getId, excludeId);
        }
        Long dup = bizSystemMapper.selectCount(q);
        if (dup != null && dup > 0) {
            throw new BusinessException(409, "本项目下已存在系统「" + systemName + "」");
        }
    }

    @Transactional
    public void deleteDataSource(UserPrincipal operator, Long id) {
        IngDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        if (isBuiltinManualUploadSource(ds.getSourceCode())) {
            throw new BusinessException(400, "平台默认「手动上传」数据源不可删除，可修改其系统名称，或新增系统/数据源");
        }
        Long tableCnt = dataTableMapper.selectCount(new LambdaQueryWrapper<IngDataTable>()
                .eq(IngDataTable::getSourceId, id));
        if (tableCnt != null && tableCnt > 0) {
            throw new BusinessException(400, "该数据库下已关联数据表，不可删除");
        }
        String name = ds.getSourceName();
        dataSourceMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_DS_DELETE", "ing_data_source", String.valueOf(id), name);
    }

    /** 删除数据源及其已登记表/字段 */
    private void deleteDataSourceCascade(Long sourceId) {
        List<IngDataTable> tables = dataTableMapper.selectList(
                new LambdaQueryWrapper<IngDataTable>().eq(IngDataTable::getSourceId, sourceId));
        for (IngDataTable t : tables) {
            dataColumnMapper.delete(new LambdaQueryWrapper<IngDataColumn>().eq(IngDataColumn::getTableId, t.getId()));
        }
        dataTableMapper.delete(new LambdaQueryWrapper<IngDataTable>().eq(IngDataTable::getSourceId, sourceId));
        dataSourceMapper.deleteById(sourceId);
    }

    public List<IngDataSource> listDataSources(UserPrincipal operator, Long projectId) {
        return listDataSources(operator, projectId, null);
    }

    public List<IngDataSource> listDataSources(UserPrincipal operator, Long projectId, Long systemId) {
        Set<Long> allowedSources = accessControlService.effectiveSourceIds(operator);
        if (allowedSources.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<IngDataSource> q = new LambdaQueryWrapper<IngDataSource>()
                .in(IngDataSource::getId, allowedSources)
                .orderByDesc(IngDataSource::getId);
        if (systemId != null) {
            IngBizSystem system = bizSystemMapper.selectById(systemId);
            if (system == null) {
                throw new BusinessException(404, "业务系统不存在");
            }
            accessControlService.assertProjectAccess(operator, system.getProjectId());
            q.eq(IngDataSource::getSystemId, systemId);
        } else if (projectId != null) {
            accessControlService.assertProjectAccess(operator, projectId);
            q.eq(IngDataSource::getProjectId, projectId);
        }
        List<IngDataSource> list = dataSourceMapper.selectList(q);
        list.forEach(ds -> ds.setConnConfigJson(maskConnConfig(ds.getConnConfigJson())));
        return list;
    }

    /** 真实 JDBC 连接测试：建立连接、SELECT 1、统计表数量；失败即 FAILED 且抛出真实原因。 */
    @Transactional
    public Map<String, Object> testDataSource(UserPrincipal operator, Long id) {
        IngDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        if ("FILE".equals(ds.getSourceType()) || "API".equals(ds.getSourceType())) {
            throw new BusinessException(400, "FILE/API 数据源不支持 JDBC 连接测试");
        }
        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        try {
            Map<String, Object> probe = jdbcProbeService.testConnection(conn);
            int tableCount = (int) probe.get("tableCount");
            ds.setConnStatus("OK");
            ds.setTableCount(tableCount);
            ds.setSourceSchema((String) probe.get("schema"));
            ds.setProbeAt(LocalDateTime.now());
            ds.setProbeMessage("连接成功 " + probe.get("product") + " 表数=" + tableCount);
            dataSourceMapper.updateById(ds);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "ING_DS_TEST", "ing_data_source", String.valueOf(id), "connection ok tables=" + tableCount);
            return Map.of("sourceId", id, "connStatus", "OK", "tableCount", tableCount,
                    "schema", probe.getOrDefault("schema", ""), "product", probe.getOrDefault("product", ""),
                    "elapsedMs", probe.getOrDefault("elapsedMs", 0), "message", "连接探测成功");
        } catch (BusinessException be) {
            ds.setConnStatus("FAILED");
            ds.setProbeAt(LocalDateTime.now());
            ds.setProbeMessage(safeMessage(be.getMessage()));
            dataSourceMapper.updateById(ds);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "ING_DS_TEST", "ing_data_source", String.valueOf(id), "connection failed");
            throw be;
        }
    }

    /** 真实探库：返回源库全部表的列/主键/行数，供前端勾选登记（不落库）。 */
    public Map<String, Object> probeDataSource(UserPrincipal operator, Long id) {
        IngDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        List<Map<String, Object>> tables = jdbcProbeService.listTables(conn);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", id);
        out.put("schema", conn.database);
        out.put("tableCount", tables.size());
        out.put("tables", tables);
        return out;
    }

    /**
     * 登记源表：对勾选的源表真实探测列/主键/行数，落到 ing_data_table + ing_data_column。
     * collectStatus 置 PENDING（真实汇聚由 Kettle 完成后置 SUCCESS），不再伪造已汇聚。
     */
    @Transactional
    public Map<String, Object> registerTables(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        Object rawTables = body.get("tables");
        if (!(rawTables instanceof List<?> reqTables) || reqTables.isEmpty()) {
            throw new BusinessException(400, "请至少选择一张源表进行登记");
        }
        JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        List<Map<String, Object>> registered = new ArrayList<>();
        for (Object item : reqTables) {
            if (!(item instanceof Map<?, ?> tm)) {
                continue;
            }
            String sourceTable = str(tm.get("sourceTable"), null);
            if (sourceTable == null) {
                throw new BusinessException(400, "登记表缺少 sourceTable");
            }
            Map<String, Object> desc = jdbcProbeService.describeTable(conn, sourceTable);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cols = (List<Map<String, Object>>) desc.get("columns");
            @SuppressWarnings("unchecked")
            List<String> pks = (List<String>) desc.get("primaryKeys");
            long rowCount = ((Number) desc.getOrDefault("rowCount", -1L)).longValue();

            String tableCode = str(tm.get("tableCode"), "TBL_" + sourceTable.toUpperCase());
            // uk_table_code 全局唯一：先按 source+code，再按全局 code，避免误插入撞唯一键后变成迷惑性 401
            IngDataTable table = dataTableMapper.selectOne(new LambdaQueryWrapper<IngDataTable>()
                    .eq(IngDataTable::getSourceId, id).eq(IngDataTable::getTableCode, tableCode).last("LIMIT 1"));
            if (table == null) {
                IngDataTable byCode = dataTableMapper.selectOne(new LambdaQueryWrapper<IngDataTable>()
                        .eq(IngDataTable::getTableCode, tableCode).last("LIMIT 1"));
                if (byCode != null && !id.equals(byCode.getSourceId())) {
                    throw new BusinessException(409, "tableCode 已被其他数据源占用: " + tableCode
                            + "（sourceId=" + byCode.getSourceId() + "），请更换编码");
                }
                table = byCode;
            }
            boolean isNew = table == null;
            if (isNew) {
                table = new IngDataTable();
                table.setSourceId(id);
                table.setTableCode(tableCode);
            } else {
                table.setSourceId(id);
                table.setTableCode(tableCode);
            }
            table.setTableName(str(tm.get("tableName"), sourceTable));
            table.setSourceSchema(conn.database);
            table.setSourceTable(sourceTable);
            table.setSourceRowCount(rowCount);
            table.setPrimaryKeyCols(String.join(",", pks));
            table.setModelingMode("REVERSE");
            table.setColumnCount(cols.size());
            table.setStatus("ACTIVE");
            table.setCollectStatus("PENDING");
            if (isNew) {
                dataTableMapper.insert(table);
            } else {
                dataColumnMapper.delete(new LambdaQueryWrapper<IngDataColumn>().eq(IngDataColumn::getTableId, table.getId()));
                dataTableMapper.updateById(table);
            }
            for (Map<String, Object> col : cols) {
                IngDataColumn c = new IngDataColumn();
                c.setTableId(table.getId());
                // 字段编码 = 源库列名；字段名称 = 列注释（无注释则为空，可在数据项管理中补全）
                String physicalName = String.valueOf(col.get("columnName"));
                String remarks = str(col.get("remarks"), null);
                c.setColumnCode(physicalName);
                c.setColumnName(remarks != null && !remarks.isBlank() ? remarks.trim() : "");
                c.setDataType(String.valueOf(col.get("dataType")));
                c.setNullableFlag(Boolean.TRUE.equals(col.get("nullable")) ? 1 : 0);
                c.setSortOrder(intVal(col.get("sortOrder"), 0));
                Object size = col.get("columnSize");
                c.setLengthVal(size == null ? null : Integer.parseInt(String.valueOf(size)));
                c.setSemanticDesc(remarks);
                c.setBuiltInFlag(0);
                dataColumnMapper.insert(c);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("tableId", table.getId());
            r.put("tableCode", tableCode);
            r.put("sourceTable", sourceTable);
            r.put("columnCount", cols.size());
            r.put("sourceRowCount", rowCount);
            r.put("primaryKeys", pks);
            r.put("collectStatus", "PENDING");
            registered.add(r);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_TABLE_REGISTER", "ing_data_table", String.valueOf(id), "registered=" + registered.size());
        return Map.of("sourceId", id, "registered", registered);
    }

    @Transactional
    public Long createDataSource(UserPrincipal operator, Map<String, Object> body) {
        Long systemId = Long.valueOf(String.valueOf(required(body.get("systemId"), "systemId")));
        IngBizSystem system = bizSystemMapper.selectById(systemId);
        if (system == null) {
            throw new BusinessException(404, "业务系统不存在");
        }
        accessControlService.assertProjectAccess(operator, system.getProjectId());
        if (body.get("projectId") != null && !String.valueOf(body.get("projectId")).isBlank()) {
            Long projectId = Long.valueOf(String.valueOf(body.get("projectId")));
            if (!system.getProjectId().equals(projectId)) {
                throw new BusinessException(400, "数据源所属项目与业务系统不一致");
            }
        }
        IngDataSource ds = new IngDataSource();
        ds.setProjectId(system.getProjectId());
        ds.setSystemId(system.getId());
        ds.setSourceCode(str(body.get("sourceCode"), "DS_" + System.currentTimeMillis()));
        String sourceName = String.valueOf(required(body.get("sourceName"), "数据源名称")).trim();
        if (sourceName.isBlank()) {
            throw new BusinessException(400, "数据源名称不能为空");
        }
        ds.setSourceName(sourceName);
        ds.setSystemName(system.getSystemName());
        ds.setSourceType(str(body.get("sourceType"), "MYSQL"));
        ds.setConnConfigJson(buildConnConfigJson(body, null));
        ds.setConnStatus("FILE".equalsIgnoreCase(ds.getSourceType()) || "API".equalsIgnoreCase(ds.getSourceType())
                ? "OK" : "UNTESTED");
        ds.setRegisterStatus(com.chengde.smartcity.exchange.support.RegisterStatuses.DRAFT);
        ds.setTableCount(0);
        ds.setSyncStatus("PENDING");
        if ("FILE".equalsIgnoreCase(ds.getSourceType()) && (ds.getConnConfigJson() == null || ds.getConnConfigJson().isBlank())) {
            ds.setConnConfigJson("{\"channel\":\"MANUAL_UPLOAD\",\"odsDb\":\"smart_city_ods\"}");
            ds.setSourceSchema("smart_city_ods");
        }
        dataSourceMapper.insert(ds);
        return ds.getId();
    }

    @Transactional
    public void updateDataSource(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) throw new BusinessException(404, "数据源不存在");
        accessControlService.assertProjectAccess(operator, ds.getProjectId());
        // 空名称不覆盖，避免「配置连接」误传空 sourceName 把名称清掉
        if (body.containsKey("sourceName")) {
            String name = body.get("sourceName") == null ? "" : String.valueOf(body.get("sourceName")).trim();
            if (!name.isBlank()) {
                ds.setSourceName(name);
            }
        }
        if (body.containsKey("systemId") && body.get("systemId") != null
                && !String.valueOf(body.get("systemId")).isBlank()) {
            Long systemId = Long.valueOf(String.valueOf(body.get("systemId")));
            IngBizSystem system = bizSystemMapper.selectById(systemId);
            if (system == null) {
                throw new BusinessException(404, "业务系统不存在");
            }
            if (!system.getProjectId().equals(ds.getProjectId())) {
                throw new BusinessException(400, "不能将数据源移动到其他项目的系统下");
            }
            ds.setSystemId(system.getId());
            ds.setSystemName(system.getSystemName());
        }
        if (body.containsKey("sourceType")) {
            Object st = body.get("sourceType");
            if (st != null && !String.valueOf(st).isBlank()) {
                ds.setSourceType(String.valueOf(st));
            }
        }
        ds.setConnConfigJson(buildConnConfigJson(body, ds));
        if (!"FILE".equalsIgnoreCase(ds.getSourceType()) && !"API".equalsIgnoreCase(ds.getSourceType())) {
            ds.setConnStatus("UNTESTED");
        }
        dataSourceMapper.updateById(ds);
    }

    /**
     * 构造连接配置 JSON；密码经 AES 加密后存入 passwordCipher，绝不明文落库。
     * 更新场景下密码留空则沿用原密文（existing）。
     */
    private String buildConnConfigJson(Map<String, Object> body, IngDataSource existing) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (body.get("host") != null) cfg.put("host", body.get("host"));
        if (body.get("port") != null) cfg.put("port", body.get("port"));
        if (body.get("database") != null) cfg.put("database", body.get("database"));
        if (body.get("username") != null) cfg.put("username", body.get("username"));
        String password = body.get("password") == null ? null : String.valueOf(body.get("password"));
        if (password != null && !password.isBlank()) {
            cfg.put("passwordCipher", credentialCipher.encrypt(password));
        } else if (existing != null && existing.getConnConfigJson() != null) {
            String prevCipher = extractCipher(existing.getConnConfigJson());
            if (prevCipher != null) {
                cfg.put("passwordCipher", prevCipher);
            }
        }
        if (cfg.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(cfg);
        } catch (Exception e) {
            throw new BusinessException(400, "连接配置序列化失败");
        }
    }

    private String extractCipher(String connConfigJson) {
        try {
            Map<?, ?> cfg = objectMapper.readValue(connConfigJson, Map.class);
            Object cipher = cfg.get("passwordCipher");
            if (cipher != null) return String.valueOf(cipher);
            Object legacy = cfg.get("password");
            return legacy == null ? null : credentialCipher.encrypt(String.valueOf(legacy));
        } catch (Exception e) {
            return null;
        }
    }

    /** 列表/详情返回时去除密码字段，仅标记 passwordSet，避免明文/密文外泄。 */
    private String maskConnConfig(String connConfigJson) {
        if (connConfigJson == null || connConfigJson.isBlank()) {
            return connConfigJson;
        }
        try {
            Map<String, Object> cfg = objectMapper.readValue(connConfigJson, Map.class);
            boolean hasPassword = cfg.remove("passwordCipher") != null | (cfg.remove("password") != null);
            cfg.put("passwordSet", hasPassword);
            return objectMapper.writeValueAsString(cfg);
        } catch (Exception e) {
            return "{\"passwordSet\":false}";
        }
    }

    private String safeMessage(String raw) {
        if (raw == null) return "连接失败";
        return raw.length() > 480 ? raw.substring(0, 480) : raw;
    }

    public List<IngDict> listDicts() {
        return dictMapper.selectList(new LambdaQueryWrapper<IngDict>().orderByAsc(IngDict::getId));
    }

    @Transactional
    public Long createDict(UserPrincipal operator, Map<String, Object> body) {
        IngDict d = new IngDict();
        // 编码仅作库内唯一键，界面不采集，自动生成
        d.setDictCode(str(body.get("dictCode"), "DICT_" + System.currentTimeMillis()));
        d.setDictName(required(body.get("dictName"), "字典名称").toString().trim());
        d.setDictType("CUSTOM");
        d.setStandardNo(str(body.get("standardNo"), null));
        d.setPublisher(null);
        d.setVersionNo(null);
        d.setRemark(str(body.get("remark"), null));
        d.setItemCount(0);
        d.setStatus("ACTIVE");
        d.setRegisterStatus(com.chengde.smartcity.exchange.support.RegisterStatuses.DRAFT);
        dictMapper.insert(d);
        return d.getId();
    }

    public List<IngUploadRecord> listUploads() {
        return uploadMapper.selectList(new LambdaQueryWrapper<IngUploadRecord>().orderByDesc(IngUploadRecord::getId));
    }

    @Transactional
    public Long uploadData(UserPrincipal operator, Map<String, Object> body) {
        IngUploadRecord r = new IngUploadRecord();
        r.setTemplateCode(str(body.get("templateCode"), "TPL_DEFAULT"));
        r.setFileName(required(body.get("fileName"), "fileName").toString());
        int rows = intVal(body.get("rowCount"), 50 + (int) (Math.random() * 200));
        r.setRowCount(rows);
        r.setStatus("PARSED");
        r.setPreviewJson("[{\"col1\":\"demo\",\"col2\":\"value\"}]");
        r.setCreatedBy(operator.getUsername());
        uploadMapper.insert(r);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_UPLOAD", "ing_upload_record", String.valueOf(r.getId()), r.getFileName());
        return r.getId();
    }

    public List<IngIngestChannel> listChannels(String channelType) {
        LambdaQueryWrapper<IngIngestChannel> q = new LambdaQueryWrapper<IngIngestChannel>().orderByAsc(IngIngestChannel::getId);
        if (channelType != null && !channelType.isBlank()) {
            q.eq(IngIngestChannel::getChannelType, channelType);
        }
        return channelMapper.selectList(q);
    }

    @Transactional
    public Long createChannel(UserPrincipal operator, Map<String, Object> body) {
        String channelType = required(body.get("channelType"), "channelType").toString().trim().toUpperCase();
        String channelName = str(body.get("channelName"), "").trim();
        if (channelName.isEmpty()) {
            throw new BusinessException(400, "请填写任务名称");
        }
        IngIngestChannel ch = new IngIngestChannel();
        ch.setChannelCode("CH_" + channelType + "_" + System.currentTimeMillis());
        ch.setChannelName(channelName);
        ch.setChannelType(channelType);
        ch.setStatus("IDLE");
        Object cfg = body.get("config");
        if (cfg instanceof Map<?, ?> cfgMap) {
            try {
                ch.setConfigJson(objectMapper.writeValueAsString(cfgMap));
            } catch (Exception e) {
                throw new BusinessException(400, "通道配置格式无效");
            }
        } else {
            ch.setConfigJson("{}");
        }
        channelMapper.insert(ch);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_CHANNEL_CREATE", "ing_ingest_channel", String.valueOf(ch.getId()), ch.getChannelName());
        return ch.getId();
    }

    public Map<String, Object> runChannel(UserPrincipal operator, Long id) {
        IngIngestChannel ch = channelMapper.selectById(id);
        if (ch == null) {
            throw new BusinessException(404, "接入通道不存在");
        }
        if ("TABLE".equals(ch.getChannelType())) {
            return runTableChannelToOds(operator, ch);
        }
        String integrationNote = "demo";
        if ("CDC".equals(ch.getChannelType())) {
            integrationNote = String.valueOf(storageClient.canalStatus().get("status"));
        } else if ("UNSTRUCT".equals(ch.getChannelType())) {
            integrationNote = storageClient.isSeaweedHealthy() ? "SEAWEED_OK" : "SEAWEED_OFFLINE";
        } else if ("SEMI".equals(ch.getChannelType())) {
            integrationNote = "PLACEHOLDER_L3";
        }
        ch.setStatus("SUCCESS");
        ch.setLastRunAt(LocalDateTime.now());
        ch.setLastMessage("ingest ok type=" + ch.getChannelType() + " integration=" + integrationNote
                + " rows=" + (100 + (int) (Math.random() * 500)));
        channelMapper.updateById(ch);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_CHANNEL_RUN", "ing_ingest_channel", String.valueOf(id), ch.getLastMessage());
        return Map.of("channelId", id, "status", ch.getStatus(), "message", ch.getLastMessage(), "integration", integrationNote);
    }

    /** 结构化库表通道：按配置源表经 Kettle 真实抽取到 smart_city_ods。 */
    private Map<String, Object> runTableChannelToOds(UserPrincipal operator, IngIngestChannel ch) {
        Map<String, Object> cfg = readChannelConfig(ch.getConfigJson());
        Object sid = cfg.get("sourceTableId");
        if (sid == null || String.valueOf(sid).isBlank()) {
            throw new BusinessException(400, "请先选择源表并保存接入配置后再执行");
        }
        Long tableId;
        try {
            tableId = Long.valueOf(String.valueOf(sid).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "源表 ID 无效");
        }
        String preferredOds = str(cfg.get("targetTable"), null);
        try {
            Map<String, Object> collect = kettleCollectService.collectTable(operator, tableId, preferredOds);
            String odsTable = String.valueOf(collect.get("odsTable"));
            Object rows = collect.get("collectedRows");
            ch.setStatus("SUCCESS");
            ch.setLastRunAt(LocalDateTime.now());
            ch.setLastMessage("已落入 smart_city_ods." + odsTable + " rows=" + rows);
            channelMapper.updateById(ch);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "ING_CHANNEL_RUN", "ing_ingest_channel", String.valueOf(ch.getId()), ch.getLastMessage());
            Map<String, Object> out = new LinkedHashMap<>(collect);
            out.put("channelId", ch.getId());
            out.put("message", ch.getLastMessage());
            out.put("odsDatabase", "smart_city_ods");
            return out;
        } catch (BusinessException e) {
            ch.setStatus("FAILED");
            ch.setLastRunAt(LocalDateTime.now());
            String msg = e.getMessage() == null ? "汇聚失败" : e.getMessage();
            ch.setLastMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
            channelMapper.updateById(ch);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "ING_CHANNEL_RUN", "ing_ingest_channel", String.valueOf(ch.getId()), ch.getLastMessage());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readChannelConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(configJson, Map.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) {
                        out.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                return out;
            }
        } catch (Exception e) {
            throw new BusinessException(400, "通道配置格式无效，请重新保存");
        }
        return Map.of();
    }

    @Transactional
    public void updateChannel(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngIngestChannel ch = channelMapper.selectById(id);
        if (ch == null) {
            throw new BusinessException(404, "接入通道不存在");
        }
        if (body.containsKey("channelName")) {
            ch.setChannelName(str(body.get("channelName"), ch.getChannelName()));
        }
        Object cfg = body.get("config");
        if (cfg instanceof Map<?, ?> cfgMap) {
            try {
                ch.setConfigJson(objectMapper.writeValueAsString(cfgMap));
            } catch (Exception e) {
                throw new BusinessException(400, "通道配置格式无效");
            }
        }
        channelMapper.updateById(ch);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_CHANNEL_SAVE", "ing_ingest_channel", String.valueOf(id), ch.getChannelName());
    }

    public List<IngPipelineJob> listPipelineJobs(String jobType) {
        LambdaQueryWrapper<IngPipelineJob> q = new LambdaQueryWrapper<IngPipelineJob>().orderByDesc(IngPipelineJob::getId);
        if (jobType != null && !jobType.isBlank()) {
            q.eq(IngPipelineJob::getJobType, jobType);
        }
        return pipelineMapper.selectList(q);
    }

    @Transactional
    public Long runPipeline(UserPrincipal operator, Map<String, Object> body) {
        String jobType = str(body.get("jobType"), "PROBE").toUpperCase();
        IngPipelineJob job = new IngPipelineJob();
        job.setJobCode("JOB_" + jobType + "_" + System.currentTimeMillis());
        job.setJobName(str(body.get("jobName"), jobType + " job"));
        job.setJobType(jobType);
        Object chId = body.get("refChannelId");
        if (chId != null) {
            job.setRefChannelId(Long.valueOf(String.valueOf(chId)));
        }
        job.setStatus("SUCCESS");
        job.setResultJson(buildPipelineResult(jobType, body));
        // 规范设计不再生成「账单(元)」演示金额
        pipelineMapper.insert(job);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_PIPELINE_RUN", "ing_pipeline_job", String.valueOf(job.getId()), jobType);
        return job.getId();
    }

    public Map<String, Object> reconcileApi(String action) {
        return switch (str(action, "analysis").toLowerCase()) {
            case "alert" -> Map.of("api", "reconcile/alert", "alerts", 2, "level", "WARN");
            case "logs" -> Map.of("api", "reconcile/logs", "logCount", 48, "lastAt", LocalDateTime.now().toString());
            case "anomaly" -> Map.of("api", "reconcile/anomaly", "anomalyRows", 3, "status", "OPEN");
            default -> Map.of("api", "reconcile/analysis", "matched", 98.6, "diffRows", 12);
        };
    }

    public List<IngResourceRegistry> listRegistries() {
        return registryMapper.selectList(new LambdaQueryWrapper<IngResourceRegistry>().orderByDesc(IngResourceRegistry::getId));
    }

    @Transactional
    public Long createRegistry(UserPrincipal operator, Map<String, Object> body) {
        IngResourceRegistry r = new IngResourceRegistry();
        r.setRegistryCode(str(body.get("registryCode"), "REG_" + System.currentTimeMillis()));
        r.setTitle(required(body.get("title"), "title").toString());
        r.setCategoryPath(str(body.get("categoryPath"), "政务数据/基础库"));
        r.setSecretLevel(str(body.get("secretLevel"), "INTERNAL"));
        r.setPublishStatus("DRAFT");
        r.setApprovalStatus("PENDING");
        r.setCreatedBy(operator.getUsername());
        registryMapper.insert(r);
        return r.getId();
    }

    @Transactional
    public void approveRegistry(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngResourceRegistry r = registryMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "编目不存在");
        }
        String action = str(body.get("action"), "APPROVE").toUpperCase();
        if ("APPROVE".equals(action)) {
            r.setApprovalStatus("APPROVED");
            r.setPublishStatus("PUBLISHED");
        } else {
            r.setApprovalStatus("REJECTED");
        }
        registryMapper.updateById(r);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_REGISTRY_APPROVE", "ing_resource_registry", String.valueOf(id), r.getApprovalStatus());
    }

    public List<IngGovernPolicy> listPolicies(String policyType) {
        LambdaQueryWrapper<IngGovernPolicy> q = new LambdaQueryWrapper<IngGovernPolicy>().orderByAsc(IngGovernPolicy::getId);
        if (policyType != null && !policyType.isBlank()) {
            q.eq(IngGovernPolicy::getPolicyType, policyType);
        }
        return policyMapper.selectList(q);
    }

    @Transactional
    public Long savePolicy(Map<String, Object> body) {
        String code = str(body == null ? null : body.get("policyCode"), "");
        String name = str(body == null ? null : body.get("policyName"), "");
        String type = str(body == null ? null : body.get("policyType"), "MASK");
        if (code.isBlank() || name.isBlank()) {
            throw new BusinessException(400, "策略编码与名称不能为空");
        }
        Long id = body.get("id") == null || String.valueOf(body.get("id")).isBlank()
                ? null : Long.valueOf(String.valueOf(body.get("id")));
        IngGovernPolicy row = id == null ? new IngGovernPolicy() : policyMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "策略不存在");
        }
        if (id == null) {
            Long exists = policyMapper.selectCount(new LambdaQueryWrapper<IngGovernPolicy>()
                    .eq(IngGovernPolicy::getPolicyCode, code));
            if (exists != null && exists > 0) {
                throw new BusinessException(400, "策略编码已存在");
            }
            row.setPolicyCode(code);
        }
        row.setPolicyName(name);
        row.setPolicyType(type);
        row.setRuleExpr(str(body.get("ruleExpr"), row.getRuleExpr()));
        row.setStatus(str(body.get("status"), row.getStatus() == null ? "ACTIVE" : row.getStatus()));
        row.setLifecycleStage(str(body.get("lifecycleStage"), row.getLifecycleStage()));
        if (id == null) {
            policyMapper.insert(row);
        } else {
            policyMapper.updateById(row);
        }
        return row.getId();
    }

    @Transactional
    public void deletePolicy(Long id) {
        policyMapper.deleteById(id);
    }

    public Map<String, Object> globalAssetView() {
        long assets = assetMapper.selectCount(null);
        long channels = channelMapper.selectCount(null);
        long registries = registryMapper.selectCount(
                new LambdaQueryWrapper<IngResourceRegistry>().eq(IngResourceRegistry::getPublishStatus, "PUBLISHED"));
        return Map.of("totalAssets", assets, "ingestChannels", channels, "publishedRegistries", registries,
                "topAssets", assetMapper.selectList(new LambdaQueryWrapper<BizDataAsset>().last("LIMIT 5")));
    }

    public List<IngHealthMetric> healthMetrics() {
        return healthMapper.selectList(new LambdaQueryWrapper<IngHealthMetric>().orderByAsc(IngHealthMetric::getId));
    }

    public Map<String, Object> searchMetadata(String q) {
        List<Map<String, Object>> hits = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            for (IngResourceRegistry r : registryMapper.selectList(new LambdaQueryWrapper<IngResourceRegistry>()
                    .like(IngResourceRegistry::getTitle, q).last("LIMIT 10"))) {
                hits.add(Map.of("type", "REGISTRY", "id", r.getId(), "title", r.getTitle()));
            }
            for (BizDataAsset a : assetMapper.selectList(new LambdaQueryWrapper<BizDataAsset>()
                    .like(BizDataAsset::getAssetName, q).last("LIMIT 10"))) {
                hits.add(Map.of("type", "ASSET", "id", a.getId(), "title", a.getAssetName()));
            }
        }
        return Map.of("query", q == null ? "" : q, "hits", hits, "engine",
                storageClient.isElasticsearchHealthy() ? "elasticsearch" : "database");
    }

    @Transactional
    public Map<String, Object> runLifecycleDemo(UserPrincipal operator, Long policyId) {
        IngGovernPolicy p = policyMapper.selectById(policyId);
        if (p == null) {
            throw new BusinessException(404, "策略不存在");
        }
        String stage = switch (str(p.getPolicyType(), "")) {
            case "BACKUP" -> "BACKUP_DONE";
            case "ARCHIVE" -> "ARCHIVED";
            case "DESTROY" -> "DESTROYED_RECYCLED";
            default -> "APPLIED";
        };
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_LIFECYCLE", "ing_govern_policy", String.valueOf(policyId), stage);
        return Map.of("policyId", policyId, "policyType", p.getPolicyType(), "lifecycleStage", stage);
    }

    private Map<String, Object> buildLineageGraph() {
        List<Map<String, Object>> nodes = List.of(
                Map.of("id", "src-mysql", "label", "示范业务库", "type", "SOURCE"),
                Map.of("id", "tbl-enterprise", "label", "企业基础表", "type", "TABLE"),
                Map.of("id", "cat-001", "label", "企业基础信息目录", "type", "CATALOG")
        );
        List<Map<String, Object>> edges = List.of(
                Map.of("from", "src-mysql", "to", "tbl-enterprise", "label", "抽取"),
                Map.of("from", "tbl-enterprise", "to", "cat-001", "label", "编目")
        );
        return Map.of("nodes", nodes, "edges", edges);
    }

    private String buildPipelineResult(String jobType, Map<String, Object> body) {
        String category = str(body == null ? null : body.get("dataCategory"), "");
        String categoryLabel = str(body == null ? null : body.get("dataCategoryLabel"), "");
        String categoryJson = category.isBlank()
                ? ""
                : ",\"dataCategory\":\"" + category.replace("\"", "") + "\",\"dataCategoryLabel\":\""
                + categoryLabel.replace("\"", "") + "\"";
        return switch (jobType) {
            case "PROBE" -> "{\"nullRate\":0.02,\"domain\":\"ok\",\"entity\":\"person\"" + categoryJson + "}";
            case "DEFINE" -> "{\"metadata\":8,\"lineage\":\"linked\",\"quality\":\"L2\"" + categoryJson + "}";
            case "READ" -> "{\"channels\":3,\"bytes\":1048576" + categoryJson + "}";
            case "RECONCILE" -> "{\"matched\":98.6,\"diff\":12,\"alert\":false" + categoryJson + "}";
            default -> category.isBlank() ? "{}" : "{\"dataCategory\":\"" + category.replace("\"", "") + "\"}";
        };
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    /**
     * 解析项目部门归属。
     * 仅超级管理员（SYSTEM_ADMIN）可传入/修改 boundOrgId；其他人一律绑定本人部门。
     * @param creating true=新建必须得到有效部门；false=更新时未传则返回 null 表示不改
     */
    private Long resolveBoundOrgIdForWrite(UserPrincipal operator, Map<String, Object> body, boolean creating) {
        Object raw = body == null ? null : body.get("boundOrgId");
        boolean hasBoundOrg = raw != null && !String.valueOf(raw).isBlank();
        if (hasBoundOrg) {
            if (!operator.isSystemAdmin()) {
                throw new BusinessException(403, "仅超级管理员可指定或修改项目部门归属");
            }
            Long orgId;
            try {
                orgId = Long.valueOf(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(400, "部门 ID 无效");
            }
            SysOrg org = orgMapper.selectById(orgId);
            if (org == null) {
                throw new BusinessException(400, "部门不存在: " + orgId);
            }
            return orgId;
        }
        if (!creating) {
            return null;
        }
        if (operator.getOrgId() == null) {
            throw new BusinessException(400, "当前账号未绑定部门，无法登记项目（超级管理员请选择部门归属）");
        }
        return operator.getOrgId();
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }

    private int intVal(Object v, int def) {
        if (v == null) {
            return def;
        }
        return Integer.parseInt(String.valueOf(v));
    }

    /** 各部门默认「其他」项目：PRJ_OTHER 或 PRJ_OTHER_{orgId} */
    private static boolean isBuiltinOtherProject(String projectCode) {
        if (projectCode == null || projectCode.isBlank()) {
            return false;
        }
        return "PRJ_OTHER".equals(projectCode) || projectCode.startsWith("PRJ_OTHER_");
    }

    /** 各部门默认「其他」业务系统：SYS_OTHER 或 SYS_OTHER_{orgId} */
    private static boolean isBuiltinOtherSystem(String systemCode) {
        if (systemCode == null || systemCode.isBlank()) {
            return false;
        }
        return "SYS_OTHER".equals(systemCode) || systemCode.startsWith("SYS_OTHER_");
    }

    /** 各部门默认手动上传源：DS_MANUAL_UPLOAD 或 DS_MANUAL_UPLOAD_{orgId} */
    private static boolean isBuiltinManualUploadSource(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return false;
        }
        return "DS_MANUAL_UPLOAD".equals(sourceCode) || sourceCode.startsWith("DS_MANUAL_UPLOAD_");
    }
}
