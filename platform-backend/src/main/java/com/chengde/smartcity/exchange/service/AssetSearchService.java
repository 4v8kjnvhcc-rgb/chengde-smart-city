package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
import com.chengde.smartcity.exchange.entity.IngAssetTagBinding;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngResourceRegistry;
import com.chengde.smartcity.exchange.entity.IngSearchAuditLog;
import com.chengde.smartcity.exchange.entity.IngSearchDocument;
import com.chengde.smartcity.exchange.entity.IngSearchGlobalBinding;
import com.chengde.smartcity.exchange.entity.IngSearchGlobalField;
import com.chengde.smartcity.exchange.entity.IngSearchIdentity;
import com.chengde.smartcity.exchange.entity.IngSearchKnowledge;
import com.chengde.smartcity.exchange.entity.IngSearchQueryLog;
import com.chengde.smartcity.exchange.entity.IngSearchSavedQuery;
import com.chengde.smartcity.exchange.entity.IngSearchSyncPolicy;
import com.chengde.smartcity.exchange.mapper.IngAssetTagBindingMapper;
import com.chengde.smartcity.exchange.mapper.IngAssetTagMapper;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngResourceRegistryMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchAuditLogMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchDocumentMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchGlobalBindingMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchGlobalFieldMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchIdentityMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchKnowledgeMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchQueryLogMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchSavedQueryMapper;
import com.chengde.smartcity.exchange.mapper.IngSearchSyncPolicyMapper;
import com.chengde.smartcity.integration.jdbc.JdbcProbeService;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据搜索门户：引擎配置 / 全局条件 / 元数据找表 / 精确·模糊·组合检索 / 标识融合画像 / 审计。
 * ES 健康时同步增强目录索引；DB 文档库保证离线可用。
 */
@Service
public class AssetSearchService {

    private static final Logger log = LoggerFactory.getLogger(AssetSearchService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final int BROWSE_MAX = 100;

    private final IngSearchDocumentMapper docMapper;
    private final IngSearchSyncPolicyMapper syncMapper;
    private final IngSearchKnowledgeMapper knowledgeMapper;
    private final IngSearchGlobalFieldMapper globalFieldMapper;
    private final IngSearchGlobalBindingMapper globalBindingMapper;
    private final IngSearchIdentityMapper identityMapper;
    private final IngSearchSavedQueryMapper savedQueryMapper;
    private final IngSearchQueryLogMapper queryLogMapper;
    private final IngSearchAuditLogMapper auditMapper;
    private final IngDataTableMapper tableMapper;
    private final IngDataColumnMapper columnMapper;
    private final IngDataSourceMapper sourceMapper;
    private final IngResourceRegistryMapper registryMapper;
    private final IngAssetTagMapper tagMapper;
    private final IngAssetTagBindingMapper tagBindingMapper;
    private final JdbcProbeService jdbcProbeService;
    private final StorageIntegrationClient storageClient;

    public AssetSearchService(
            IngSearchDocumentMapper docMapper,
            IngSearchSyncPolicyMapper syncMapper,
            IngSearchKnowledgeMapper knowledgeMapper,
            IngSearchGlobalFieldMapper globalFieldMapper,
            IngSearchGlobalBindingMapper globalBindingMapper,
            IngSearchIdentityMapper identityMapper,
            IngSearchSavedQueryMapper savedQueryMapper,
            IngSearchQueryLogMapper queryLogMapper,
            IngSearchAuditLogMapper auditMapper,
            IngDataTableMapper tableMapper,
            IngDataColumnMapper columnMapper,
            IngDataSourceMapper sourceMapper,
            IngResourceRegistryMapper registryMapper,
            IngAssetTagMapper tagMapper,
            IngAssetTagBindingMapper tagBindingMapper,
            JdbcProbeService jdbcProbeService,
            StorageIntegrationClient storageClient) {
        this.docMapper = docMapper;
        this.syncMapper = syncMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.globalFieldMapper = globalFieldMapper;
        this.globalBindingMapper = globalBindingMapper;
        this.identityMapper = identityMapper;
        this.savedQueryMapper = savedQueryMapper;
        this.queryLogMapper = queryLogMapper;
        this.auditMapper = auditMapper;
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
        this.sourceMapper = sourceMapper;
        this.registryMapper = registryMapper;
        this.tagMapper = tagMapper;
        this.tagBindingMapper = tagBindingMapper;
        this.jdbcProbeService = jdbcProbeService;
        this.storageClient = storageClient;
    }

    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("docCount", docMapper.selectCount(new LambdaQueryWrapper<IngSearchDocument>().eq(IngSearchDocument::getStatus, "ACTIVE")));
        out.put("syncPolicies", syncMapper.selectCount(new LambdaQueryWrapper<IngSearchSyncPolicy>().eq(IngSearchSyncPolicy::getStatus, "ACTIVE")));
        out.put("knowledgeCount", knowledgeMapper.selectCount(new LambdaQueryWrapper<IngSearchKnowledge>().eq(IngSearchKnowledge::getStatus, "ACTIVE")));
        out.put("globalFields", globalFieldMapper.selectCount(new LambdaQueryWrapper<IngSearchGlobalField>().eq(IngSearchGlobalField::getStatus, "ACTIVE")));
        out.put("globalBindings", globalBindingMapper.selectCount(null));
        out.put("identities", identityMapper.selectCount(new LambdaQueryWrapper<IngSearchIdentity>().eq(IngSearchIdentity::getStatus, "ACTIVE")));
        out.put("esHealthy", storageClient.isElasticsearchHealthy());
        out.put("engine", storageClient.isElasticsearchHealthy() ? "elasticsearch+database" : "database");
        IngSearchSyncPolicy last = syncMapper.selectOne(new LambdaQueryWrapper<IngSearchSyncPolicy>()
                .isNotNull(IngSearchSyncPolicy::getLastSuccessAt)
                .orderByDesc(IngSearchSyncPolicy::getLastSuccessAt).last("LIMIT 1"));
        out.put("lastSuccessAt", last == null ? null : last.getLastSuccessAt());
        out.put("uiConfig", uiConfig());
        return out;
    }

    // ---------- sync ----------

    public List<IngSearchSyncPolicy> listSyncPolicies() {
        return syncMapper.selectList(new LambdaQueryWrapper<IngSearchSyncPolicy>().orderByAsc(IngSearchSyncPolicy::getId));
    }

