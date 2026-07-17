package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.BizDataAsset;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionPlatformService {

    private final IngStatsMetricMapper statsMapper;
    private final IngGuideStepMapper guideMapper;
    private final IngProjectMapper projectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestionPlatformService(IngStatsMetricMapper statsMapper, IngGuideStepMapper guideMapper,
                                    IngProjectMapper projectMapper, IngDataSourceMapper dataSourceMapper,
                                    IngDataTableMapper dataTableMapper, IngDataColumnMapper dataColumnMapper,
                                    IngDictMapper dictMapper, IngUploadRecordMapper uploadMapper,
                                    IngIngestChannelMapper channelMapper, IngPipelineJobMapper pipelineMapper,
                                    IngResourceRegistryMapper registryMapper, IngGovernPolicyMapper policyMapper,
                                    IngHealthMetricMapper healthMapper, BizDataAssetMapper assetMapper,
                                    AuditService auditService, StorageIntegrationClient storageClient,
                                    CredentialCipher credentialCipher, JdbcProbeService jdbcProbeService) {
        this.statsMapper = statsMapper;
        this.guideMapper = guideMapper;
        this.projectMapper = projectMapper;
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

    public Map<String, Object> registerOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projects", projectMapper.selectCount(null));
        out.put("dataSources", dataSourceMapper.selectCount(null));
        out.put("dicts", dictMapper.selectCount(null));
        out.put("assets", assetMapper.selectCount(null));
        out.put("lineageGraph", Map.of("nodes", List.of(), "edges", List.of()));
        out.put("systemLinks", List.of(
                Map.of("mCode", "M048", "route", "/system/orgs", "label", "访问控制"),
                Map.of("mCode", "M049", "route", "/system/security", "label", "等保开关")
        ));
        return out;
    }

    public List<IngProject> listProjects() {
        return projectMapper.selectList(new LambdaQueryWrapper<IngProject>().orderByDesc(IngProject::getId));
    }

    @Transactional
    public Long createProject(UserPrincipal operator, Map<String, Object> body) {
        IngProject p = new IngProject();
        p.setProjectCode(str(body.get("projectCode"), "PRJ_" + UUID.randomUUID().toString().substring(0, 8)));
        p.setProjectName(required(body.get("projectName"), "projectName").toString());
        Object orgId = body.get("boundOrgId");
        p.setBoundOrgId(orgId == null ? operator.getOrgId() : Long.valueOf(String.valueOf(orgId)));
        p.setSystemName(str(body.get("systemName"), "业务系统"));
        p.setStatus("ACTIVE");
        p.setCreatedBy(operator.getUsername());
        projectMapper.insert(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_PROJECT_CREATE", "ing_project", String.valueOf(p.getId()), p.getProjectName());
        return p.getId();
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
        List<IngDataSource> sources = dataSourceMapper.selectList(
                new LambdaQueryWrapper<IngDataSource>().eq(IngDataSource::getProjectId, id));
        for (IngDataSource ds : sources) {
            List<IngDataTable> tables = dataTableMapper.selectList(
                    new LambdaQueryWrapper<IngDataTable>().eq(IngDataTable::getSourceId, ds.getId()));
            for (IngDataTable t : tables) {
                dataColumnMapper.delete(new LambdaQueryWrapper<IngDataColumn>().eq(IngDataColumn::getTableId, t.getId()));
            }
            dataTableMapper.delete(new LambdaQueryWrapper<IngDataTable>().eq(IngDataTable::getSourceId, ds.getId()));
        }
        dataSourceMapper.delete(new LambdaQueryWrapper<IngDataSource>().eq(IngDataSource::getProjectId, id));
        projectMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ING_PROJECT_DELETE", "ing_project", String.valueOf(id), p.getProjectName());
    }

    public List<IngDataSource> listDataSources(Long projectId) {
        LambdaQueryWrapper<IngDataSource> q = new LambdaQueryWrapper<IngDataSource>().orderByDesc(IngDataSource::getId);
        if (projectId != null) {
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
                c.setColumnCode(String.valueOf(col.get("columnName")));
                c.setColumnName(String.valueOf(col.get("columnName")));
                c.setDataType(String.valueOf(col.get("dataType")));
                c.setNullableFlag(Boolean.TRUE.equals(col.get("nullable")) ? 1 : 0);
                c.setSortOrder(intVal(col.get("sortOrder"), 0));
                Object size = col.get("columnSize");
                c.setLengthVal(size == null ? null : Integer.parseInt(String.valueOf(size)));
                c.setSemanticDesc(str(col.get("remarks"), null));
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
        IngDataSource ds = new IngDataSource();
        ds.setProjectId(Long.valueOf(String.valueOf(required(body.get("projectId"), "projectId"))));
        ds.setSourceCode(str(body.get("sourceCode"), "DS_" + System.currentTimeMillis()));
        ds.setSourceName(required(body.get("sourceName"), "sourceName").toString());
        ds.setSourceType(str(body.get("sourceType"), "MYSQL"));
        ds.setConnConfigJson(buildConnConfigJson(body, null));
        ds.setConnStatus("UNTESTED");
        ds.setTableCount(0);
        ds.setSyncStatus("PENDING");
        dataSourceMapper.insert(ds);
        return ds.getId();
    }

    @Transactional
    public void updateDataSource(UserPrincipal operator, Long id, Map<String, Object> body) {
        IngDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) throw new BusinessException(404, "数据源不存在");
        if (body.containsKey("sourceName")) ds.setSourceName(body.get("sourceName").toString());
        if (body.containsKey("sourceType")) ds.setSourceType(body.get("sourceType").toString());
        ds.setConnConfigJson(buildConnConfigJson(body, ds));
        ds.setConnStatus("UNTESTED");
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
        d.setDictCode(str(body.get("dictCode"), "DICT_" + System.currentTimeMillis()));
        d.setDictName(required(body.get("dictName"), "dictName").toString());
        d.setDictType(str(body.get("dictType"), "STANDARD"));
        d.setItemCount(intVal(body.get("itemCount"), 0));
        d.setStatus("ACTIVE");
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
    public Map<String, Object> runChannel(UserPrincipal operator, Long id) {
        IngIngestChannel ch = channelMapper.selectById(id);
        if (ch == null) {
            throw new BusinessException(404, "接入通道不存在");
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
        job.setResultJson(buildPipelineResult(jobType));
        if ("READ".equals(jobType)) {
            job.setBillAmount(BigDecimal.valueOf(128.50).setScale(2, RoundingMode.HALF_UP));
        }
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

    private String buildPipelineResult(String jobType) {
        return switch (jobType) {
            case "PROBE" -> "{\"nullRate\":0.02,\"domain\":\"ok\",\"entity\":\"person\"}";
            case "DEFINE" -> "{\"metadata\":8,\"lineage\":\"linked\",\"quality\":\"L2\"}";
            case "READ" -> "{\"channels\":3,\"bytes\":1048576,\"bill\":\"generated\"}";
            case "RECONCILE" -> "{\"matched\":98.6,\"diff\":12,\"alert\":false}";
            default -> "{}";
        };
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
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
}
