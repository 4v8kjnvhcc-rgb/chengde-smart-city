package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCatalogApproval;
import com.chengde.smartcity.masterdata.entity.GovCatalogAuthorization;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogSubscription;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectRun;
import com.chengde.smartcity.masterdata.entity.GovMetaCollectTask;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.GovOmConnector;
import com.chengde.smartcity.masterdata.entity.GovQualityRule;
import com.chengde.smartcity.masterdata.entity.GovQualityRuleConfig;
import com.chengde.smartcity.masterdata.entity.GovQualityTask;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskDetail;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskRun;
import com.chengde.smartcity.masterdata.mapper.GovCatalogApprovalMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogAuthorizationMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogSubscriptionMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaCollectRunMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaCollectTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.GovOmConnectorMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleConfigMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskDetailMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskRunMapper;
import com.chengde.smartcity.masterdata.service.SharePathSupportService.ColumnDef;
import com.chengde.smartcity.masterdata.service.SharePathSupportService.EligibleTable;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 直通共享黄金路径：按 tableId 选择任意已登记且已汇聚表，幂等执行。
 */
@Service
public class DirectShareGoldenPathService {

    private static final String DEFAULT_TABLE_CODE = "TBL_ENTERPRISE";
    private static final String META_PREFIX = "D_META_DIRECT";
    private static final String CATALOG_PREFIX = "D_DIRECT";
    private static final String RULE_PREFIX = "D_Q";
    private static final BigDecimal PUBLISH_MIN_SCORE = new BigDecimal("50");

    private final SharePathSupportService shareSupport;
    private final GovOmConnectorMapper connectorMapper;
    private final GovMetaCollectTaskMapper metaTaskMapper;
    private final GovMetaCollectRunMapper metaRunMapper;
    private final GovMetadataRegistryMapper registryMapper;
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

    public DirectShareGoldenPathService(SharePathSupportService shareSupport,
                                        GovOmConnectorMapper connectorMapper,
                                        GovMetaCollectTaskMapper metaTaskMapper,
                                        GovMetaCollectRunMapper metaRunMapper,
                                        GovMetadataRegistryMapper registryMapper,
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
                                        CatalogSubscriptionService catalogSubscriptionService) {
        this.shareSupport = shareSupport;
        this.connectorMapper = connectorMapper;
        this.metaTaskMapper = metaTaskMapper;
        this.metaRunMapper = metaRunMapper;
        this.registryMapper = registryMapper;
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
    }

    public List<Map<String, Object>> eligibleTables() {
        return shareSupport.listEligibleTables();
    }

    public Map<String, Object> overview(UserPrincipal operator, Long tableId) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        String entryCode = shareSupport.sourceEntryCode(sample);
        GovMetadataRegistry metadata = findEntry(entryCode);
        GovQualityTask qualityTask = findQualityTask(entryCode);
        GovQualityTaskRun qualityRun = latestQualityRun(qualityTask);
        GovCatalogResource resource = findCatalogResource(entryCode);
        GovCatalogSubscription subscription = findSubscription(resource, operator);
        GovCatalogAuthorization authorization = subscription == null ? null : findAuthorization(subscription.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sample", shareSupport.sampleMap(sample));
        out.put("metadata", metadataMap(metadata));
        out.put("quality", qualityMap(qualityTask, qualityRun));
        out.put("catalog", catalogMap(resource));
        out.put("subscription", subscriptionMap(subscription, authorization));
        out.put("steps", List.of(
                step(1, "样例表", sample.ingestTask().getStatus()),
                step(2, "元数据入账", metadata == null ? "PENDING" : "SUCCESS"),
                step(3, "质量挂表", qualityRun == null ? "PENDING" : qualityRun.getStatus()),
                step(4, "编目发布", resource == null ? "PENDING" : resource.getPublishStatus()),
                step(5, "订阅授权", authorization == null ? "PENDING" : authorization.getStatus())
        ));
        return out;
    }