    @Transactional
    public Long saveSyncPolicy(Map<String, Object> body) {
        String code = str(body.get("policyCode"), "");
        String name = str(body.get("policyName"), "");
        if (code.isBlank() || name.isBlank()) throw new BusinessException(400, "策略编码与名称不能为空");
        Long id = longVal(body.get("id"));
        IngSearchSyncPolicy row = id == null ? new IngSearchSyncPolicy() : syncMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "同步策略不存在");
        if (id == null) {
            if (syncMapper.selectCount(new LambdaQueryWrapper<IngSearchSyncPolicy>().eq(IngSearchSyncPolicy::getPolicyCode, code)) > 0) {
                throw new BusinessException(400, "策略编码已存在");
            }
            row.setPolicyCode(code);
            row.setCreateTime(LocalDateTime.now());
            row.setDocCount(0);
        }
        row.setPolicyName(name);
        row.setScopeType(str(body.get("scopeType"), row.getScopeType() == null ? "ALL" : row.getScopeType()));
        row.setScopeRef(str(body.get("scopeRef"), row.getScopeRef()));
        row.setUpdateStrategy(str(body.get("updateStrategy"), row.getUpdateStrategy() == null ? "SCHEDULED" : row.getUpdateStrategy()));
        row.setCronExpr(str(body.get("cronExpr"), row.getCronExpr()));
        row.setIncludeTables(intVal(body.get("includeTables"), row.getIncludeTables() == null ? 1 : row.getIncludeTables()));
        row.setIncludeColumns(intVal(body.get("includeColumns"), row.getIncludeColumns() == null ? 1 : row.getIncludeColumns()));
        row.setIncludeRegistry(intVal(body.get("includeRegistry"), row.getIncludeRegistry() == null ? 1 : row.getIncludeRegistry()));
        row.setIncludeDataSample(intVal(body.get("includeDataSample"), row.getIncludeDataSample() == null ? 0 : row.getIncludeDataSample()));
        row.setRetryMax(intVal(body.get("retryMax"), row.getRetryMax() == null ? 3 : row.getRetryMax()));
        row.setStatus(str(body.get("status"), row.getStatus() == null ? "ACTIVE" : row.getStatus()));
        if (id == null) syncMapper.insert(row);
        else syncMapper.updateById(row);
        return row.getId();
    }

    @Transactional
    public Map<String, Object> runSync(UserPrincipal principal, Long policyId) {
        IngSearchSyncPolicy policy = syncMapper.selectById(policyId);
        if (policy == null) throw new BusinessException(404, "同步策略不存在");
        policy.setLastRunAt(LocalDateTime.now());
        policy.setLastStatus("RUNNING");
        syncMapper.updateById(policy);
        int indexed = 0;
        int esOk = 0;
        try {
            if (policy.getIncludeTables() == null || policy.getIncludeTables() == 1) {
                indexed += syncTables();
            }
            if (policy.getIncludeColumns() != null && policy.getIncludeColumns() == 1) {
                indexed += syncColumns();
            }
            if (policy.getIncludeRegistry() == null || policy.getIncludeRegistry() == 1) {
                indexed += syncRegistries();
            }
            indexed += syncIdentities();
            if (storageClient.isElasticsearchHealthy()) {
                for (IngSearchDocument d : docMapper.selectList(new LambdaQueryWrapper<IngSearchDocument>()
                        .eq(IngSearchDocument::getDocType, "REGISTRY").eq(IngSearchDocument::getStatus, "ACTIVE").last("LIMIT 100"))) {
                    Map<String, Object> r = storageClient.indexCatalog(
                            String.valueOf(d.getAssetId() == null ? d.getId() : d.getAssetId()),
                            d.getAssetCode(), d.getTitle(), d.getSummary());
                    if ("INDEXED".equals(r.get("indexStatus"))) esOk++;
                }
            }
            policy.setLastSuccessAt(LocalDateTime.now());
            policy.setLastStatus("SUCCESS");
            policy.setLastMessage("indexed=" + indexed + ", es=" + esOk);
            policy.setDocCount(indexed);
            syncMapper.updateById(policy);
            audit(principal, "SYNC", policy.getPolicyCode(),
                    "{\"indexed\":" + indexed + ",\"es\":" + esOk + ",\"strategy\":\"" + policy.getUpdateStrategy() + "\"}");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("policyId", policyId);
            out.put("indexed", indexed);
            out.put("esIndexed", esOk);
            out.put("engine", storageClient.isElasticsearchHealthy() ? "elasticsearch+database" : "database");
            out.put("docTotal", docMapper.selectCount(new LambdaQueryWrapper<IngSearchDocument>().eq(IngSearchDocument::getStatus, "ACTIVE")));
            return out;
        } catch (Exception e) {
            policy.setLastStatus("FAILED");
            policy.setLastMessage(e.getMessage() == null ? "sync failed" : e.getMessage().substring(0, Math.min(500, e.getMessage().length())));
            syncMapper.updateById(policy);
            throw new BusinessException(500, "同步失败: " + e.getMessage());
        }
    }

    private int syncTables() {
        int n = 0;
        List<IngDataTable> tables = tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>().orderByDesc(IngDataTable::getId).last("LIMIT 2000"));
        for (IngDataTable t : tables) {
            List<String> tagNames = tableTagNames(t.getId());
            String content = join(" ", t.getTableCode(), t.getTableName(), t.getUsageDesc(),
                    t.getPhysicalTableName(), String.join(" ", tagNames), expandSynonyms(join(" ", t.getTableName(), t.getUsageDesc())));
            Map<String, Object> high = new LinkedHashMap<>();
            high.put("tableName", t.getTableName());
            high.put("physicalTable", t.getPhysicalTableName());
            high.put("tagNames", tagNames);
            Map<String, Object> mid = new LinkedHashMap<>();
            mid.put("tableCode", t.getTableCode());
            mid.put("usageDesc", t.getUsageDesc());
            mid.put("sourceSchema", t.getSourceSchema());
            upsertDoc("TABLE:" + t.getId(), "TABLE", t.getId(), t.getTableCode(),
                    nullTo(t.getTableName(), t.getTableCode()),
                    t.getUsageDesc(), content, toJson(tagNames), null,
                    null, t.getSourceSchema(), nullTo(t.getPhysicalTableName(), t.getSourceTable()),
                    t.getSourceId(), new BigDecimal("70"), null, toJson(high), toJson(mid));
            n++;
        }
        return n;
    }

    private int syncColumns() {
        int n = 0;
        List<IngDataColumn> cols = columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>().orderByDesc(IngDataColumn::getId).last("LIMIT 5000"));
        for (IngDataColumn c : cols) {
            IngDataTable t = tableMapper.selectById(c.getTableId());
            String title = (t == null ? "" : nullTo(t.getTableName(), t.getTableCode()) + ".") + nullTo(c.getColumnName(), c.getColumnCode());
            String content = join(" ", c.getColumnCode(), c.getColumnName(), c.getSemanticDesc(), c.getDataType(),
                    expandSynonyms(nullTo(c.getColumnName(), "") + " " + nullTo(c.getSemanticDesc(), "")));
            Map<String, Object> high = Map.of("columnName", nullTo(c.getColumnName(), ""), "columnCode", nullTo(c.getColumnCode(), ""));
            Map<String, Object> mid = Map.of("dataType", nullTo(c.getDataType(), ""), "tableId", c.getTableId());
            upsertDoc("COLUMN:" + c.getId(), "COLUMN", c.getId(), c.getColumnCode(), title, c.getSemanticDesc(), content,
                    null, null, null, null, t == null ? null : t.getPhysicalTableName(),
                    t == null ? null : t.getSourceId(), new BigDecimal("40"), null, toJson(high), toJson(mid));
            n++;
        }
        return n;
    }

    private int syncRegistries() {
        int n = 0;
        List<IngResourceRegistry> regs = registryMapper.selectList(new LambdaQueryWrapper<IngResourceRegistry>().orderByDesc(IngResourceRegistry::getId).last("LIMIT 1000"));
        for (IngResourceRegistry r : regs) {
            String title = r.getTitle() == null ? "registry-" + r.getId() : r.getTitle();
            upsertDoc("REGISTRY:" + r.getId(), "REGISTRY", r.getId(), null, title, title, title + " " + expandSynonyms(title),
                    null, null, null, null, null, null, new BigDecimal("60"), null,
                    toJson(Map.of("title", title)), toJson(Map.of("id", r.getId())));
            n++;
        }
        return n;
    }

    private int syncIdentities() {
        int n = 0;
        for (IngSearchIdentity id : identityMapper.selectList(new LambdaQueryWrapper<IngSearchIdentity>().eq(IngSearchIdentity::getStatus, "ACTIVE"))) {
            String title = nullTo(id.getDisplayName(), id.getIdValue());
            String content = join(" ", id.getEntityId(), id.getIdType(), id.getIdValue(), id.getDisplayName(), id.getProfileJson());
            upsertDoc("IDENTITY:" + id.getId(), "IDENTITY", id.getId(), id.getIdValue(), title, id.getProfileJson(), content,
                    null, null, null, null, null, null, new BigDecimal("90"), id.getIdValue(),
                    id.getProfileJson(), toJson(Map.of("idType", id.getIdType(), "entityId", id.getEntityId())));
            n++;
        }
        return n;
    }

    private void upsertDoc(String key, String type, Long assetId, String assetCode, String title, String summary,
                           String content, String tags, String category, String pdb, String pschema, String ptable,
                           Long sourceId, BigDecimal weight, String identityKeys, String high, String mid) {
        IngSearchDocument exists = docMapper.selectOne(new LambdaQueryWrapper<IngSearchDocument>().eq(IngSearchDocument::getDocKey, key).last("LIMIT 1"));
        IngSearchDocument d = exists == null ? new IngSearchDocument() : exists;
        d.setDocKey(key);
        d.setDocType(type);
        d.setAssetId(assetId);
        d.setAssetCode(assetCode);
        d.setTitle(title == null ? key : title);
        d.setSummary(truncate(summary, 1000));
        d.setContentText(truncate(content, 8000));
        d.setTagsJson(tags);
        d.setCategoryJson(category);
        d.setPhysicalDb(pdb);
        d.setPhysicalSchema(pschema);
        d.setPhysicalTable(ptable);
        d.setSourceId(sourceId);
        d.setWeightScore(weight);
        d.setIdentityKeys(identityKeys);
        d.setHighAttrsJson(high);
        d.setMidAttrsJson(mid);
        d.setStatus("ACTIVE");
        d.setIndexedAt(LocalDateTime.now());
        if (exists == null) {
            d.setHotScore(0);
            d.setCreateTime(LocalDateTime.now());
            docMapper.insert(d);
        } else {
            docMapper.updateById(d);
        }
    }

    // ---------- knowledge ----------

    public List<IngSearchKnowledge> listKnowledge(String type) {
        LambdaQueryWrapper<IngSearchKnowledge> q = new LambdaQueryWrapper<IngSearchKnowledge>().orderByAsc(IngSearchKnowledge::getPriority);
        if (type != null && !type.isBlank()) q.eq(IngSearchKnowledge::getKnowledgeType, type.trim());
        return knowledgeMapper.selectList(q);
    }

    @Transactional
    public Long saveKnowledge(UserPrincipal principal, Map<String, Object> body) {
        String ktype = str(body.get("knowledgeType"), "");
        String code = str(body.get("knowledgeCode"), "");
        String name = str(body.get("knowledgeName"), "");
        String payload = str(body.get("payloadJson"), "{}");
        if (ktype.isBlank() || code.isBlank() || name.isBlank()) throw new BusinessException(400, "类型/编码/名称不能为空");
        validateJson(payload);
        Long id = longVal(body.get("id"));
        IngSearchKnowledge row = id == null ? new IngSearchKnowledge() : knowledgeMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "业务知识不存在");
        if (id == null) {
            row.setKnowledgeCode(code);
            row.setKnowledgeType(ktype);
            row.setCreateTime(LocalDateTime.now());
        }
        row.setKnowledgeName(name);
        row.setPayloadJson(payload);
        row.setPriority(intVal(body.get("priority"), row.getPriority() == null ? 100 : row.getPriority()));
        row.setStatus(str(body.get("status"), row.getStatus() == null ? "ACTIVE" : row.getStatus()));
        row.setDescription(str(body.get("description"), row.getDescription()));
        if (id == null) knowledgeMapper.insert(row);
        else knowledgeMapper.updateById(row);
        audit(principal, "CONFIG", code, payload);
        return row.getId();
    }

    @Transactional
    public void deleteKnowledge(Long id) {
        knowledgeMapper.deleteById(id);
    }

    // ---------- global fields ----------

    public List<IngSearchGlobalField> listGlobalFields() {
        return globalFieldMapper.selectList(new LambdaQueryWrapper<IngSearchGlobalField>().orderByAsc(IngSearchGlobalField::getId));
    }

    @Transactional
    public Long saveGlobalField(UserPrincipal principal, Map<String, Object> body) {
        String code = str(body.get("fieldCode"), "");
        String name = str(body.get("fieldName"), "");
        if (code.isBlank() || name.isBlank()) throw new BusinessException(400, "字段编码与名称不能为空");
        Long id = longVal(body.get("id"));
        IngSearchGlobalField row = id == null ? new IngSearchGlobalField() : globalFieldMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "全局条件不存在");
        if (id == null) {
            if (globalFieldMapper.selectCount(new LambdaQueryWrapper<IngSearchGlobalField>().eq(IngSearchGlobalField::getFieldCode, code)) > 0) {
                throw new BusinessException(400, "字段编码已存在");
            }
            row.setFieldCode(code);
            row.setVersionNo(1);
            row.setCreateTime(LocalDateTime.now());
        } else {
            row.setVersionNo((row.getVersionNo() == null ? 1 : row.getVersionNo()) + 1);
        }
        row.setFieldName(name);
        row.setSemantic(str(body.get("semantic"), row.getSemantic()));
        row.setDataType(str(body.get("dataType"), row.getDataType() == null ? "STRING" : row.getDataType()));
        row.setControlType(str(body.get("controlType"), row.getControlType() == null ? "INPUT" : row.getControlType()));
        row.setDictCode(str(body.get("dictCode"), row.getDictCode()));
        row.setRequiredFlag(intVal(body.get("requiredFlag"), row.getRequiredFlag() == null ? 0 : row.getRequiredFlag()));
        row.setMatchNameRegex(str(body.get("matchNameRegex"), row.getMatchNameRegex()));
        row.setMatchCommentKeywords(str(body.get("matchCommentKeywords"), row.getMatchCommentKeywords()));
        row.setMatchTags(str(body.get("matchTags"), row.getMatchTags()));
        row.setStatus(str(body.get("status"), row.getStatus() == null ? "ACTIVE" : row.getStatus()));
        row.setDescription(str(body.get("description"), row.getDescription()));
        if (id == null) globalFieldMapper.insert(row);
        else globalFieldMapper.updateById(row);
        audit(principal, "CONFIG", row.getFieldCode(), toJson(row));
        return row.getId();
    }

    @Transactional
    public Map<String, Object> autoMatchGlobalField(UserPrincipal principal, Long fieldId) {
        IngSearchGlobalField field = globalFieldMapper.selectById(fieldId);
        if (field == null) throw new BusinessException(404, "全局条件不存在");
        Pattern namePat = null;
        if (field.getMatchNameRegex() != null && !field.getMatchNameRegex().isBlank()) {
            try { namePat = Pattern.compile(field.getMatchNameRegex(), Pattern.CASE_INSENSITIVE); }
            catch (Exception e) { throw new BusinessException(400, "匹配正则非法"); }
        }
        List<String> kws = splitCsv(field.getMatchCommentKeywords());
        int matched = 0;
        List<IngDataColumn> cols = columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>().last("LIMIT 5000"));
        for (IngDataColumn c : cols) {
            String text = (nullTo(c.getColumnCode(), "") + " " + nullTo(c.getColumnName(), "") + " " + nullTo(c.getSemanticDesc(), "")).toLowerCase(Locale.ROOT);
            double score = 0;
            if (namePat != null && namePat.matcher(text).find()) score += 70;
            for (String kw : kws) {
                if (!kw.isBlank() && text.contains(kw.toLowerCase(Locale.ROOT))) score += 15;
            }
            if (score < 60) continue;
            IngSearchGlobalBinding exists = globalBindingMapper.selectOne(new LambdaQueryWrapper<IngSearchGlobalBinding>()
                    .eq(IngSearchGlobalBinding::getGlobalFieldId, fieldId)
                    .eq(IngSearchGlobalBinding::getTableId, c.getTableId())
                    .eq(IngSearchGlobalBinding::getColumnCode, c.getColumnCode())
                    .last("LIMIT 1"));
            if (exists != null) continue;
            IngSearchGlobalBinding b = new IngSearchGlobalBinding();
            b.setGlobalFieldId(fieldId);
            b.setTableId(c.getTableId());
            b.setColumnId(c.getId());
            b.setColumnCode(c.getColumnCode());
            b.setMatchScore(BigDecimal.valueOf(Math.min(score, 99)));
            b.setConfirmStatus("SUGGESTED");
            b.setMatchSource("AUTO");
            b.setCreateTime(LocalDateTime.now());
            globalBindingMapper.insert(b);
            matched++;
        }
        audit(principal, "GLOBAL_BIND", field.getFieldCode(), "{\"matched\":" + matched + "}");
        return Map.of("fieldId", fieldId, "matched", matched);
    }

    public List<Map<String, Object>> listGlobalBindings(Long fieldId, String confirmStatus) {
        LambdaQueryWrapper<IngSearchGlobalBinding> q = new LambdaQueryWrapper<IngSearchGlobalBinding>().orderByDesc(IngSearchGlobalBinding::getMatchScore);
        if (fieldId != null) q.eq(IngSearchGlobalBinding::getGlobalFieldId, fieldId);
        if (confirmStatus != null && !confirmStatus.isBlank()) q.eq(IngSearchGlobalBinding::getConfirmStatus, confirmStatus.trim());
        q.last("LIMIT 300");
        List<Map<String, Object>> out = new ArrayList<>();
        for (IngSearchGlobalBinding b : globalBindingMapper.selectList(q)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", b.getId());
            row.put("globalFieldId", b.getGlobalFieldId());
            row.put("tableId", b.getTableId());
            row.put("columnId", b.getColumnId());
            row.put("columnCode", b.getColumnCode());
            row.put("matchScore", b.getMatchScore());
            row.put("confirmStatus", b.getConfirmStatus());
            row.put("matchSource", b.getMatchSource());
            IngDataTable t = tableMapper.selectById(b.getTableId());
            if (t != null) {
                row.put("tableName", t.getTableName());
                row.put("tableCode", t.getTableCode());
                row.put("physicalTable", t.getPhysicalTableName());
            }
            IngSearchGlobalField f = globalFieldMapper.selectById(b.getGlobalFieldId());
            if (f != null) row.put("fieldName", f.getFieldName());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public void confirmGlobalBinding(Long bindingId, boolean accept) {
        IngSearchGlobalBinding b = globalBindingMapper.selectById(bindingId);
        if (b == null) throw new BusinessException(404, "绑定不存在");
        b.setConfirmStatus(accept ? "CONFIRMED" : "EXCLUDED");
        globalBindingMapper.updateById(b);
    }

    public Map<String, Object> previewGlobalImpact(Long fieldId) {
        List<Map<String, Object>> binds = listGlobalBindings(fieldId, "CONFIRMED");
        List<Map<String, Object>> suggested = listGlobalBindings(fieldId, "SUGGESTED");
        return Map.of("confirmedTables", binds.size(), "suggestedTables", suggested.size(),
                "confirmed", binds.stream().limit(50).toList(),
                "suggested", suggested.stream().limit(50).toList());
    }

    public List<Map<String, Object>> globalFieldsForTable(Long tableId) {
        List<IngSearchGlobalBinding> binds = globalBindingMapper.selectList(new LambdaQueryWrapper<IngSearchGlobalBinding>()
                .eq(IngSearchGlobalBinding::getTableId, tableId)
                .eq(IngSearchGlobalBinding::getConfirmStatus, "CONFIRMED"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (IngSearchGlobalBinding b : binds) {
            IngSearchGlobalField f = globalFieldMapper.selectById(b.getGlobalFieldId());
            if (f == null || !"ACTIVE".equals(f.getStatus())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fieldId", f.getId());
            row.put("fieldCode", f.getFieldCode());
            row.put("fieldName", f.getFieldName());
            row.put("controlType", f.getControlType());
            row.put("dataType", f.getDataType());
            row.put("requiredFlag", f.getRequiredFlag());
            row.put("columnCode", b.getColumnCode());
            row.put("columnId", b.getColumnId());
            out.add(row);
        }
        return out;
    }

    // ---------- search ----------

    public Map<String, Object> search(UserPrincipal principal, Map<String, Object> body) {
        String mode = str(body.get("mode"), "FUZZY").toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "EXACT" -> exactSearch(principal, body);
            case "META" -> metaSearch(principal, body);
            case "COMBO" -> comboSearch(principal, body);
            default -> fuzzySearch(principal, body);
        };
    }

    private Map<String, Object> fuzzySearch(UserPrincipal principal, Map<String, Object> body) {
        String q = str(body.get("q"), "");
        int limit = Math.min(intVal(body.get("limit"), 50), 100);
        Set<String> tokens = expandQueryTokens(q);
        List<IngSearchDocument> docs = docMapper.selectList(new LambdaQueryWrapper<IngSearchDocument>()
                .eq(IngSearchDocument::getStatus, "ACTIVE").last("LIMIT 3000"));
        List<Map<String, Object>> hits = new ArrayList<>();
        Map<String, Integer> facetsType = new HashMap<>();
        for (IngSearchDocument d : docs) {
            double score = scoreDoc(d, tokens, q);
            if (score <= 0 && !q.isBlank()) continue;
            if (q.isBlank()) score = d.getWeightScore() == null ? 1 : d.getWeightScore().doubleValue();
            hits.add(toHit(d, score, tokens));
            facetsType.merge(d.getDocType(), 1, Integer::sum);
        }
        hits.sort(Comparator.comparingDouble((Map<String, Object> m) -> ((Number) m.get("score")).doubleValue()).reversed());
        if (hits.size() > limit) hits = hits.subList(0, limit);
        Map<String, Object> secondary = buildSecondary(q, hits);
        logQuery(principal, "SEARCH", "FUZZY", q, body, hits.size(), null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "FUZZY");
        out.put("query", q);
        out.put("expandedTokens", tokens);
        out.put("total", hits.size());
        out.put("hits", hits);
        out.put("facets", Map.of("docType", facetsType));
        out.put("weightConfig", weightConfig());
        out.put("secondary", secondary);
        out.put("engine", overview().get("engine"));
        if (hits.isEmpty()) out.put("emptyHint", uiConfig().getOrDefault("emptyHint", "未找到结果"));
        return out;
    }

    private Map<String, Object> exactSearch(UserPrincipal principal, Map<String, Object> body) {
        String idValue = str(body.get("q"), str(body.get("idValue"), ""));
        if (idValue.isBlank()) throw new BusinessException(400, "请输入精确标识号");
        String idTypeHint = detectIdType(idValue);
        List<IngSearchIdentity> ids = identityMapper.selectList(new LambdaQueryWrapper<IngSearchIdentity>()
                .eq(IngSearchIdentity::getIdValue, idValue)
                .eq(IngSearchIdentity::getStatus, "ACTIVE"));
        if (ids.isEmpty()) {
            ids = identityMapper.selectList(new LambdaQueryWrapper<IngSearchIdentity>()
                    .like(IngSearchIdentity::getIdValue, idValue)
                    .eq(IngSearchIdentity::getStatus, "ACTIVE").last("LIMIT 20"));
        }
        Set<String> entityIds = ids.stream().map(IngSearchIdentity::getEntityId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<IngSearchIdentity> cluster = entityIds.isEmpty() ? List.of()
                : identityMapper.selectList(new LambdaQueryWrapper<IngSearchIdentity>()
                .in(IngSearchIdentity::getEntityId, entityIds).eq(IngSearchIdentity::getStatus, "ACTIVE"));
        List<Map<String, Object>> relatedDocs = new ArrayList<>();
        for (IngSearchDocument d : docMapper.selectList(new LambdaQueryWrapper<IngSearchDocument>()
                .eq(IngSearchDocument::getStatus, "ACTIVE")
                .and(w -> w.like(IngSearchDocument::getIdentityKeys, idValue)
                        .or().like(IngSearchDocument::getContentText, idValue)
                        .or().like(IngSearchDocument::getTitle, idValue))
                .last("LIMIT 50"))) {
            relatedDocs.add(toHit(d, 95, Set.of(idValue.toLowerCase(Locale.ROOT))));
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        if (!cluster.isEmpty()) {
            IngSearchIdentity primary = cluster.get(0);
            profile.put("entityId", primary.getEntityId());
            profile.put("displayName", primary.getDisplayName());
            profile.put("profile", parseJson(primary.getProfileJson()));
            profile.put("identifiers", cluster.stream().map(i -> Map.of(
                    "idType", i.getIdType(), "idValue", i.getIdValue(), "sourceSystem", nullTo(i.getSourceSystem(), "")
            )).toList());
        }
        logQuery(principal, "EXACT_PROFILE", "EXACT", idValue, body, relatedDocs.size(), null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "EXACT");
        out.put("query", idValue);
        out.put("detectedIdType", idTypeHint);
        out.put("profile", profile);
        out.put("identityCluster", cluster);
        out.put("hits", relatedDocs);
        out.put("total", relatedDocs.size());
        return out;
    }

    private Map<String, Object> metaSearch(UserPrincipal principal, Map<String, Object> body) {
        String keyword = str(body.get("q"), "");
        @SuppressWarnings("unchecked")
        List<Object> tagIdsRaw = body.get("tagIds") instanceof List ? (List<Object>) body.get("tagIds") : List.of();
        List<Long> tagIds = tagIdsRaw.stream().map(this::longVal).filter(Objects::nonNull).toList();
        String category = str(body.get("category"), "");
        Set<Long> tableIdsByTag = new HashSet<>();
        if (!tagIds.isEmpty()) {
            for (IngAssetTagBinding b : tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                    .in(IngAssetTagBinding::getTagId, tagIds)
                    .eq(IngAssetTagBinding::getAssetType, "TABLE")
                    .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"))) {
                tableIdsByTag.add(b.getAssetId());
            }
        }
        List<Map<String, Object>> hits = new ArrayList<>();
        List<IngDataTable> tables = tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>().orderByDesc(IngDataTable::getId).last("LIMIT 500"));
        for (IngDataTable t : tables) {
            if (!tagIds.isEmpty() && !tableIdsByTag.contains(t.getId())) continue;
            boolean kwOk = keyword.isBlank()
                    || containsIgnore(t.getTableName(), keyword)
                    || containsIgnore(t.getTableCode(), keyword)
                    || containsIgnore(t.getUsageDesc(), keyword)
                    || containsIgnore(t.getPhysicalTableName(), keyword);
            if (!kwOk) continue;
            if (!category.isBlank() && !containsIgnore(t.getUsageDesc(), category) && !containsIgnore(t.getTableName(), category)) {
                // soft filter on category keyword
            }
            List<IngDataColumn> cols = columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>().eq(IngDataColumn::getTableId, t.getId()).last("LIMIT 50"));
            if (body.get("dataItem") != null && !String.valueOf(body.get("dataItem")).isBlank()) {
                String di = String.valueOf(body.get("dataItem"));
                boolean colHit = cols.stream().anyMatch(c -> containsIgnore(c.getColumnName(), di) || containsIgnore(c.getColumnCode(), di)
                        || containsIgnore(c.getSemanticDesc(), di));
                if (!colHit) continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("docType", "TABLE");
            row.put("docKey", "TABLE:" + t.getId());
            row.put("assetId", t.getId());
            row.put("title", nullTo(t.getTableName(), t.getTableCode()));
            row.put("tableCode", t.getTableCode());
            row.put("physicalSchema", t.getSourceSchema());
            row.put("physicalTable", nullTo(t.getPhysicalTableName(), t.getSourceTable()));
            row.put("sourceId", t.getSourceId());
            row.put("tags", tableTagNames(t.getId()));
            row.put("columns", cols.stream().limit(12).map(c -> Map.of(
                    "id", c.getId(), "code", nullTo(c.getColumnCode(), ""), "name", nullTo(c.getColumnName(), ""),
                    "dataType", nullTo(c.getDataType(), ""))).toList());
            row.put("globalFields", globalFieldsForTable(t.getId()));
            row.put("score", 80);
            hits.add(row);
            if (hits.size() >= 50) break;
        }
        logQuery(principal, "SEARCH", "META", keyword, body, hits.size(), null);
        return Map.of("mode", "META", "query", keyword, "total", hits.size(), "hits", hits,
                "hint", "命中元数据后可「条件浏览」锁定物理表");
    }

    private Map<String, Object> comboSearch(UserPrincipal principal, Map<String, Object> body) {
        Long tableId = longVal(body.get("tableId"));
        String q = str(body.get("q"), "");
        @SuppressWarnings("unchecked")
        Map<String, Object> globalValues = body.get("globalValues") instanceof Map
                ? (Map<String, Object>) body.get("globalValues") : Map.of();
        List<Map<String, Object>> base;
        if (tableId != null) {
            Map<String, Object> metaBody = new LinkedHashMap<>(body);
            metaBody.put("mode", "META");
            Map<String, Object> meta = metaSearch(principal, metaBody);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hits = (List<Map<String, Object>>) meta.get("hits");
            base = hits.stream().filter(h -> Objects.equals(longVal(h.get("assetId")), tableId)).toList();
        } else {
            Map<String, Object> fuzzy = fuzzySearch(principal, body);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hits = (List<Map<String, Object>>) fuzzy.get("hits");
            base = hits;
        }
        // attach applicable global fields for first table hit
        List<Map<String, Object>> appliedGlobals = List.of();
        if (tableId != null) appliedGlobals = globalFieldsForTable(tableId);
        else if (!base.isEmpty() && "TABLE".equals(base.get(0).get("docType"))) {
            appliedGlobals = globalFieldsForTable(longVal(base.get(0).get("assetId")));
        }
        List<String> validation = new ArrayList<>();
        for (Map<String, Object> gf : appliedGlobals) {
            if (Objects.equals(gf.get("requiredFlag"), 1) || Objects.equals(gf.get("requiredFlag"), true)) {
                Object v = globalValues.get(String.valueOf(gf.get("fieldCode")));
                if (v == null || String.valueOf(v).isBlank()) {
                    validation.add("必填全局条件缺失: " + gf.get("fieldName"));
                }
            }
        }
        logQuery(principal, "SEARCH", "COMBO", q, body, base.size(), null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "COMBO");
        out.put("total", base.size());
        out.put("hits", base);
        out.put("appliedGlobalFields", appliedGlobals);
        out.put("globalValues", globalValues);
        out.put("validation", validation);
        out.put("valid", validation.isEmpty());
        return out;
    }

    @Transactional
    public Map<String, Object> browseTable(UserPrincipal principal, Map<String, Object> body) {
        Long tableId = longVal(body.get("tableId"));
        if (tableId == null) throw new BusinessException(400, "tableId required");
        IngDataTable table = tableMapper.selectById(tableId);
        if (table == null) throw new BusinessException(404, "数据表不存在");
        int limit = Math.min(intVal(body.get("limit"), 20), BROWSE_MAX);
        int offset = Math.max(intVal(body.get("offset"), 0), 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> globalValues = body.get("globalValues") instanceof Map
                ? (Map<String, Object>) body.get("globalValues") : Map.of();

        List<Map<String, Object>> gfs = globalFieldsForTable(tableId);
        String physical = nullTo(table.getPhysicalTableName(), table.getSourceTable());
        if (physical.isBlank()) {
            throw new BusinessException(400, "未登记物理表名，无法浏览");
        }
        if (!physical.matches("[A-Za-z0-9_$.]+")) {
            throw new BusinessException(400, "物理表名非法");
        }

        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map<String, Object> gf : gfs) {
            String fcode = String.valueOf(gf.get("fieldCode"));
            Object val = globalValues.get(fcode);
            if (val == null || String.valueOf(val).isBlank()) continue;
            String col = String.valueOf(gf.get("columnCode"));
            if (!col.matches("[A-Za-z0-9_]+")) continue;
            where.add("`" + col + "` = ?");
            params.add(String.valueOf(val));
        }
        String extra = str(body.get("keyword"), "");
        // keyword not pushed to SQL blindly

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableId", tableId);
        out.put("tableName", table.getTableName());
        out.put("physicalTable", physical);
        out.put("physicalSchema", table.getSourceSchema());
        out.put("globalFields", gfs);
        out.put("limit", limit);
        out.put("offset", offset);
        out.put("masked", true);
        out.put("mode", "LIVE");

        IngDataSource source = table.getSourceId() == null ? null : sourceMapper.selectById(table.getSourceId());
        if (source == null || source.getConnConfigJson() == null || source.getConnConfigJson().isBlank()) {
            out.put("mode", "LEDGER");
            out.put("message", "数据源连接未配置，返回字段元数据；配置 JDBC 后可条件浏览业务数据");
            List<IngDataColumn> cols = columnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>().eq(IngDataColumn::getTableId, tableId));
            out.put("columns", cols.stream().map(c -> Map.of(
                    "code", nullTo(c.getColumnCode(), ""), "name", nullTo(c.getColumnName(), ""),
                    "dataType", nullTo(c.getDataType(), ""))).toList());
            out.put("rows", List.of());
            out.put("rowCount", 0);
            logQuery(principal, "BROWSE", "META", physical, body, 0, "TABLE:" + tableId);
            audit(principal, "BROWSE", "TABLE:" + tableId, "{\"mode\":\"LEDGER\"}");
            return out;
        }

        try {
            JdbcProbeService.ConnConfig cfg = jdbcProbeService.parse(source.getSourceType(), source.getConnConfigJson());
            String sql = "SELECT * FROM `" + physical.replace("`", "") + "`"
                    + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                    + " LIMIT " + limit + " OFFSET " + offset;
            List<String> columns = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(jdbcProbeService.jdbcUrl(cfg), cfg.username, cfg.password);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cc = meta.getColumnCount();
                    for (int i = 1; i <= cc; i++) columns.add(meta.getColumnLabel(i));
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (String col : columns) {
                            Object v = rs.getObject(col);
                            row.put(col, maskCell(col, v == null ? null : String.valueOf(v)));
                        }
                        rows.add(row);
                    }
                }
            }
            out.put("columns", columns);
            out.put("rows", rows);
            out.put("rowCount", rows.size());
            out.put("sqlPreview", sql.replaceAll("\\s+", " "));
            logQuery(principal, "BROWSE", "COMBO", physical, body, rows.size(), "TABLE:" + tableId);
            audit(principal, "BROWSE", "TABLE:" + tableId, "{\"rows\":" + rows.size() + ",\"limit\":" + limit + "}");
            return out;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("browse failed tableId={}: {}", tableId, e.getMessage());
            throw new BusinessException(502, "浏览失败: " + e.getMessage());
        }
    }

    public Map<String, Object> downloadRequest(UserPrincipal principal, Map<String, Object> body) {
        // 合规下载：仅登记申请台账，实际导出走权限/审批/脱敏
        audit(principal, "DOWNLOAD", str(body.get("docKey"), str(body.get("tableId"), "")), toJson(body));
        logQuery(principal, "DOWNLOAD", str(body.get("mode"), "COMBO"), str(body.get("q"), ""), body, 0, str(body.get("docKey"), null));
        return Map.of("status", "SUBMITTED", "message", "下载申请已登记，需权限与脱敏策略审批后异步导出",
                "needApproval", true, "maskRequired", true);
    }

    @Transactional
    public void click(UserPrincipal principal, Map<String, Object> body) {
        String docKey = str(body.get("docKey"), "");
        if (docKey.isBlank()) return;
        IngSearchDocument d = docMapper.selectOne(new LambdaQueryWrapper<IngSearchDocument>().eq(IngSearchDocument::getDocKey, docKey).last("LIMIT 1"));
        if (d != null) {
            d.setHotScore((d.getHotScore() == null ? 0 : d.getHotScore()) + 1);
            docMapper.updateById(d);
        }
        logQuery(principal, "CLICK", str(body.get("mode"), "FUZZY"), str(body.get("q"), ""), body, null, docKey);
    }

    // ---------- identity ----------

    public List<IngSearchIdentity> listIdentities(String keyword) {
        LambdaQueryWrapper<IngSearchIdentity> q = new LambdaQueryWrapper<IngSearchIdentity>()
                .eq(IngSearchIdentity::getStatus, "ACTIVE").orderByDesc(IngSearchIdentity::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(IngSearchIdentity::getIdValue, keyword)
                    .or().like(IngSearchIdentity::getDisplayName, keyword)
                    .or().like(IngSearchIdentity::getEntityId, keyword));
        }
        return identityMapper.selectList(q.last("LIMIT 100"));
    }

    @Transactional
    public Long saveIdentity(UserPrincipal principal, Map<String, Object> body) {
        String entityId = str(body.get("entityId"), "");
        String idType = str(body.get("idType"), "");
        String idValue = str(body.get("idValue"), "");
        if (entityId.isBlank() || idType.isBlank() || idValue.isBlank()) {
            throw new BusinessException(400, "entityId/idType/idValue 不能为空");
        }
        Long id = longVal(body.get("id"));
        IngSearchIdentity row = id == null ? new IngSearchIdentity() : identityMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "标识不存在");
        if (id == null) {
            row.setCreateTime(LocalDateTime.now());
        }
        row.setEntityId(entityId);
        row.setIdType(idType);
        row.setIdValue(idValue);
        row.setSourceSystem(str(body.get("sourceSystem"), row.getSourceSystem()));
        row.setDisplayName(str(body.get("displayName"), row.getDisplayName()));
        row.setProfileJson(str(body.get("profileJson"), row.getProfileJson() == null ? "{}" : row.getProfileJson()));
        row.setRelatedDocs(str(body.get("relatedDocs"), row.getRelatedDocs()));
        row.setStatus(str(body.get("status"), "ACTIVE"));
        if (id == null) identityMapper.insert(row);
        else identityMapper.updateById(row);
        audit(principal, "IDENTITY", entityId, toJson(row));
        return row.getId();
    }

    // ---------- saved query ----------

    public List<IngSearchSavedQuery> listSavedQueries() {
        return savedQueryMapper.selectList(new LambdaQueryWrapper<IngSearchSavedQuery>()
                .eq(IngSearchSavedQuery::getStatus, "ACTIVE").orderByDesc(IngSearchSavedQuery::getId));
    }

    @Transactional
    public Long saveQuery(UserPrincipal principal, Map<String, Object> body) {
        String code = str(body.get("queryCode"), "Q_" + System.currentTimeMillis());
        String name = str(body.get("queryName"), "");
        String payload = str(body.get("payloadJson"), "{}");
        if (name.isBlank()) throw new BusinessException(400, "方案名称不能为空");
        validateJson(payload);
        IngSearchSavedQuery row = new IngSearchSavedQuery();
        row.setQueryCode(code);
        row.setQueryName(name);
        row.setMode(str(body.get("mode"), "COMBO"));
        row.setPayloadJson(payload);
        row.setOwnerName(principal == null ? null : principal.getUsername());
        row.setStatus("ACTIVE");
        row.setCreateTime(LocalDateTime.now());
        savedQueryMapper.insert(row);
        return row.getId();
    }

    public List<IngSearchQueryLog> queryLogs(Integer limit) {
        int lim = limit == null ? 50 : Math.min(limit, 200);
        return queryLogMapper.selectList(new LambdaQueryWrapper<IngSearchQueryLog>()
                .orderByDesc(IngSearchQueryLog::getCreatedAt).last("LIMIT " + lim));
    }

    public List<IngSearchAuditLog> auditLogs(Integer limit) {
        int lim = limit == null ? 50 : Math.min(limit, 200);
        return auditMapper.selectList(new LambdaQueryWrapper<IngSearchAuditLog>()
                .orderByDesc(IngSearchAuditLog::getCreatedAt).last("LIMIT " + lim));
    }

    // ---------- helpers ----------

    private Map<String, Object> toHit(IngSearchDocument d, double score, Set<String> tokens) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("docKey", d.getDocKey());
        row.put("docType", d.getDocType());
        row.put("assetId", d.getAssetId());
        row.put("assetCode", d.getAssetCode());
        row.put("title", d.getTitle());
        row.put("summary", d.getSummary());
        row.put("score", Math.round(score * 10) / 10.0);
        row.put("hotScore", d.getHotScore());
        row.put("physicalSchema", d.getPhysicalSchema());
        row.put("physicalTable", d.getPhysicalTable());
        row.put("sourceId", d.getSourceId());
        row.put("tags", parseJsonList(d.getTagsJson()));
        row.put("highAttrs", orderAttrs(parseJson(d.getHighAttrsJson()), true));
        row.put("midAttrs", orderAttrs(parseJson(d.getMidAttrsJson()), false));
        row.put("highlight", highlight(d.getTitle() + " " + nullTo(d.getSummary(), ""), tokens));
        return row;
    }

    private Map<String, Object> orderAttrs(Map<String, Object> attrs, boolean high) {
        if (attrs == null || attrs.isEmpty()) return Map.of();
        Map<String, Object> cfg = weightConfig();
        @SuppressWarnings("unchecked")
        List<String> order = (List<String>) cfg.get(high ? "high" : "mid");
        Map<String, Object> out = new LinkedHashMap<>();
        if (order != null) {
            for (String k : order) if (attrs.containsKey(k)) out.put(k, attrs.get(k));
        }
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
            if (!out.containsKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    private Map<String, Object> weightConfig() {
        IngSearchKnowledge k = knowledgeMapper.selectOne(new LambdaQueryWrapper<IngSearchKnowledge>()
                .eq(IngSearchKnowledge::getKnowledgeCode, "WEIGHT_DEFAULT")
                .eq(IngSearchKnowledge::getStatus, "ACTIVE").last("LIMIT 1"));
        if (k == null) return Map.of("high", List.of("tableName", "tagNames"), "mid", List.of("tableCode"));
        return parseJson(k.getPayloadJson());
    }

    private Map<String, Object> uiConfig() {
        IngSearchKnowledge k = knowledgeMapper.selectOne(new LambdaQueryWrapper<IngSearchKnowledge>()
                .eq(IngSearchKnowledge::getKnowledgeCode, "UI_PORTAL")
                .eq(IngSearchKnowledge::getStatus, "ACTIVE").last("LIMIT 1"));
        return k == null ? Map.of() : parseJson(k.getPayloadJson());
    }

    private Set<String> expandQueryTokens(String q) {
        Set<String> tokens = new LinkedHashSet<>();
        if (q == null || q.isBlank()) return tokens;
        for (String p : q.trim().split("[\\s,;，；]+")) {
            if (!p.isBlank()) tokens.add(p.toLowerCase(Locale.ROOT));
        }
        for (IngSearchKnowledge syn : knowledgeMapper.selectList(new LambdaQueryWrapper<IngSearchKnowledge>()
                .eq(IngSearchKnowledge::getKnowledgeType, "SYNONYM").eq(IngSearchKnowledge::getStatus, "ACTIVE"))) {
            Map<String, Object> payload = parseJson(syn.getPayloadJson());
            Object termsObj = payload.get("terms");
            if (!(termsObj instanceof List<?> terms)) continue;
            List<String> list = terms.stream().map(String::valueOf).map(s -> s.toLowerCase(Locale.ROOT)).toList();
            boolean hit = list.stream().anyMatch(tokens::contains) || list.stream().anyMatch(t -> q.toLowerCase(Locale.ROOT).contains(t));
            if (hit) tokens.addAll(list);
        }
        return tokens;
    }

    private String expandSynonyms(String text) {
        if (text == null || text.isBlank()) return "";
        Set<String> extra = expandQueryTokens(text);
        return text + " " + String.join(" ", extra);
    }

    private double scoreDoc(IngSearchDocument d, Set<String> tokens, String rawQ) {
        if (tokens.isEmpty()) return 0;
        String hay = (nullTo(d.getTitle(), "") + " " + nullTo(d.getSummary(), "") + " " + nullTo(d.getContentText(), "")
                + " " + nullTo(d.getTagsJson(), "")).toLowerCase(Locale.ROOT);
        double score = 0;
        for (String t : tokens) {
            if (t.length() < 1) continue;
            if (nullTo(d.getTitle(), "").toLowerCase(Locale.ROOT).contains(t)) score += 40;
            else if (hay.contains(t)) score += 15;
        }
        if (rawQ != null && !rawQ.isBlank() && nullTo(d.getTitle(), "").equalsIgnoreCase(rawQ.trim())) score += 50;
        if (d.getWeightScore() != null) score += d.getWeightScore().doubleValue() * 0.2;
        if (d.getHotScore() != null) score += Math.min(d.getHotScore(), 20);
        return score;
    }

    private Map<String, Object> buildSecondary(String q, List<Map<String, Object>> hits) {
        Map<String, Object> sec = new LinkedHashMap<>();
        if (hits.isEmpty() && q != null && !q.isBlank()) {
            sec.put("action", "EXPAND");
            sec.put("suggestions", expandQueryTokens(q));
            sec.put("hint", "可尝试同义词扩词或切换元数据找表");
        } else if (!hits.isEmpty()) {
            Set<String> types = hits.stream().map(h -> String.valueOf(h.get("docType"))).collect(Collectors.toCollection(LinkedHashSet::new));
            sec.put("action", "FACET");
            sec.put("suggestFacets", types);
            sec.put("hint", "可点击结果或按类型收窄");
        }
        return sec;
    }

    private String detectIdType(String v) {
        if (v == null) return "CUSTOM";
        if (v.matches("\\d{17}[\\dXx]")) return "ID_CARD";
        if (v.matches("[0-9A-Z]{18}")) return "CREDIT_CODE";
        if (v.matches("1\\d{10}")) return "PHONE";
        return "CUSTOM";
    }

    private List<String> tableTagNames(Long tableId) {
        List<IngAssetTagBinding> binds = tagBindingMapper.selectList(new LambdaQueryWrapper<IngAssetTagBinding>()
                .eq(IngAssetTagBinding::getAssetType, "TABLE")
                .eq(IngAssetTagBinding::getAssetId, tableId)
                .ne(IngAssetTagBinding::getConfirmStatus, "REJECTED"));
        List<String> names = new ArrayList<>();
        for (IngAssetTagBinding b : binds) {
            IngAssetTag t = tagMapper.selectById(b.getTagId());
            if (t != null) names.add(t.getTagName());
        }
        return names;
    }

    private String maskCell(String col, String val) {
        if (val == null) return null;
        String c = col == null ? "" : col.toLowerCase(Locale.ROOT);
        if (c.contains("sfzh") || c.contains("id_card") || c.contains("idcard")) {
            if (val.length() >= 8) return val.substring(0, 4) + "**********" + val.substring(val.length() - 4);
        }
        if (c.contains("phone") || c.contains("mobile") || c.contains("tel")) {
            if (val.length() >= 7) return val.substring(0, 3) + "****" + val.substring(val.length() - 4);
        }
        return val;
    }

    private void logQuery(UserPrincipal p, String action, String mode, String q, Object payload, Integer hits, String docKey) {
        IngSearchQueryLog row = new IngSearchQueryLog();
        row.setActionType(action);
        row.setMode(mode);
        row.setQueryText(truncate(q, 500));
        row.setPayloadJson(truncate(toJson(payload), 2000));
        row.setHitCount(hits);
        row.setClickedDocKey(docKey);
        if (p != null) {
            row.setOperatorId(p.getUserId());
            row.setOperatorName(p.getDisplayName() != null ? p.getDisplayName() : p.getUsername());
        }
        row.setCreatedAt(LocalDateTime.now());
        queryLogMapper.insert(row);
    }

    private void audit(UserPrincipal p, String action, String target, String detail) {
        IngSearchAuditLog row = new IngSearchAuditLog();
        row.setActionType(action);
        row.setTargetRef(target);
        row.setDetailJson(truncate(detail, 2000));
        if (p != null) {
            row.setOperatorId(p.getUserId());
            row.setOperatorName(p.getDisplayName() != null ? p.getDisplayName() : p.getUsername());
        }
        row.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(row);
    }

    private String highlight(String text, Set<String> tokens) {
        String t = nullTo(text, "");
        for (String tok : tokens) {
            if (tok.length() < 2) continue;
            t = t.replaceAll("(?i)(" + Pattern.quote(tok) + ")", "【$1】");
        }
        return truncate(t, 200);
    }

    private Map<String, Object> parseJson(String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> parseJsonList(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            if (json.trim().startsWith("[")) {
                return OM.readValue(json, new TypeReference<List<String>>() {});
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private void validateJson(String json) {
        try {
            OM.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(400, "JSON 非法: " + e.getMessage());
        }
    }

    private String toJson(Object o) {
        try { return OM.writeValueAsString(o); } catch (Exception e) { return String.valueOf(o); }
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    private static List<String> splitCsv(String s) {
        if (s == null || s.isBlank()) return List.of();
        return List.of(s.split("[,;，；\\s]+")).stream().filter(x -> !x.isBlank()).toList();
    }

    private static boolean containsIgnore(String hay, String needle) {
        if (hay == null || needle == null || needle.isBlank()) return false;
        return hay.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String nullTo(String s, String def) { return s == null || s.isBlank() ? def : s; }
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? def : s;
    }
    private Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v))) return null;
        return Long.valueOf(String.valueOf(v));
    }
    private static int intVal(Object v, int def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        return Integer.parseInt(String.valueOf(v));
    }
}
