package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCatalogApproval;
import com.chengde.smartcity.masterdata.entity.GovCatalogAuthorization;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogSubscription;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTaskRun;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectRun;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectTask;
import com.chengde.smartcity.masterdata.entity.GovMetaRelation;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.GovOmConnector;
import com.chengde.smartcity.masterdata.entity.GovQualityRule;
import com.chengde.smartcity.masterdata.entity.GovQualityRuleConfig;
import com.chengde.smartcity.masterdata.entity.GovQualityTask;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskDetail;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskRun;
import com.chengde.smartcity.masterdata.entity.RcThemeLibrary;
import com.chengde.smartcity.masterdata.mapper.GovCatalogApprovalMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogAuthorizationMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogSubscriptionMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskRunMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaCollectRunMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaCollectTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaRelationMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.GovOmConnectorMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleConfigMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskDetailMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskRunMapper;
import com.chengde.smartcity.masterdata.mapper.RcThemeLibraryMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.kettle.KettleClient;
import com.chengde.smartcity.integration.kettle.KettleKtrCompiler;
import com.chengde.smartcity.masterdata.service.DsOrchestrationService;
import com.chengde.smartcity.masterdata.service.FusionSqlCompiler.CompileResult;
import com.chengde.smartcity.masterdata.service.SharePathSupportService.ColumnDef;
import com.chengde.smartcity.masterdata.service.SharePathSupportService.EligibleTable;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 加工共享黄金路径：任意已汇聚源表 + 声明式 fusionSpec → 主题/专题落地表 → 二次元数据/质量/编目/授权。
 */
@Service
public class ProcessedShareGoldenPathService {

    private static final String DEFAULT_TABLE_CODE = "TBL_ENTERPRISE";
    private static final String THEME_CODE = "THEME_ENTERPRISE";
    private static final String CATALOG_PREFIX = "D_PROCESSED";
    private static final String RULE_PREFIX = "D_PQ";
    private static final BigDecimal PUBLISH_MIN_SCORE = new BigDecimal("50");
    private static final ObjectMapper OM = new ObjectMapper();

    private final SharePathSupportService shareSupport;
    private final FusionSqlCompiler fusionSqlCompiler;
    private final GovOmConnectorMapper connectorMapper;
    private final GovGovernanceTaskMapper governanceTaskMapper;
    private final GovGovernanceTaskRunMapper governanceRunMapper;
    private final RcThemeLibraryMapper themeLibraryMapper;
    private final GovMetaCollectTaskMapper metaTaskMapper;
    private final GovMetaCollectRunMapper metaRunMapper;
    private final GovMetadataRegistryMapper registryMapper;
    private final GovMetaRelationMapper relationMapper;
    private final GovQualityRuleMapper qualityRuleMapper;
    private final GovQualityRuleConfigMapper qualityConfigMapper;
    private final GovQualityTaskMapper qualityTaskMapper;
    private final GovQualityTaskDetailMapper qualityDetailMapper;
    private final GovQualityTaskRunMapper qualityRunMapper;
    private final GovCatalogResourceMapper catalogResourceMapper;
    private final GovCatalogApprovalMapper catalogApprovalMapper;
    private final GovCatalogSubscriptionMapper catalogSubscriptionMapper;
    private final GovCatalogAuthorizationMapper authorizationMapper;
    private final QualityExecuteService qualityExecuteService;
    private final CatalogResourceService catalogResourceService;
    private final CatalogSubscriptionService catalogSubscriptionService;
    private final ResourceCenterPlatformService resourceCenterPlatformService;
    private final DataSource platformDataSource;
    private final KettleClient kettleClient;
    private final KettleKtrCompiler ktrCompiler;
    private final IntegrationProperties integrationProperties;
    private final OpenMetadataSyncService openMetadataSyncService;
    private final DsOrchestrationService dsOrchestrationService;

    public ProcessedShareGoldenPathService(SharePathSupportService shareSupport,
                                           FusionSqlCompiler fusionSqlCompiler,
                                           GovOmConnectorMapper connectorMapper,
                                           GovGovernanceTaskMapper governanceTaskMapper,
                                           GovGovernanceTaskRunMapper governanceRunMapper,
                                           RcThemeLibraryMapper themeLibraryMapper,
                                           GovMetaCollectTaskMapper metaTaskMapper,
                                           GovMetaCollectRunMapper metaRunMapper,
                                           GovMetadataRegistryMapper registryMapper,
                                           GovMetaRelationMapper relationMapper,
                                           GovQualityRuleMapper qualityRuleMapper,
                                           GovQualityRuleConfigMapper qualityConfigMapper,
                                           GovQualityTaskMapper qualityTaskMapper,
                                           GovQualityTaskDetailMapper qualityDetailMapper,
                                           GovQualityTaskRunMapper qualityRunMapper,
                                           GovCatalogResourceMapper catalogResourceMapper,
                                           GovCatalogApprovalMapper catalogApprovalMapper,
                                           GovCatalogSubscriptionMapper catalogSubscriptionMapper,
                                           GovCatalogAuthorizationMapper authorizationMapper,
                                           QualityExecuteService qualityExecuteService,
                                           CatalogResourceService catalogResourceService,
                                           CatalogSubscriptionService catalogSubscriptionService,
                                           ResourceCenterPlatformService resourceCenterPlatformService,
                                           DataSource platformDataSource,
                                           KettleClient kettleClient,
                                           KettleKtrCompiler ktrCompiler,
                                           IntegrationProperties integrationProperties,
                                           OpenMetadataSyncService openMetadataSyncService,
                                           DsOrchestrationService dsOrchestrationService) {
        this.shareSupport = shareSupport;
        this.fusionSqlCompiler = fusionSqlCompiler;
        this.connectorMapper = connectorMapper;
        this.governanceTaskMapper = governanceTaskMapper;
        this.governanceRunMapper = governanceRunMapper;
        this.themeLibraryMapper = themeLibraryMapper;
        this.metaTaskMapper = metaTaskMapper;
        this.metaRunMapper = metaRunMapper;
        this.registryMapper = registryMapper;
        this.relationMapper = relationMapper;
        this.qualityRuleMapper = qualityRuleMapper;
        this.qualityConfigMapper = qualityConfigMapper;
        this.qualityTaskMapper = qualityTaskMapper;
        this.qualityDetailMapper = qualityDetailMapper;
        this.qualityRunMapper = qualityRunMapper;
        this.catalogResourceMapper = catalogResourceMapper;
        this.catalogApprovalMapper = catalogApprovalMapper;
        this.catalogSubscriptionMapper = catalogSubscriptionMapper;
        this.authorizationMapper = authorizationMapper;
        this.qualityExecuteService = qualityExecuteService;
        this.catalogResourceService = catalogResourceService;
        this.catalogSubscriptionService = catalogSubscriptionService;
        this.resourceCenterPlatformService = resourceCenterPlatformService;
        this.platformDataSource = platformDataSource;
        this.kettleClient = kettleClient;
        this.ktrCompiler = ktrCompiler;
        this.integrationProperties = integrationProperties;
        this.openMetadataSyncService = openMetadataSyncService;
        this.dsOrchestrationService = dsOrchestrationService;
    }