    public Map<String, Object> sample(Long tableId) {
        return shareSupport.sampleMap(shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE));
    }

    @Transactional
    public Map<String, Object> collectMetadata(UserPrincipal operator, Long tableId) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        String physicalTable = shareSupport.requireIdentifier(sample.table().getPhysicalTableName(), "physicalTableName");
        GovOmConnector connector = ensureConnector(operator);
        GovMetaCollectTask task = ensureMetaTask(operator, sample, connector);
        String entryCode = shareSupport.sourceEntryCode(sample);

        LocalDateTime now = LocalDateTime.now();
        GovMetaCollectRun run = new GovMetaCollectRun();
        run.setTaskId(task.getId());
        run.setStatus("RUNNING");
        run.setStartedAt(now);
        run.setLogText("direct-share metadata collect table=" + physicalTable);
        metaRunMapper.insert(run);

        try {
            List<ColumnDef> columns = shareSupport.inspectColumns(physicalTable);
            if (columns.isEmpty()) {
                throw new BusinessException(404, "物理表不存在或无字段: " + physicalTable);
            }
            String sourceEntryCode = shareSupport.stableCode("SRC_ING_" + sample.source().getId());
            upsertEntry(sourceEntryCode, sample.source().getSourceName(), "SOURCE", null,
                    sample, run.getId(), "登记数据源");
            upsertEntry(entryCode, sample.table().getTableName(), "TABLE", sourceEntryCode,
                    sample, run.getId(), "汇聚落地表 " + physicalTable);
            for (ColumnDef column : columns) {
                upsertEntry(shareSupport.columnEntryCode(entryCode, column.name()),
                        column.name(), "COLUMN", entryCode, sample, run.getId(), column.typeName());
            }

            run.setStatus("SUCCESS");
            run.setEndedAt(LocalDateTime.now());
            run.setTableCount(1);
            run.setSummary("元数据入账成功，表1，字段" + columns.size());
            run.setLogText(run.getLogText() + "\nentryCode=" + entryCode + "\ncolumns=" + columns.size());
            metaRunMapper.updateById(run);

            task.setStatus("READY");
            task.setLastRunAt(run.getEndedAt());
            task.setLastMessage(run.getSummary());
            metaTaskMapper.updateById(task);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("runId", run.getId());
            out.put("tableId", sample.table().getId());
            out.put("entryCode", entryCode);
            out.put("physicalTableName", physicalTable);
            out.put("columnCount", columns.size());
            out.put("searchable", findEntry(entryCode) != null);
            out.put("status", "SUCCESS");
            return out;
        } catch (RuntimeException ex) {
            failMetaRun(task, run, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            failMetaRun(task, run, ex.getMessage());
            throw new BusinessException(500, "元数据采集失败: " + ex.getMessage());
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> runQuality(UserPrincipal operator, Long tableId, Map<String, Object> body) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        String entryCode = shareSupport.sourceEntryCode(sample);
        if (findEntry(entryCode) == null) {
            throw new BusinessException(409, "请先完成元数据入账");
        }
        String physical = sample.table().getPhysicalTableName();
        List<Map<String, Object>> rules = body != null && body.get("rules") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();

        List<BoundRule> bound = new ArrayList<>();
        int order = 1;
        // 默认行数门禁
        bound.add(bindRule(operator, sample, entryCode, physical, null, "RECORD_COUNT", "行数门禁", "COMPLETENESS", order++));
        for (Map<String, Object> rule : rules) {
            String column = stringValue(rule.get("column"), null);
            String checkType = stringValue(rule.get("checkType"), "NULL_CHECK").toUpperCase();
            if (column == null && !"RECORD_COUNT".equals(checkType)) {
                continue;
            }
            if (column != null) {
                shareSupport.requireIdentifier(column, "column");
            }
            bound.add(bindRule(operator, sample, entryCode, physical, column, checkType,
                    stringValue(rule.get("ruleName"), checkType + (column == null ? "" : "-" + column)),
                    stringValue(rule.get("ruleType"), "COMPLETENESS"), order++));
        }

        GovQualityTask task = findQualityTask(entryCode);
        if (task == null) {
            task = new GovQualityTask();
            task.setTaskName(shareSupport.qualityTaskName("直通共享质量", sample));
            task.setDescription("直通共享，绑定 " + entryCode);
            task.setStatus("READY");
            task.setScheduleType("MANUAL");
            task.setMetadataEntryCode(entryCode);
            task.setCreatedBy(operator.getUsername());
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            qualityTaskMapper.insert(task);
        }
        for (BoundRule br : bound) {
            ensureQualityDetail(task.getId(), br.ruleId(), physical, br.column(), br.checkType(), br.sortOrder());
        }

        Map<String, Object> result = qualityExecuteService.executeTask(operator, task.getId());
        result.put("metadataEntryCode", entryCode);
        result.put("tableId", sample.table().getId());
        result.put("physicalTableName", physical);
        return result;
    }

    @Transactional
    public Map<String, Object> publishCatalog(UserPrincipal operator, Long tableId) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        String entryCode = shareSupport.sourceEntryCode(sample);
        GovQualityTask qualityTask = findQualityTask(entryCode);
        GovQualityTaskRun run = latestQualityRun(qualityTask);
        if (run == null || run.getScore() == null) {
            throw new BusinessException(409, "请先完成质量稽核");
        }
        if (run.getScore().compareTo(PUBLISH_MIN_SCORE) < 0) {
            throw new BusinessException(409, "质量评分低于发布门槛 " + PUBLISH_MIN_SCORE + "，当前=" + run.getScore());
        }

        String resourceCode = shareSupport.catalogResourceCode(CATALOG_PREFIX, sample);
        GovCatalogResource resource = findCatalogResource(entryCode);
        if (resource == null) {
            Map<String, Object> createBody = new LinkedHashMap<>();
            createBody.put("resourceCode", resourceCode);
            createBody.put("resourceName", sample.table().getTableName() + "（直通共享）");
            createBody.put("resourceType", "DATA");
            createBody.put("metadataEntryCode", entryCode);
            createBody.put("dataSourceId", sample.source().getId());
            createBody.put("physicalTableName", sample.table().getPhysicalTableName());
            createBody.put("sourcePathType", "DIRECT");
            createBody.put("qualityScore", run.getScore());
            createBody.put("categoryPath", "政务/基础库");
            createBody.put("providerOrg", "示范单位");
            createBody.put("resourceFormat", "DATABASE");
            createBody.put("shareType", "CONDITIONAL");
            createBody.put("updateCycle", "DAILY");
            createBody.put("description", "来源于已登记、已汇聚表；元数据条目=" + entryCode);
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
                        Map.of("actionType", "PUBLISH", "comment", "直通共享发布"));
            }
            catalogResourceService.approve(operator, approval.getId(), Map.of("comment", "质量门禁通过，准予发布"));
            resource = catalogResourceMapper.selectById(resource.getId());
        }

        Map<String, Object> out = catalogMap(resource);
        out.put("qualityGate", "PASSED");
        out.put("minimumScore", PUBLISH_MIN_SCORE);
        return out;
    }

    @Transactional
    public Map<String, Object> subscribeAndAuthorize(UserPrincipal operator, Long tableId, Map<String, Object> body) {
        EligibleTable sample = shareSupport.requireEligibleOrDefault(tableId, DEFAULT_TABLE_CODE);
        GovCatalogResource resource = findCatalogResource(shareSupport.sourceEntryCode(sample));
        if (resource == null || !"PUBLISHED".equalsIgnoreCase(resource.getPublishStatus())) {
            throw new BusinessException(409, "请先完成资源编目发布");
        }

        GovCatalogSubscription subscription = findSubscription(resource, operator);
        if (subscription == null || List.of("REJECTED", "CANCELLED").contains(subscription.getStatus())) {
            Map<String, Object> apply = new LinkedHashMap<>();
            apply.put("resourceId", resource.getId());
            apply.put("shareMode", stringValue(body.get("shareMode"), "DB_SYNC"));
            apply.put("applicantOrg", stringValue(body.get("applicantOrg"), "机构" + operator.getOrgId()));
            apply.put("purpose", stringValue(body.get("purpose"), "直通共享黄金路径验证"));
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

    private BoundRule bindRule(UserPrincipal operator, EligibleTable sample, String entryCode,
                               String table, String column, String checkType, String ruleName,
                               String ruleType, int sortOrder) {
        String code = shareSupport.qualityRuleCode(RULE_PREFIX, sample.table().getId(), checkType, column);
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
            connector.setLastMessage("使用平台 DataSource 探测");
            connector.setCreatedBy(operator.getUsername());
            connector.setCreatedAt(LocalDateTime.now());
            connector.setUpdatedAt(LocalDateTime.now());
            connectorMapper.insert(connector);
        }
        return connector;
    }

    private GovMetaCollectTask ensureMetaTask(UserPrincipal operator, EligibleTable sample, GovOmConnector connector) {
        String code = shareSupport.metaTaskCode(META_PREFIX, sample);
        GovMetaCollectTask task = metaTaskMapper.selectOne(new LambdaQueryWrapper<GovMetaCollectTask>()
                .eq(GovMetaCollectTask::getTaskCode, code)
                .last("LIMIT 1"));
        if (task == null) {
            task = new GovMetaCollectTask();
            task.setTaskCode(code);
            task.setTaskName("直通元数据采集-" + sample.table().getTableName());
            task.setConnectorId(connector.getId());
            task.setIngDataSourceId(sample.source().getId());
            task.setIngTableId(sample.table().getId());
            task.setScopeType("TABLE");
            task.setTableList(sample.table().getPhysicalTableName());
            task.setStatus("READY");
            task.setCreatedBy(operator.getUsername());
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            metaTaskMapper.insert(task);
        }
        return task;
    }

    private void upsertEntry(String code, String name, String type, String parentCode,
                             EligibleTable sample, Long runId, String description) {
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
        entry.setDataSourceId(sample.source().getId());
        entry.setSourceTableId(sample.table().getId());
        entry.setPhysicalTableName(sample.table().getPhysicalTableName());
        entry.setDescription(description);
        entry.setTags("直通共享");
        entry.setKeywords(sample.source().getSourceName() + "," + sample.table().getTableName());
        entry.setSecurityLevel("INTERNAL");
        entry.setStatus("ACTIVE");
        entry.setUpdatedAt(LocalDateTime.now());
        if (creating) {
            registryMapper.insert(entry);
        } else {
            registryMapper.updateById(entry);
        }
    }

    private GovQualityRule ensureQualityRule(UserPrincipal operator, String code, String name, String type) {
        GovQualityRule rule = qualityRuleMapper.selectOne(new LambdaQueryWrapper<GovQualityRule>()
                .eq(GovQualityRule::getRuleCode, code)
                .last("LIMIT 1"));
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
                .eq(GovQualityRuleConfig::getRuleId, rule.getId())
                .last("LIMIT 1"));
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
        if (creating) {
            qualityConfigMapper.insert(config);
        } else {
            qualityConfigMapper.updateById(config);
        }
    }

    private void ensureQualityDetail(Long taskId, Long ruleId, String table, String column,
                                     String checkType, int sortOrder) {
        GovQualityTaskDetail detail = qualityDetailMapper.selectOne(
                new LambdaQueryWrapper<GovQualityTaskDetail>()
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
        if (detail.getId() == null) {
            qualityDetailMapper.insert(detail);
        } else {
            qualityDetailMapper.updateById(detail);
        }
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
            out.put("lastMessage", task.getLastMessage());
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
            out.put("approvalStatus", resource.getApprovalStatus());
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
            out.put("applicantOrg", subscription.getApplicantOrg());
        }
        if (authorization != null) {
            out.put("authorizationId", authorization.getId());
            out.put("authorizationCode", authorization.getAuthorizationCode());
            out.put("authorizationStatus", authorization.getStatus());
            out.put("credentialRef", authorization.getCredentialRef());
            out.put("validFrom", authorization.getValidFrom());
        }
        return out;
    }

    private Map<String, Object> step(int no, String name, String status) {
        return Map.of("no", no, "name", name, "status", status == null ? "PENDING" : status);
    }

    private GovMetadataRegistry findEntry(String entryCode) {
        return registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, entryCode)
                .last("LIMIT 1"));
    }

    private GovQualityTask findQualityTask(String entryCode) {
        return qualityTaskMapper.selectOne(new LambdaQueryWrapper<GovQualityTask>()
                .eq(GovQualityTask::getMetadataEntryCode, entryCode)
                .orderByDesc(GovQualityTask::getId)
                .last("LIMIT 1"));
    }

    private GovQualityTaskRun latestQualityRun(GovQualityTask task) {
        if (task == null) return null;
        return qualityRunMapper.selectOne(new LambdaQueryWrapper<GovQualityTaskRun>()
                .eq(GovQualityTaskRun::getTaskId, task.getId())
                .orderByDesc(GovQualityTaskRun::getId)
                .last("LIMIT 1"));
    }

    private GovCatalogResource findCatalogResource(String entryCode) {
        return catalogResourceMapper.selectOne(new LambdaQueryWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getMetadataEntryCode, entryCode)
                .orderByDesc(GovCatalogResource::getId)
                .last("LIMIT 1"));
    }

    private GovCatalogSubscription findSubscription(GovCatalogResource resource, UserPrincipal operator) {
        if (resource == null || operator == null) return null;
        return catalogSubscriptionMapper.selectOne(new LambdaQueryWrapper<GovCatalogSubscription>()
                .eq(GovCatalogSubscription::getResourceId, resource.getId())
                .eq(GovCatalogSubscription::getApplicantUser, operator.getUsername())
                .orderByDesc(GovCatalogSubscription::getId)
                .last("LIMIT 1"));
    }

    private GovCatalogAuthorization findAuthorization(Long subscriptionId) {
        return authorizationMapper.selectOne(new LambdaQueryWrapper<GovCatalogAuthorization>()
                .eq(GovCatalogAuthorization::getSubscriptionId, subscriptionId)
                .last("LIMIT 1"));
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) return defaultValue;
        return String.valueOf(value).trim();
    }

    private record BoundRule(Long ruleId, String column, String checkType, int sortOrder) {
    }
}