    public List<Map<String, Object>> eligibleTables() {
        return shareSupport.listEligibleTables();
    }

    public Map<String, Object> overview(UserPrincipal operator, Long tableId) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        String producedTable = defaultProducedTable(sample);
        String producedEntry = shareSupport.producedEntryCode(producedTable);
        GovGovernanceTask fusionTask = findFusionTask(sample, producedTable);
        GovGovernanceTaskRun fusionRun = latestFusionRun(fusionTask);
        GovMetadataRegistry metadata = findEntry(producedEntry);
        GovQualityTask qualityTask = findQualityTask(producedEntry);
        GovQualityTaskRun qualityRun = latestQualityRun(qualityTask);
        GovCatalogResource resource = findCatalogResource(producedEntry);
        GovCatalogSubscription subscription = findSubscription(resource, operator);
        GovCatalogAuthorization authorization = subscription == null ? null : findAuthorization(subscription.getId());

        Map<String, Object> source = shareSupport.sampleMap(sample);
        source.put("producedTable", producedTable);
        source.put("producedRows", shareSupport.tableExists(producedTable) ? shareSupport.countRows(producedTable) : 0);
        source.put("producedEntryCode", producedEntry);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", source);
        out.put("fusion", fusionMap(fusionTask, fusionRun, producedTable));
        out.put("metadata", metadataMap(metadata));
        out.put("quality", qualityMap(qualityTask, qualityRun));
        out.put("catalog", catalogMap(resource));
        out.put("subscription", subscriptionMap(subscription, authorization));
        out.put("steps", List.of(
                step(1, "源表", sample.ingestTask().getStatus()),
                step(2, "融合入库", fusionRun == null ? "PENDING" : fusionRun.getStatus()),
                step(3, "产出元数据", metadata == null ? "PENDING" : "SUCCESS"),
                step(4, "产出质量", qualityRun == null ? "PENDING" : qualityRun.getStatus()),
                step(5, "融合编目发布", resource == null ? "PENDING" : resource.getPublishStatus()),
                step(6, "订阅授权", authorization == null ? "PENDING" : authorization.getStatus())
        ));
        return out;
    }

    public Map<String, Object> source(Long tableId) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        Map<String, Object> out = shareSupport.sampleMap(sample);
        String produced = defaultProducedTable(sample);
        out.put("producedTable", produced);
        out.put("producedRows", shareSupport.tableExists(produced) ? shareSupport.countRows(produced) : 0);
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> previewFusion(Long tableId, Map<String, Object> body) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        Map<String, Object> spec = resolveFusionSpec(sample, body);
        CompileResult compiled = fusionSqlCompiler.compile(spec);
        Map<String, Object> out = new LinkedHashMap<>(compiled.preview());
        out.put("tableId", sample.table().getId());
        out.put("status", "PREVIEW");
        return out;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> runFusion(UserPrincipal operator, Long tableId, Map<String, Object> body) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        Map<String, Object> spec = resolveFusionSpec(sample, body);
        CompileResult compiled = fusionSqlCompiler.compile(spec);
        ensureThemeLibrary(operator, stringValue(spec.get("themeCode"), THEME_CODE));
        GovGovernanceTask task = ensureFusionTask(operator, sample, compiled, spec);

        LocalDateTime now = LocalDateTime.now();
        GovGovernanceTaskRun run = new GovGovernanceTaskRun();
        run.setTaskId(task.getId());
        run.setTransName(task.getTaskCode());
        run.setStatus("RUNNING");
        run.setStartedAt(now);
        run.setTotalNodes(4);
        run.setSuccessNodes(0);
        run.setFailedNodes(0);
        run.setTriggeredBy(operator.getUsername());
        run.setCreatedAt(now);
        governanceRunMapper.insert(run);
        task.setStatus("RUNNING");
        task.setUpdatedAt(now);
        governanceTaskMapper.updateById(task);

        boolean useKettle = integrationProperties.isEnabled();
        try {
            long producedRows;
            String engineNote;
            if (useKettle) {
                String transName = "FUSION_" + task.getId() + "_" + System.currentTimeMillis();
                producedRows = executeCompiledViaKettle(operator, compiled, transName, run);
                engineNote = "Carte(" + transName + ")";
            } else {
                producedRows = executeCompiled(compiled);
                engineNote = "SQL";
            }
            LocalDateTime ended = LocalDateTime.now();
            run.setStatus("SUCCESS");
            run.setEndedAt(ended);
            run.setSuccessNodes(4);
            run.setRowCount((int) Math.min(producedRows, Integer.MAX_VALUE));
            run.setLineCount((int) Math.min(producedRows, Integer.MAX_VALUE));
            run.setProducedRows(producedRows);
            run.setMessage("[" + engineNote + "] INPUT " + compiled.sourceTable() + " -> OUTPUT " + compiled.targetTable()
                    + "，产出 " + producedRows + " 行");
            governanceRunMapper.updateById(run);
            task.setStatus("READY");
            task.setLastRunAt(ended);
            task.setLastMessage(run.getMessage());
            governanceTaskMapper.updateById(task);

            String themeCode = stringValue(spec.get("themeCode"), THEME_CODE);
            Long managedId = null;
            try {
                managedId = autoManageProduced(operator, themeCode, compiled.targetTable());
            } catch (Exception ignored) {
                // 纳管失败不阻断融合主链
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("runId", run.getId());
            out.put("tableId", sample.table().getId());
            out.put("sourceTable", compiled.sourceTable());
            out.put("producedTable", compiled.targetTable());
            out.put("producedEntryCode", shareSupport.producedEntryCode(compiled.targetTable()));
            out.put("producedRows", producedRows);
            out.put("themeCode", themeCode);
            out.put("managedTableId", managedId);
            out.put("status", "SUCCESS");
            out.put("preview", compiled.preview());
            return out;
        } catch (RuntimeException ex) {
            failFusionRun(task, run, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            failFusionRun(task, run, ex.getMessage());
            throw new BusinessException(500, "融合入库失败: " + ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> collectMetadata(UserPrincipal operator, Long tableId, Map<String, Object> body) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        String producedTable = resolveProducedTable(sample, body);
        if (!shareSupport.tableExists(producedTable) || shareSupport.countRows(producedTable) <= 0) {
            throw new BusinessException(409, "请先完成融合入库");
        }
        String producedEntry = shareSupport.producedEntryCode(producedTable);
        GovOmConnector connector = ensureConnector(operator);
        GovMetaCollectTask task = ensureMetaTask(operator, sample, connector, producedTable);

        LocalDateTime now = LocalDateTime.now();
        GovMetaCollectRun run = new GovMetaCollectRun();
        run.setTaskId(task.getId());
        run.setStatus("RUNNING");
        run.setStartedAt(now);
        run.setLogText("processed-share metadata collect table=" + producedTable);
        metaRunMapper.insert(run);

        try {
            List<ColumnDef> columns = shareSupport.inspectColumns(producedTable);
            upsertEntry(producedEntry, producedTable + "（加工产出）", "TABLE", null,
                    producedTable, run.getId(), "主题库加工产出");
            for (ColumnDef column : columns) {
                upsertEntry(shareSupport.columnEntryCode(producedEntry, column.name()),
                        column.name(), "COLUMN", producedEntry, producedTable, run.getId(), column.typeName());
            }
            String sourceEntry = shareSupport.sourceEntryCode(sample);
            ensureLineage(sourceEntry, producedEntry);

            // 真实同步 OM：产出 DWS 表 upsert；并写 Source→DWS 表级血缘（含对账台账）
            Map<String, Object> omResult = openMetadataSyncService.syncTable(producedEntry, producedTable,
                    toColumnMaps(columns), false);
            Map<String, Object> omLineage = openMetadataSyncService.writeLineage(sourceEntry, producedEntry,
                    "加工融合 " + sample.table().getPhysicalTableName() + " -> " + producedTable);

            run.setStatus("SUCCESS");
            run.setEndedAt(LocalDateTime.now());
            run.setTableCount(1);
            run.setSummary("产出元数据入账成功，表1，字段" + columns.size() + "，OM=" + omResult.get("syncStatus"));
            run.setLogText(run.getLogText() + "\nentryCode=" + producedEntry
                    + "\nomFqn=" + omResult.getOrDefault("fqn", "-")
                    + "\nlineage=" + omLineage.get("syncStatus"));
            metaRunMapper.updateById(run);
            task.setStatus("READY");
            task.setLastRunAt(run.getEndedAt());
            task.setLastMessage(run.getSummary());
            metaTaskMapper.updateById(task);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("runId", run.getId());
            out.put("entryCode", producedEntry);
            out.put("physicalTableName", producedTable);
            out.put("columnCount", columns.size());
            out.put("lineageFrom", sourceEntry);
            out.put("omSyncStatus", omResult.get("syncStatus"));
            out.put("omFqn", omResult.get("fqn"));
            out.put("lineageSyncStatus", omLineage.get("syncStatus"));
            out.put("searchable", findEntry(producedEntry) != null);
            out.put("status", "SUCCESS");
            return out;
        } catch (RuntimeException ex) {
            failMetaRun(task, run, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            failMetaRun(task, run, ex.getMessage());
            throw new BusinessException(500, "产出元数据采集失败: " + ex.getMessage());
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> runQuality(UserPrincipal operator, Long tableId, Map<String, Object> body) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        String producedTable = resolveProducedTable(sample, body);
        String producedEntry = shareSupport.producedEntryCode(producedTable);
        if (findEntry(producedEntry) == null) {
            throw new BusinessException(409, "请先完成产出元数据入账");
        }

        List<Map<String, Object>> rules = body != null && body.get("rules") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : defaultQualityRules(producedTable);

        List<BoundRule> bound = new ArrayList<>();
        int order = 1;
        bound.add(bindRule(operator, sample.table().getId(), producedEntry, producedTable,
                null, "RECORD_COUNT", "产出行数门禁", "COMPLETENESS", order++));
        for (Map<String, Object> rule : rules) {
            String column = stringValue(rule.get("column"), null);
            String checkType = stringValue(rule.get("checkType"), "NULL_CHECK").toUpperCase();
            if (column == null && !"RECORD_COUNT".equals(checkType)) continue;
            if (column != null) shareSupport.requireIdentifier(column, "column");
            bound.add(bindRule(operator, sample.table().getId(), producedEntry, producedTable,
                    column, checkType,
                    stringValue(rule.get("ruleName"), checkType + (column == null ? "" : "-" + column)),
                    stringValue(rule.get("ruleType"), "COMPLETENESS"), order++));
        }

        GovQualityTask task = findQualityTask(producedEntry);
        if (task == null) {
            task = new GovQualityTask();
            task.setTaskName("加工共享质量-" + producedTable);
            task.setDescription("加工共享，绑定 " + producedEntry);
            task.setStatus("READY");
            task.setScheduleType("MANUAL");
            task.setMetadataEntryCode(producedEntry);
            task.setCreatedBy(operator.getUsername());
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            qualityTaskMapper.insert(task);
        }
        for (BoundRule br : bound) {
            ensureQualityDetail(task.getId(), br.ruleId(), producedTable, br.column(), br.checkType(), br.sortOrder());
        }

        Map<String, Object> result = qualityExecuteService.executeTask(operator, task.getId());
        result.put("metadataEntryCode", producedEntry);
        result.put("physicalTableName", producedTable);
        return result;
    }

    @Transactional
    public Map<String, Object> publishCatalog(UserPrincipal operator, Long tableId, Map<String, Object> body) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        String producedTable = resolveProducedTable(sample, body);
        String producedEntry = shareSupport.producedEntryCode(producedTable);
        GovQualityTask qualityTask = findQualityTask(producedEntry);
        GovQualityTaskRun run = latestQualityRun(qualityTask);
        if (run == null || run.getScore() == null) {
            throw new BusinessException(409, "请先完成产出质量稽核");
        }
        if (run.getScore().compareTo(PUBLISH_MIN_SCORE) < 0) {
            throw new BusinessException(409, "质量评分低于发布门槛 " + PUBLISH_MIN_SCORE + "，当前=" + run.getScore());
        }

        String resourceCode = shareSupport.catalogResourceCode(CATALOG_PREFIX, producedTable);
        GovCatalogResource resource = findCatalogResource(producedEntry);
        if (resource == null) {
            Map<String, Object> createBody = new LinkedHashMap<>();
            createBody.put("resourceCode", resourceCode);
            createBody.put("resourceName", producedTable + "（加工共享·主题库）");
            createBody.put("resourceType", "DATA");
            createBody.put("metadataEntryCode", producedEntry);
            createBody.put("physicalTableName", producedTable);
            createBody.put("sourcePathType", "PROCESSED");
            createBody.put("qualityScore", run.getScore());
            createBody.put("categoryPath", "政务/主题库");
            createBody.put("providerOrg", "示范单位");
            createBody.put("resourceFormat", "DATABASE");
            createBody.put("shareType", "CONDITIONAL");
            createBody.put("updateCycle", "DAILY");
            createBody.put("description", "经治理ETL写入主题库；元数据条目=" + producedEntry);
            Long id = catalogResourceService.create(operator, createBody);
            resource = catalogResourceMapper.selectById(id);
        } else {
            resource.setQualityScore(run.getScore());
            catalogResourceMapper.updateById(resource);
        }

        if (!"PUBLISHED".equalsIgnoreCase(resource.getPublishStatus())) {
            GovCatalogApproval approval = catalogApprovalMapper.selectOne(
                    new LambdaQueryWrapper<GovCatalogApproval>()
                            .eq(GovCatalogApproval::getResourceId, resource.getId())
                            .eq(GovCatalogApproval::getStatus, "PENDING")
                            .orderByDesc(GovCatalogApproval::getId)
                            .last("LIMIT 1"));
            if (approval == null) {
                approval = catalogResourceService.submit(operator, resource.getId(),
                        Map.of("actionType", "PUBLISH", "comment", "加工共享发布"));
            }
            catalogResourceService.approve(operator, approval.getId(), Map.of("comment", "质量门禁通过，准予发布"));
            resource = catalogResourceMapper.selectById(resource.getId());
        }
        Map<String, Object> out = catalogMap(resource);
        out.put("qualityGate", "PASSED");
        return out;
    }

    @Transactional
    public Map<String, Object> subscribeAndAuthorize(UserPrincipal operator, Long tableId, Map<String, Object> body) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        String producedTable = resolveProducedTable(sample, body);
        GovCatalogResource resource = findCatalogResource(shareSupport.producedEntryCode(producedTable));
        if (resource == null || !"PUBLISHED".equalsIgnoreCase(resource.getPublishStatus())) {
            throw new BusinessException(409, "请先完成融合资源编目发布");
        }
        GovCatalogSubscription subscription = findSubscription(resource, operator);
        if (subscription == null || List.of("REJECTED", "CANCELLED").contains(subscription.getStatus())) {
            Map<String, Object> apply = new LinkedHashMap<>();
            apply.put("resourceId", resource.getId());
            apply.put("shareMode", stringValue(body.get("shareMode"), "DB_SYNC"));
            apply.put("applicantOrg", stringValue(body.get("applicantOrg"), "机构" + operator.getOrgId()));
            apply.put("purpose", stringValue(body.get("purpose"), "加工共享黄金路径验证"));
            Long id = catalogSubscriptionService.create(operator, apply);
            subscription = catalogSubscriptionMapper.selectById(id);
        }
        if ("PENDING".equalsIgnoreCase(subscription.getStatus())) {
            catalogSubscriptionService.approve(operator, subscription.getId(),
                    Map.of("comment", "本地授权通过，待后续接入真实ESB"));
            subscription = catalogSubscriptionMapper.selectById(subscription.getId());
        }
        GovCatalogAuthorization authorization = findAuthorization(subscription.getId());
        if (authorization == null) {
            throw new BusinessException(409, "订阅已通过但授权台账缺失，请重新审批");
        }
        return subscriptionMap(subscription, authorization);
    }

    // ---------- fusion helpers ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveFusionSpec(EligibleTable sample, Map<String, Object> body) {
        Object raw = body == null ? null : body.get("fusionSpec");
        Map<String, Object> defaults = defaultFusionSpec(sample);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> spec = new LinkedHashMap<>(defaults);
            spec.putAll((Map<String, Object>) map);
            spec.putIfAbsent("sourceTable", sample.table().getPhysicalTableName());
            if (spec.get("targetTable") == null || String.valueOf(spec.get("targetTable")).isBlank()) {
                spec.put("targetTable", defaultProducedTable(sample));
            }
            if (!(spec.get("select") instanceof List<?> list) || list.isEmpty()) {
                if (map.containsKey("select")) {
                    throw new BusinessException(400, "fusionSpec.select 不能为空");
                }
                spec.put("select", defaults.get("select"));
            }
            spec.putIfAbsent("writeMode", "TRUNCATE_INSERT");
            spec.putIfAbsent("themeCode", THEME_CODE);
            return spec;
        }
        return defaults;
    }

    private Map<String, Object> defaultFusionSpec(EligibleTable sample) {
        String source = sample.table().getPhysicalTableName();
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("sourceTable", source);
        spec.put("targetTable", defaultProducedTable(sample));
        spec.put("writeMode", "TRUNCATE_INSERT");
        spec.put("themeCode", THEME_CODE);
        if ("ods_enterprise_base".equalsIgnoreCase(source)) {
            spec.put("filterSql", "enterprise_name IS NOT NULL");
            spec.put("select", List.of(
                    Map.of("expr", "MASK(unified_credit_code,8,4)", "as", "unified_credit_code_masked"),
                    Map.of("expr", "enterprise_name", "as", "enterprise_name"),
                    Map.of("expr", "CASE_LEVEL(registered_capital,2000,800)", "as", "capital_level"),
                    Map.of("expr", "industry_code", "as", "industry_code"),
                    Map.of("expr", "district_code", "as", "district_code")
            ));
        } else if ("ods_project_base".equalsIgnoreCase(source)) {
            spec.put("themeCode", "TOPIC_PROJECT");
            spec.put("filterSql", "project_name IS NOT NULL");
            spec.put("select", List.of(
                    Map.of("expr", "project_code", "as", "project_code"),
                    Map.of("expr", "project_name", "as", "project_name"),
                    Map.of("expr", "CASE_LEVEL(budget_amount,1000,500)", "as", "budget_level"),
                    Map.of("expr", "owner_org", "as", "owner_org"),
                    Map.of("expr", "district_code", "as", "district_code")
            ));
        } else {
            // 通用：复制前若干列 + 第一非空列过滤
            List<ColumnDef> cols = shareSupport.inspectColumns(source);
            if (cols.isEmpty()) {
                throw new BusinessException(400, "源表无字段，无法生成默认加工配置");
            }
            List<Map<String, Object>> select = new ArrayList<>();
            for (int i = 0; i < Math.min(cols.size(), 6); i++) {
                ColumnDef c = cols.get(i);
                if ("id".equalsIgnoreCase(c.name())) continue;
                select.add(Map.of("expr", c.name(), "as", c.name()));
            }
            if (select.isEmpty()) {
                throw new BusinessException(400, "请提供 fusionSpec.select");
            }
            String filterCol = cols.stream()
                    .map(ColumnDef::name)
                    .filter(n -> !n.equalsIgnoreCase("id"))
                    .findFirst().orElse(cols.get(0).name());
            spec.put("filterSql", filterCol + " IS NOT NULL");
            spec.put("select", select);
        }
        return spec;
    }

    private List<Map<String, Object>> defaultQualityRules(String producedTable) {
        List<ColumnDef> cols = shareSupport.inspectColumns(producedTable);
        List<Map<String, Object>> rules = new ArrayList<>();
        for (ColumnDef c : cols) {
            if ("id".equalsIgnoreCase(c.name()) || "fused_at".equalsIgnoreCase(c.name())) continue;
            if (c.name().toLowerCase().contains("name") || c.name().toLowerCase().endsWith("_code")) {
                rules.add(Map.of("column", c.name(), "checkType", "NULL_CHECK", "ruleName", c.name() + "完整性"));
            }
            if (rules.size() >= 2) break;
        }
        return rules;
    }

    private String defaultProducedTable(EligibleTable sample) {
        String physical = sample.table().getPhysicalTableName();
        if ("ods_enterprise_base".equalsIgnoreCase(physical)) {
            return "dws_enterprise_theme";
        }
        if (physical.toLowerCase().startsWith("ods_")) {
            return "dws_" + physical.substring(4);
        }
        return "dws_" + physical;
    }

    private String resolveProducedTable(EligibleTable sample, Map<String, Object> body) {
        if (body != null && body.get("producedTable") != null && !String.valueOf(body.get("producedTable")).isBlank()) {
            return shareSupport.requireIdentifier(String.valueOf(body.get("producedTable")), "producedTable");
        }
        if (body != null && body.get("fusionSpec") instanceof Map<?, ?> spec
                && spec.get("targetTable") != null) {
            return shareSupport.requireIdentifier(String.valueOf(spec.get("targetTable")), "targetTable");
        }
        GovGovernanceTask task = findFusionTask(sample, defaultProducedTable(sample));
        if (task != null && task.getFusionSpecJson() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> saved = OM.readValue(task.getFusionSpecJson(), Map.class);
                if (saved.get("targetTable") != null) {
                    return shareSupport.requireIdentifier(String.valueOf(saved.get("targetTable")), "targetTable");
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return defaultProducedTable(sample);
    }

    private long executeCompiled(CompileResult compiled) {
        String targetQ = DataLayerSupport.qualify(compiled.targetDatabase(), compiled.targetTable());
        try (Connection connection = platformDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS " + targetQ);
            statement.executeUpdate(compiled.ddlSql());
            statement.executeUpdate(compiled.insertSql());
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + targetQ)) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception ex) {
            throw new BusinessException(500, "融合入库执行失败: " + ex.getMessage());
        }
    }

    /**
     * 由 Carte 真实执行加工：平台侧先建 DWS 目标表 DDL，Carte 完成 ODS(源) -> DWS(目标) 的
     * TableInput(白名单 SELECT) -> TableOutput(TRUNCATE 写入)。失败即抛出真实原因。
     */
    private long executeCompiledViaKettle(UserPrincipal operator, CompileResult compiled, String transName, GovGovernanceTaskRun run) {
        String targetQ = DataLayerSupport.qualify(compiled.targetDatabase(), compiled.targetTable());
        // 1) 平台侧建目标表结构（Carte 不负责建表）
        try (Connection connection = platformDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS " + targetQ);
            statement.executeUpdate(compiled.ddlSql());
        } catch (Exception ex) {
            throw new BusinessException(500, "创建目标表失败: " + ex.getMessage());
        }
        if (!kettleClient.isHealthy()) {
            throw new BusinessException(502, "Kettle Carte 不可用，无法以 KETTLE 引擎执行加工");
        }
        // 2) 源=ODS 分层库，目标=DWS 等分层库（Carte 可达地址）
        KettleKtrCompiler.SourceConn src = new KettleKtrCompiler.SourceConn();
        src.host = integrationProperties.getKettle().getTargetHost();
        src.port = integrationProperties.getKettle().getTargetPort();
        src.database = compiled.sourceDatabase();
        src.username = integrationProperties.getKettle().getTargetUser();
        src.password = integrationProperties.getKettle().getTargetPassword();
        // selectSql 已含全限定 FROM，但 Table Input 连接在源库时可改用裸表名；保留编译结果
        String selectSql = compiled.selectSql();
        String ktr = ktrCompiler.compileCopy(transName, src, selectSql,
                compiled.targetDatabase(), compiled.targetTable(), true);
        run.setKettleTransName(transName);
        governanceRunMapper.updateById(run);

        Map<String, Object> add = kettleClient.addTrans(transName, ktr);
        if (!"SUCCESS".equals(add.get("status"))) {
            throw new BusinessException(502, "注册加工转换失败: " + add.get("message"));
        }
        // 通过 DS SHELL 启动并等待 Carte trans 完成
        dsOrchestrationService.runKettleTrans(operator, transName, "FUSION_" + run.getTaskId());

        Map<String, Object> st = kettleClient.getTransStatus(transName);
        String status = String.valueOf(st.get("status"));

        kettleClient.removeTrans(transName);
        if (!"FINISHED".equals(status)) {
            throw new BusinessException(502, "Carte 加工未成功，状态=" + status);
        }
        // 3) 回读真实产出行数
        try (Connection connection = platformDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + targetQ)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception ex) {
            throw new BusinessException(500, "回读产出行数失败: " + ex.getMessage());
        }
    }

    private void ensureThemeLibrary(UserPrincipal operator, String themeCode) {
        RcThemeLibrary theme = themeLibraryMapper.selectOne(new LambdaQueryWrapper<RcThemeLibrary>()
                .eq(RcThemeLibrary::getThemeCode, themeCode)
                .last("LIMIT 1"));
        if (theme == null) {
            boolean topic = themeCode != null && themeCode.toUpperCase().startsWith("TOPIC");
            theme = new RcThemeLibrary();
            theme.setThemeCode(themeCode);
            theme.setThemeName(themeCode);
            theme.setLibraryKind(topic ? "TOPIC" : "THEME");
            theme.setZoneCode(topic ? "ZONE_TOPIC" : "ZONE_THEME");
            theme.setOwnerOrg("示范单位");
            theme.setDescription("加工共享产出库 " + themeCode);
            theme.setPartitionKey("district_code");
            theme.setStatus("ACTIVE");
            theme.setCreatedBy(operator.getUsername());
            theme.setCreatedAt(LocalDateTime.now());
            theme.setUpdatedAt(LocalDateTime.now());
            themeLibraryMapper.insert(theme);
        }
    }

    /** 加工产出自动纳管到资源中心主题/专题库（幂等）。 */
    private Long autoManageProduced(UserPrincipal operator, String themeCode, String physicalTable) {
        RcThemeLibrary theme = themeLibraryMapper.selectOne(new LambdaQueryWrapper<RcThemeLibrary>()
                .eq(RcThemeLibrary::getThemeCode, themeCode)
                .last("LIMIT 1"));
        if (theme == null) {
            ensureThemeLibrary(operator, themeCode);
            theme = themeLibraryMapper.selectOne(new LambdaQueryWrapper<RcThemeLibrary>()
                    .eq(RcThemeLibrary::getThemeCode, themeCode)
                    .last("LIMIT 1"));
        }
        if (theme == null) {
            throw new BusinessException(500, "主题/专题库创建失败: " + themeCode);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("themeId", theme.getId());
        body.put("physicalTable", physicalTable);
        body.put("metaEntryCode", shareSupport.producedEntryCode(physicalTable));
        return resourceCenterPlatformService.manageTable(operator, body);
    }

    private GovGovernanceTask ensureFusionTask(UserPrincipal operator, EligibleTable sample,
                                               CompileResult compiled, Map<String, Object> spec) {
        String code = shareSupport.stableCode("D_FUSION_" + sample.table().getId() + "_" + compiled.targetTable());
        GovGovernanceTask task = governanceTaskMapper.selectOne(new LambdaQueryWrapper<GovGovernanceTask>()
                .eq(GovGovernanceTask::getTaskCode, code)
                .last("LIMIT 1"));
        boolean creating = task == null;
        if (creating) {
            task = new GovGovernanceTask();
            task.setTaskCode(code);
            task.setCreatedBy(operator.getUsername());
            task.setCreatedAt(LocalDateTime.now());
        }
        task.setTaskName("加工融合-" + compiled.targetTable());
        task.setDescription(compiled.sourceTable() + " -> " + compiled.targetTable());
        task.setGraphJson("{\"engine\":\"SAFE_SQL\",\"source\":\"" + compiled.sourceTable()
                + "\",\"target\":\"" + compiled.targetTable() + "\"}");
        try {
            task.setFusionSpecJson(OM.writeValueAsString(spec));
        } catch (Exception e) {
            task.setFusionSpecJson("{}");
        }
        task.setStatus("READY");
        task.setEngineType("SQL");
        task.setUpdatedAt(LocalDateTime.now());
        if (creating) {
            governanceTaskMapper.insert(task);
        } else {
            governanceTaskMapper.updateById(task);
        }
        return task;
    }

    private BoundRule bindRule(UserPrincipal operator, Long tableId, String entryCode, String table,
                               String column, String checkType, String ruleName, String ruleType, int sortOrder) {
        String code = shareSupport.qualityRuleCode(RULE_PREFIX, tableId, checkType, column);
        GovQualityRule rule = ensureQualityRule(operator, code, ruleName, ruleType);
        ensureQualityConfig(operator, rule, entryCode, table, column, checkType);
        return new BoundRule(rule.getId(), column, checkType, sortOrder);
    }

    private GovOmConnector ensureConnector(UserPrincipal operator) {
        GovOmConnector connector = connectorMapper.selectOne(new LambdaQueryWrapper<GovOmConnector>()
                .eq(GovOmConnector::getConnectorCode, "P0_PLATFORM_MYSQL")
                .last("LIMIT 1"));
        if (connector == null) {
            connector = new GovOmConnector();
            connector.setConnectorCode("P0_PLATFORM_MYSQL");
            connector.setConnectorName("平台汇聚库");
            connector.setSourceType("MySQL");
            connector.setStatus("ACTIVE");
            connector.setCreatedBy(operator.getUsername());
            connector.setCreatedAt(LocalDateTime.now());
            connector.setUpdatedAt(LocalDateTime.now());
            connectorMapper.insert(connector);
        }
        return connector;
    }

    private GovMetaCollectTask ensureMetaTask(UserPrincipal operator, EligibleTable sample,
                                              GovOmConnector connector, String producedTable) {
        String code = shareSupport.stableCode("D_META_PROC_" + sample.table().getId() + "_" + producedTable);
        GovMetaCollectTask task = metaTaskMapper.selectOne(new LambdaQueryWrapper<GovMetaCollectTask>()
                .eq(GovMetaCollectTask::getTaskCode, code)
                .last("LIMIT 1"));
        if (task == null) {
            task = new GovMetaCollectTask();
            task.setTaskCode(code);
            task.setTaskName("加工产出元数据-" + producedTable);
            task.setConnectorId(connector.getId());
            task.setIngDataSourceId(sample.source().getId());
            task.setScopeType("TABLE");
            task.setTableList(producedTable);
            task.setStatus("READY");
            task.setCreatedBy(operator.getUsername());
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            metaTaskMapper.insert(task);
        }
        return task;
    }

    private void upsertEntry(String code, String name, String type, String parentCode,
                             String physicalTable, Long runId, String description) {
        GovMetadataRegistry entry = findEntry(code);
        boolean creating = entry == null;
        if (creating) {
            entry = new GovMetadataRegistry();
            entry.setEntryCode(code);
            entry.setChangeFlag("NEW");
        } else {
            entry.setChangeFlag("UNCHANGED");
        }
        entry.setEntryName(name);
        entry.setEntryType(type);
        entry.setParentCode(parentCode);
        entry.setRunId(runId);
        entry.setPhysicalTableName(physicalTable);
        if (physicalTable != null && !physicalTable.isBlank()) {
            String layer = DataLayerSupport.layerForTableName(physicalTable);
            String db = DataLayerSupport.databaseForLayer(layer);
            entry.setDataLayer(layer);
            entry.setDatabaseName(db);
            entry.setSchemaName(db);
        }
        entry.setDescription(description);
        entry.setTags("加工共享,主题库");
        entry.setKeywords(physicalTable);
        entry.setSecurityLevel("INTERNAL");
        entry.setStatus("ACTIVE");
        entry.setUpdatedAt(LocalDateTime.now());
        if (creating) registryMapper.insert(entry);
        else registryMapper.updateById(entry);
    }

    private void ensureLineage(String fromCode, String toCode) {
        GovMetaRelation existing = relationMapper.selectOne(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getFromCode, fromCode)
                .eq(GovMetaRelation::getToCode, toCode)
                .eq(GovMetaRelation::getRelationType, "LINEAGE")
                .last("LIMIT 1"));
        if (existing != null) return;
        GovMetaRelation relation = new GovMetaRelation();
        relation.setFromCode(fromCode);
        relation.setToCode(toCode);
        relation.setRelationType("LINEAGE");
        relation.setLabel("加工融合产出");
        relation.setStatus("ACTIVE");
        relation.setCreatedAt(LocalDateTime.now());
        relationMapper.insert(relation);
    }

    private GovQualityRule ensureQualityRule(UserPrincipal operator, String code, String name, String type) {
        GovQualityRule rule = qualityRuleMapper.selectOne(new LambdaQueryWrapper<GovQualityRule>()
                .eq(GovQualityRule::getRuleCode, code).last("LIMIT 1"));
        if (rule == null) {
            rule = new GovQualityRule();
            rule.setRuleCode(code);
            rule.setRuleName(name);
            rule.setRuleType(type);
            rule.setOrgId(operator.getOrgId());
            rule.setStatus("ENABLED");
            rule.setCreatedBy(operator.getUsername());
            rule.setCreatedAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());
            qualityRuleMapper.insert(rule);
        }
        return rule;
    }

    private void ensureQualityConfig(UserPrincipal operator, GovQualityRule rule, String entryCode,
                                     String table, String column, String checkType) {
        GovQualityRuleConfig config = qualityConfigMapper.selectOne(new LambdaQueryWrapper<GovQualityRuleConfig>()
                .eq(GovQualityRuleConfig::getRuleId, rule.getId()).last("LIMIT 1"));
        boolean creating = config == null;
        if (creating) {
            config = new GovQualityRuleConfig();
            config.setRuleId(rule.getId());
            config.setCreatedBy(operator.getUsername());
            config.setCreatedAt(LocalDateTime.now());
        }
        config.setMetadataEntryCode(entryCode);
        config.setTargetTable(table);
        config.setTargetColumn(column);
        config.setCheckType(checkType);
        config.setThreshold(BigDecimal.ZERO);
        if ("RECORD_COUNT".equalsIgnoreCase(checkType)) {
            config.setConfigJson("{\"min\":1}");
        }
        config.setStatus("ENABLED");
        config.setUpdatedAt(LocalDateTime.now());
        if (creating) qualityConfigMapper.insert(config);
        else qualityConfigMapper.updateById(config);
    }

    private void ensureQualityDetail(Long taskId, Long ruleId, String table, String column,
                                     String checkType, int sortOrder) {
        GovQualityTaskDetail detail = qualityDetailMapper.selectOne(new LambdaQueryWrapper<GovQualityTaskDetail>()
                .eq(GovQualityTaskDetail::getTaskId, taskId)
                .eq(GovQualityTaskDetail::getRuleId, ruleId)
                .last("LIMIT 1"));
        if (detail == null) {
            detail = new GovQualityTaskDetail();
            detail.setTaskId(taskId);
            detail.setRuleId(ruleId);
            detail.setCreatedAt(LocalDateTime.now());
        }
        detail.setTargetTable(table);
        detail.setTargetColumn(column);
        detail.setCheckType(checkType);
        detail.setSortOrder(sortOrder);
        detail.setStatus("ENABLED");
        detail.setUpdatedAt(LocalDateTime.now());
        if (detail.getId() == null) qualityDetailMapper.insert(detail);
        else qualityDetailMapper.updateById(detail);
    }

    private void failFusionRun(GovGovernanceTask task, GovGovernanceTaskRun run, String message) {
        run.setStatus("FAILED");
        run.setEndedAt(LocalDateTime.now());
        run.setFailedNodes(1);
        run.setMessage("融合入库失败: " + message);
        governanceRunMapper.updateById(run);
        task.setStatus("STOPPED");
        task.setLastRunAt(run.getEndedAt());
        task.setLastMessage(run.getMessage());
        governanceTaskMapper.updateById(task);
    }

    private void failMetaRun(GovMetaCollectTask task, GovMetaCollectRun run, String message) {
        run.setStatus("FAILED");
        run.setEndedAt(LocalDateTime.now());
        run.setSummary("采集失败: " + message);
        metaRunMapper.updateById(run);
        task.setStatus("READY");
        task.setLastRunAt(run.getEndedAt());
        task.setLastMessage(run.getSummary());
        metaTaskMapper.updateById(task);
    }

    private Map<String, Object> fusionMap(GovGovernanceTask task, GovGovernanceTaskRun run, String producedTable) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", run == null ? "PENDING" : run.getStatus());
        if (task != null) {
            out.put("taskId", task.getId());
            out.put("taskCode", task.getTaskCode());
            out.put("taskName", task.getTaskName());
            out.put("lastMessage", task.getLastMessage());
        }
        if (run != null) {
            out.put("runId", run.getId());
            out.put("rowCount", run.getRowCount());
        }
        out.put("producedTable", producedTable);
        out.put("themeCode", THEME_CODE);
        return out;
    }

    private Map<String, Object> metadataMap(GovMetadataRegistry metadata) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", metadata == null ? "PENDING" : metadata.getStatus());
        if (metadata != null) {
            out.put("id", metadata.getId());
            out.put("entryCode", metadata.getEntryCode());
            out.put("entryName", metadata.getEntryName());
            out.put("physicalTableName", metadata.getPhysicalTableName());
            out.put("runId", metadata.getRunId());
        }
        return out;
    }

    private Map<String, Object> qualityMap(GovQualityTask task, GovQualityTaskRun run) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", run == null ? "PENDING" : run.getStatus());
        if (task != null) {
            out.put("taskId", task.getId());
            out.put("taskName", task.getTaskName());
            out.put("metadataEntryCode", task.getMetadataEntryCode());
            out.put("lastScore", task.getLastScore());
        }
        if (run != null) {
            out.put("runId", run.getId());
            out.put("score", run.getScore());
            out.put("issueCount", run.getIssueCount());
        }
        return out;
    }

    private Map<String, Object> catalogMap(GovCatalogResource resource) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", resource == null ? "PENDING" : resource.getPublishStatus());
        if (resource != null) {
            out.put("resourceId", resource.getId());
            out.put("resourceCode", resource.getResourceCode());
            out.put("resourceName", resource.getResourceName());
            out.put("metadataEntryCode", resource.getMetadataEntryCode());
            out.put("sourcePathType", resource.getSourcePathType());
            out.put("qualityScore", resource.getQualityScore());
            out.put("publishStatus", resource.getPublishStatus());
        }
        return out;
    }

    private Map<String, Object> subscriptionMap(GovCatalogSubscription subscription,
                                                GovCatalogAuthorization authorization) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", authorization != null ? authorization.getStatus()
                : subscription == null ? "PENDING" : subscription.getStatus());
        if (subscription != null) {
            out.put("subscriptionId", subscription.getId());
            out.put("subscriptionStatus", subscription.getStatus());
            out.put("shareMode", subscription.getShareMode());
        }
        if (authorization != null) {
            out.put("authorizationId", authorization.getId());
            out.put("authorizationCode", authorization.getAuthorizationCode());
            out.put("authorizationStatus", authorization.getStatus());
            out.put("credentialRef", authorization.getCredentialRef());
        }
        return out;
    }

    private Map<String, Object> step(int no, String name, String status) {
        return Map.of("no", no, "name", name, "status", status == null ? "PENDING" : status);
    }

    private GovGovernanceTask findFusionTask(EligibleTable sample, String producedTable) {
        String code = shareSupport.stableCode("D_FUSION_" + sample.table().getId() + "_" + producedTable);
        return governanceTaskMapper.selectOne(new LambdaQueryWrapper<GovGovernanceTask>()
                .eq(GovGovernanceTask::getTaskCode, code).last("LIMIT 1"));
    }

    private GovGovernanceTaskRun latestFusionRun(GovGovernanceTask task) {
        if (task == null) return null;
        return governanceRunMapper.selectOne(new LambdaQueryWrapper<GovGovernanceTaskRun>()
                .eq(GovGovernanceTaskRun::getTaskId, task.getId())
                .orderByDesc(GovGovernanceTaskRun::getId).last("LIMIT 1"));
    }

    private GovMetadataRegistry findEntry(String entryCode) {
        return registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, entryCode).last("LIMIT 1"));
    }

    private GovQualityTask findQualityTask(String entryCode) {
        return qualityTaskMapper.selectOne(new LambdaQueryWrapper<GovQualityTask>()
                .eq(GovQualityTask::getMetadataEntryCode, entryCode)
                .orderByDesc(GovQualityTask::getId).last("LIMIT 1"));
    }

    private GovQualityTaskRun latestQualityRun(GovQualityTask task) {
        if (task == null) return null;
        return qualityRunMapper.selectOne(new LambdaQueryWrapper<GovQualityTaskRun>()
                .eq(GovQualityTaskRun::getTaskId, task.getId())
                .orderByDesc(GovQualityTaskRun::getId).last("LIMIT 1"));
    }

    private GovCatalogResource findCatalogResource(String entryCode) {
        return catalogResourceMapper.selectOne(new LambdaQueryWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getMetadataEntryCode, entryCode)
                .orderByDesc(GovCatalogResource::getId).last("LIMIT 1"));
    }

    private GovCatalogSubscription findSubscription(GovCatalogResource resource, UserPrincipal operator) {
        if (resource == null || operator == null) return null;
        return catalogSubscriptionMapper.selectOne(new LambdaQueryWrapper<GovCatalogSubscription>()
                .eq(GovCatalogSubscription::getResourceId, resource.getId())
                .eq(GovCatalogSubscription::getApplicantUser, operator.getUsername())
                .orderByDesc(GovCatalogSubscription::getId).last("LIMIT 1"));
    }

    private GovCatalogAuthorization findAuthorization(Long subscriptionId) {
        return authorizationMapper.selectOne(new LambdaQueryWrapper<GovCatalogAuthorization>()
                .eq(GovCatalogAuthorization::getSubscriptionId, subscriptionId).last("LIMIT 1"));
    }

    private List<Map<String, Object>> toColumnMaps(List<ColumnDef> columns) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ColumnDef c : columns) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("columnName", c.name());
            m.put("dataType", c.typeName());
            out.add(m);
        }
        return out;
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) return defaultValue;
        return String.valueOf(value).trim();
    }

    private record BoundRule(Long ruleId, String column, String checkType, int sortOrder) {
    }
}
