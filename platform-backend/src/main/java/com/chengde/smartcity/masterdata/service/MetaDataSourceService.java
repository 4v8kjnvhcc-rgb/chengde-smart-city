package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.jdbc.CredentialCipher;
import com.chengde.smartcity.integration.jdbc.JdbcProbeService;
import com.chengde.smartcity.exchange.entity.IngBizSystem;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.mapper.IngBizSystemMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.exchange.service.IngestionPlatformService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengde.smartcity.masterdata.entity.GovMetaDataSource;
import com.chengde.smartcity.masterdata.entity.GovMetaSourceCategory;
import com.chengde.smartcity.masterdata.entity.GovOmConnector;
import com.chengde.smartcity.masterdata.mapper.GovMetaDataSourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaSourceCategoryMapper;
import com.chengde.smartcity.masterdata.mapper.GovOmConnectorMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetaDataSourceService {

    private static final Logger log = LoggerFactory.getLogger(MetaDataSourceService.class);

    private static final ObjectMapper OM = new ObjectMapper();

    private final GovMetaDataSourceMapper dataSourceMapper;
    private final GovMetaSourceCategoryMapper categoryMapper;
    private final GovOmConnectorMapper connectorMapper;
    private final MetaSourceCategoryService categoryService;
    private final JdbcProbeService jdbcProbeService;
    private final CredentialCipher credentialCipher;
    private final AuditService auditService;
    private final IngestionPlatformService ingestionPlatformService;
    private final IngDataSourceMapper ingDataSourceMapper;
    private final IngProjectMapper ingProjectMapper;
    private final IngBizSystemMapper ingBizSystemMapper;
    private final SysOrgMapper orgMapper;

    public MetaDataSourceService(GovMetaDataSourceMapper dataSourceMapper,
                                 GovMetaSourceCategoryMapper categoryMapper,
                                 GovOmConnectorMapper connectorMapper,
                                 MetaSourceCategoryService categoryService,
                                 JdbcProbeService jdbcProbeService,
                                 CredentialCipher credentialCipher,
                                 AuditService auditService,
                                 IngestionPlatformService ingestionPlatformService,
                                 IngDataSourceMapper ingDataSourceMapper,
                                 IngProjectMapper ingProjectMapper,
                                 IngBizSystemMapper ingBizSystemMapper,
                                 SysOrgMapper orgMapper) {
        this.dataSourceMapper = dataSourceMapper;
        this.categoryMapper = categoryMapper;
        this.connectorMapper = connectorMapper;
        this.categoryService = categoryService;
        this.jdbcProbeService = jdbcProbeService;
        this.credentialCipher = credentialCipher;
        this.auditService = auditService;
        this.ingestionPlatformService = ingestionPlatformService;
        this.ingDataSourceMapper = ingDataSourceMapper;
        this.ingProjectMapper = ingProjectMapper;
        this.ingBizSystemMapper = ingBizSystemMapper;
        this.orgMapper = orgMapper;
    }

    public List<Map<String, Object>> list(Long categoryId, String keyword) {
        LambdaQueryWrapper<GovMetaDataSource> q = new LambdaQueryWrapper<GovMetaDataSource>()
                .eq(GovMetaDataSource::getStatus, "ACTIVE")
                .orderByAsc(GovMetaDataSource::getSortOrder)
                .orderByDesc(GovMetaDataSource::getId);
        if (categoryId != null && categoryId > 0) {
            List<Long> scope = categoryService.collectCategoryScope(categoryId);
            if (!scope.isEmpty()) {
                q.in(GovMetaDataSource::getCategoryId, scope);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(GovMetaDataSource::getSourceName, keyword.trim());
        }
        List<GovMetaDataSource> rows = dataSourceMapper.selectList(q);
        Map<Long, String> categoryNames = categoryMapper.selectList(new LambdaQueryWrapper<GovMetaSourceCategory>()
                        .eq(GovMetaSourceCategory::getStatus, "ACTIVE"))
                .stream()
                .collect(Collectors.toMap(GovMetaSourceCategory::getId, GovMetaSourceCategory::getCategoryName, (a, b) -> a));
        Map<Long, String> orgNames = loadOrgNames(rows);
        Map<Long, String> ingDeptNames = loadDeptNamesFromIngSources(rows);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetaDataSource row : rows) {
            out.add(toView(row, categoryNames, orgNames, ingDeptNames));
        }
        return out;
    }

    public Map<String, Object> getDetail(Long id) {
        GovMetaDataSource row = require(id);
        Map<Long, String> categoryNames = categoryMapper.selectList(new LambdaQueryWrapper<GovMetaSourceCategory>()
                        .eq(GovMetaSourceCategory::getStatus, "ACTIVE"))
                .stream()
                .collect(Collectors.toMap(GovMetaSourceCategory::getId, GovMetaSourceCategory::getCategoryName, (a, b) -> a));
        Map<Long, String> orgNames = loadOrgNames(List.of(row));
        Map<Long, String> ingDeptNames = loadDeptNamesFromIngSources(List.of(row));
        Map<String, Object> view = toView(row, categoryNames, orgNames, ingDeptNames);
        view.put("password", "");
        return view;
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        GovMetaDataSource row = mapFromBody(body, null);
        row.setSourceCode(generateCode(row.getSourceName()));
        row.setStatus("ACTIVE");
        row.setCreatedBy(operator == null ? "system" : operator.getUsername());
        if (row.getReadOnlyFlag() == null) {
            row.setReadOnlyFlag(0);
        }
        if (body.get("password") != null && !String.valueOf(body.get("password")).isBlank()) {
            row.setPasswordCipher(credentialCipher.encrypt(String.valueOf(body.get("password"))));
        }
        categoryService.requireCategory(row.getCategoryId());
        if (row.getTagCategoryId() != null) {
            categoryService.requireCategory(row.getTagCategoryId());
        }
        dataSourceMapper.insert(row);
        syncConnector(row);
        audit(operator, "META_DATA_SOURCE_CREATE", row);
        return row.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovMetaDataSource row = require(id);
        applyBody(row, body);
        if (body.containsKey("password") && !String.valueOf(body.get("password")).isBlank()) {
            row.setPasswordCipher(credentialCipher.encrypt(String.valueOf(body.get("password"))));
        }
        if (row.getTagCategoryId() != null) {
            categoryService.requireCategory(row.getTagCategoryId());
        }
        categoryService.requireCategory(row.getCategoryId());
        dataSourceMapper.updateById(row);
        syncConnector(row);
        audit(operator, "META_DATA_SOURCE_UPDATE", row);
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovMetaDataSource row = require(id);
        row.setStatus("DELETED");
        dataSourceMapper.updateById(row);
        audit(operator, "META_DATA_SOURCE_DELETE", row);
    }

    @Transactional
    public void move(UserPrincipal operator, Long id, Long categoryId) {
        GovMetaDataSource row = require(id);
        categoryService.requireCategory(categoryId);
        row.setCategoryId(categoryId);
        dataSourceMapper.updateById(row);
        audit(operator, "META_DATA_SOURCE_MOVE", row);
    }

    public Map<String, Object> testConnection(Map<String, Object> body) {
        JdbcProbeService.ConnConfig cfg = toConnConfig(body, true);
        Map<String, Object> result = jdbcProbeService.testConnection(cfg);
        result.put("ok", true);
        result.put("message", "连接成功");
        return result;
    }

    /** 来源分类采集：归集登记项目/系统/数据源候选列表 */
    public List<Map<String, Object>> listCollectCandidates(UserPrincipal operator) {
        List<IngProject> projects = ingestionPlatformService.listProjects(operator);
        List<Long> collectedIds = dataSourceMapper.selectList(new LambdaQueryWrapper<GovMetaDataSource>()
                        .isNotNull(GovMetaDataSource::getIngSourceId)
                        .ne(GovMetaDataSource::getStatus, "DELETED"))
                .stream()
                .map(GovMetaDataSource::getIngSourceId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (IngProject project : projects) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("id", project.getId());
            pm.put("projectName", project.getProjectName());
            pm.put("deptName", project.getBoundOrgName());
            pm.put("boundOrgId", project.getBoundOrgId());
            pm.put("registerStatus", project.getRegisterStatus());
            List<Map<String, Object>> systems = new ArrayList<>();
            for (IngBizSystem system : ingestionPlatformService.listBizSystems(operator, project.getId())) {
                Map<String, Object> sm = new HashMap<>();
                sm.put("id", system.getId());
                sm.put("systemName", system.getSystemName());
                sm.put("dataSourceCount", system.getDataSourceCount());
                List<Map<String, Object>> sources = new ArrayList<>();
                for (IngDataSource ds : ingestionPlatformService.listDataSources(operator, null, system.getId())) {
                    Map<String, Object> dm = new HashMap<>();
                    dm.put("id", ds.getId());
                    dm.put("sourceCode", ds.getSourceCode());
                    dm.put("sourceName", ds.getSourceName());
                    dm.put("sourceType", ds.getSourceType());
                    dm.put("systemName", ds.getSystemName());
                    dm.put("projectName", project.getProjectName());
                    dm.put("tableCount", ds.getTableCount());
                    dm.put("sourceSchema", ds.getSourceSchema());
                    dm.put("collected", collectedIds.contains(ds.getId()));
                    IngDataSource raw = ingDataSourceMapper.selectById(ds.getId());
                    fillCollectConnView(dm, raw);
                    sources.add(dm);
                }
                sm.put("dataSources", sources);
                systems.add(sm);
            }
            pm.put("systems", systems);
            out.add(pm);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> collectFromIngestion(UserPrincipal operator, Map<String, Object> body) {
        Long categoryId = Long.valueOf(String.valueOf(required(body.get("categoryId"), "所属分类")));
        if (!categoryService.isSourceCategory(categoryId)) {
            throw new BusinessException(400, "仅「来源」分类支持采集，其它分类请手动新增");
        }
        List<Long> ingSourceIds = parseLongList(body.get("ingSourceIds"));
        if (ingSourceIds.isEmpty()) {
            throw new BusinessException(400, "请至少选择一个数据源");
        }
        int created = 0;
        int skipped = 0;
        for (Long ingSourceId : ingSourceIds) {
            if (importIngSource(operator, categoryId, ingSourceId)) {
                created++;
            } else {
                skipped++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        return result;
    }

    private boolean importIngSource(UserPrincipal operator, Long categoryId, Long ingSourceId) {
        GovMetaDataSource exists = dataSourceMapper.selectOne(new LambdaQueryWrapper<GovMetaDataSource>()
                .eq(GovMetaDataSource::getIngSourceId, ingSourceId)
                .ne(GovMetaDataSource::getStatus, "DELETED")
                .last("LIMIT 1"));
        if (exists != null) {
            return false;
        }
        IngDataSource ing = ingDataSourceMapper.selectById(ingSourceId);
        if (ing == null) {
            throw new BusinessException(404, "归集数据源不存在: " + ingSourceId);
        }
        IngProject project = resolveIngProject(ing);
        GovMetaDataSource row = new GovMetaDataSource();
        row.setIngSourceId(ing.getId());
        row.setSourceCode("ING_" + (ing.getSourceCode() == null ? String.valueOf(ing.getId()) : ing.getSourceCode()));
        row.setSourceName(ing.getSourceName());
        row.setSortOrder(1);
        row.setCategoryId(categoryId);
        row.setBelongSystem(ing.getSystemName());
        row.setRemarks("采集自归集登记");
        row.setAdapterType(normalizeAdapterType(ing.getSourceType()));
        row.setRealtimeFlag(0);
        row.setReadOnlyFlag(0);
        row.setStatus("ACTIVE");
        row.setCreatedBy(operator == null ? "system" : operator.getUsername());
        if (project != null) {
            row.setOrgId(project.getBoundOrgId());
            String orgName = resolveOrgName(project.getBoundOrgId(), project.getBoundOrgName());
            row.setDeptName(orgName);
            row.setOrgName(orgName);
        }
        if (ing.getConnConfigJson() != null && !ing.getConnConfigJson().isBlank()) {
            try {
                JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(ing.getSourceType(), ing.getConnConfigJson());
                row.setDbHost(conn.host);
                row.setDbPort(conn.port);
                row.setDbName(conn.database);
                row.setUsername(conn.username);
                String cipher = extractPasswordCipher(ing.getConnConfigJson());
                if (cipher != null) {
                    row.setPasswordCipher(cipher);
                }
            } catch (BusinessException ex) {
                log.warn("Collect ing source {} skipped conn parse: {}", ingSourceId, ex.getMessage());
            }
        }
        row.setDbSchema(ing.getSourceSchema());
        dataSourceMapper.insert(row);
        syncConnector(row);
        audit(operator, "META_DATA_SOURCE_COLLECT", row);
        return true;
    }

    private IngProject resolveIngProject(IngDataSource ing) {
        if (ing.getProjectId() != null) {
            IngProject project = ingProjectMapper.selectById(ing.getProjectId());
            if (project != null) {
                return project;
            }
        }
        if (ing.getSystemId() == null) {
            return null;
        }
        IngBizSystem system = ingBizSystemMapper.selectById(ing.getSystemId());
        if (system == null || system.getProjectId() == null) {
            return null;
        }
        return ingProjectMapper.selectById(system.getProjectId());
    }

    private void fillCollectConnView(Map<String, Object> dm, IngDataSource raw) {
        if (raw == null || raw.getConnConfigJson() == null || raw.getConnConfigJson().isBlank()) {
            return;
        }
        String type = raw.getSourceType() == null ? "MYSQL" : raw.getSourceType().trim().toUpperCase(Locale.ROOT);
        if ("FILE".equals(type) || "API".equals(type)) {
            try {
                Map<?, ?> cfg = OM.readValue(raw.getConnConfigJson(), Map.class);
                if (cfg.get("odsDb") != null) {
                    dm.put("dbName", String.valueOf(cfg.get("odsDb")));
                }
            } catch (Exception e) {
                log.warn("Collect FILE/API conn parse failed for ing source {}: {}", raw.getId(), e.getMessage());
            }
            return;
        }
        try {
            JdbcProbeService.ConnConfig conn = jdbcProbeService.parse(type, raw.getConnConfigJson());
            if (conn.host != null) {
                dm.put("dbHost", conn.host);
            }
            dm.put("dbPort", conn.port);
            if (conn.database != null && !conn.database.isBlank()) {
                dm.put("dbName", conn.database);
            }
        } catch (Exception e) {
            log.warn("Collect JDBC conn parse failed for ing source {}, fallback json: {}", raw.getId(), e.getMessage());
            dm.putAll(summarizeConnBrief(raw.getConnConfigJson()));
        }
    }

    private Map<String, Object> summarizeConnBrief(String connConfigJson) {
        Map<String, Object> m = new HashMap<>();
        if (connConfigJson == null || connConfigJson.isBlank()) {
            return m;
        }
        try {
            Map<?, ?> cfg = OM.readValue(connConfigJson, Map.class);
            putIfPresent(m, "dbHost", cfg.get("host"));
            putIfPresent(m, "dbPort", cfg.get("port"));
            putIfPresent(m, "dbName", cfg.get("database"));
        } catch (Exception e) {
            log.warn("Failed to summarize ing conn config");
        }
        return m;
    }

    private String resolveOrgName(Long orgId, String boundOrgName) {
        if (boundOrgName != null && !boundOrgName.isBlank()) {
            return boundOrgName.trim();
        }
        if (orgId == null) {
            return null;
        }
        SysOrg org = orgMapper.selectById(orgId);
        return org == null || org.getOrgName() == null || org.getOrgName().isBlank() ? null : org.getOrgName().trim();
    }

    private Map<String, Object> summarizeConn(String connConfigJson) {
        Map<String, Object> m = new HashMap<>();
        if (connConfigJson == null || connConfigJson.isBlank()) {
            return m;
        }
        try {
            Map<?, ?> cfg = OM.readValue(connConfigJson, Map.class);
            putIfPresent(m, "dbHost", cfg.get("host"));
            putIfPresent(m, "dbPort", cfg.get("port"));
            putIfPresent(m, "dbName", cfg.get("database"));
            putIfPresent(m, "username", cfg.get("username"));
            String cipher = cfg.get("passwordCipher") != null ? String.valueOf(cfg.get("passwordCipher")) : null;
            if (cipher != null && !cipher.isBlank()) {
                try {
                    m.put("password", credentialCipher.decrypt(cipher));
                } catch (Exception ex) {
                    m.put("password", "******");
                }
            } else if (cfg.get("password") != null && !String.valueOf(cfg.get("password")).isBlank()) {
                m.put("password", String.valueOf(cfg.get("password")));
            }
        } catch (Exception e) {
            log.warn("Failed to summarize ing conn config");
        }
        return m;
    }

    private static void putIfPresent(Map<String, Object> m, String key, Object val) {
        if (val != null && !String.valueOf(val).isBlank()) {
            m.put(key, val);
        }
    }

    private List<Long> parseLongList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    ids.add(Long.valueOf(String.valueOf(item)));
                }
            }
        }
        return ids;
    }

    private String extractPasswordCipher(String connConfigJson) {
        try {
            Map<?, ?> cfg = OM.readValue(connConfigJson, Map.class);
            Object cipher = cfg.get("passwordCipher");
            if (cipher != null && !String.valueOf(cipher).isBlank()) {
                return String.valueOf(cipher);
            }
            Object legacy = cfg.get("password");
            if (legacy != null && !String.valueOf(legacy).isBlank()) {
                return credentialCipher.encrypt(String.valueOf(legacy));
            }
        } catch (Exception e) {
            log.warn("Failed to parse password cipher from ing conn config");
        }
        return null;
    }

    private static String normalizeAdapterType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "MYSQL";
        }
        String s = sourceType.trim().toUpperCase(Locale.ROOT);
        if ("DAMENG".equals(s)) {
            return "DM";
        }
        if ("POSTGRES".equals(s)) {
            return "POSTGRESQL";
        }
        return s;
    }

    private GovMetaDataSource mapFromBody(Map<String, Object> body, GovMetaDataSource existing) {
        GovMetaDataSource row = existing == null ? new GovMetaDataSource() : existing;
        applyBody(row, body);
        return row;
    }

    private void applyBody(GovMetaDataSource row, Map<String, Object> body) {
        if (body.containsKey("sourceName")) {
            row.setSourceName(required(body.get("sourceName"), "名称").trim());
        }
        if (body.containsKey("sortOrder")) {
            row.setSortOrder(parseInt(body.get("sortOrder"), row.getSortOrder()));
        }
        if (body.containsKey("deptName")) {
            row.setDeptName(required(body.get("deptName"), "部门名称").trim());
        }
        if (body.containsKey("orgId")) {
            Object orgId = body.get("orgId");
            row.setOrgId(orgId == null || String.valueOf(orgId).isBlank() ? null : Long.valueOf(String.valueOf(orgId)));
        }
        if (body.containsKey("orgName")) {
            row.setOrgName(str(body.get("orgName"), null));
        }
        if (body.containsKey("categoryId")) {
            row.setCategoryId(Long.valueOf(String.valueOf(body.get("categoryId"))));
        }
        if (body.containsKey("tagCategoryId")) {
            Object tag = body.get("tagCategoryId");
            row.setTagCategoryId(tag == null || String.valueOf(tag).isBlank() ? null : Long.valueOf(String.valueOf(tag)));
        }
        if (body.containsKey("belongSystem")) {
            row.setBelongSystem(str(body.get("belongSystem"), null));
        }
        if (body.containsKey("remarks")) {
            row.setRemarks(str(body.get("remarks"), null));
        }
        if (body.containsKey("adapterType")) {
            row.setAdapterType(normalizeAdapter(body.get("adapterType")));
        }
        if (body.containsKey("realtimeFlag")) {
            row.setRealtimeFlag(boolInt(body.get("realtimeFlag")));
        }
        if (body.containsKey("readOnlyFlag")) {
            row.setReadOnlyFlag(boolInt(body.get("readOnlyFlag")));
        }
        if (body.containsKey("dbHost")) {
            row.setDbHost(required(body.get("dbHost"), "数据库连接地址").trim());
        }
        if (body.containsKey("dbPort")) {
            row.setDbPort(parseInt(body.get("dbPort"), 3306));
        }
        if (body.containsKey("dbName")) {
            row.setDbName(str(body.get("dbName"), null));
        }
        if (body.containsKey("dbSchema")) {
            row.setDbSchema(str(body.get("dbSchema"), null));
        }
        if (body.containsKey("username")) {
            row.setUsername(required(body.get("username"), "用户名").trim());
        }
    }

    private JdbcProbeService.ConnConfig toConnConfig(Map<String, Object> body, boolean requirePassword) {
        JdbcProbeService.ConnConfig cfg = new JdbcProbeService.ConnConfig();
        cfg.sourceType = normalizeAdapter(body.get("adapterType"));
        cfg.host = required(body.get("dbHost"), "数据库连接地址").trim();
        cfg.port = parseInt(body.get("dbPort"), 3306);
        cfg.database = str(body.get("dbName"), "");
        cfg.username = required(body.get("username"), "用户名").trim();
        String pwd = str(body.get("password"), null);
        if ((pwd == null || pwd.isBlank()) && body.get("id") != null) {
            GovMetaDataSource existing = require(Long.valueOf(String.valueOf(body.get("id"))));
            pwd = existing.getPasswordCipher() == null ? "" : credentialCipher.decrypt(existing.getPasswordCipher());
        }
        if (requirePassword && (pwd == null || pwd.isBlank())) {
            throw new BusinessException(400, "密码不能为空");
        }
        cfg.password = pwd == null ? "" : pwd;
        return cfg;
    }

    private void syncConnector(GovMetaDataSource row) {
        if (row.getDbHost() == null || row.getUsername() == null) {
            return;
        }
        GovOmConnector connector = row.getConnectorId() == null ? null : connectorMapper.selectById(row.getConnectorId());
        if (connector == null) {
            connector = new GovOmConnector();
            connector.setConnectorCode("META_DS_" + row.getId());
            connector.setStatus("ACTIVE");
            connector.setCreatedBy(row.getCreatedBy());
        }
        connector.setConnectorName(row.getSourceName());
        connector.setSourceType(row.getAdapterType());
        connector.setJdbcUrl(jdbcProbeService.jdbcUrl(toConnConfigFromRow(row)));
        connector.setJdbcUser(row.getUsername());
        connector.setJdbcPassword(row.getPasswordCipher());
        connector.setJdbcDatabase(row.getDbName());
        connector.setLastMessage("sync from gov_meta_data_source");
        connector.setLastSyncAt(LocalDateTime.now());
        if (connector.getId() == null) {
            connectorMapper.insert(connector);
            row.setConnectorId(connector.getId());
            dataSourceMapper.updateById(row);
        } else {
            connectorMapper.updateById(connector);
        }
    }

    private JdbcProbeService.ConnConfig toConnConfigFromRow(GovMetaDataSource row) {
        JdbcProbeService.ConnConfig cfg = new JdbcProbeService.ConnConfig();
        cfg.sourceType = row.getAdapterType();
        cfg.host = row.getDbHost();
        cfg.port = row.getDbPort() == null ? 3306 : row.getDbPort();
        cfg.database = row.getDbName() == null ? "" : row.getDbName();
        cfg.username = row.getUsername();
        cfg.password = row.getPasswordCipher() == null ? "" : credentialCipher.decrypt(row.getPasswordCipher());
        return cfg;
    }

    private Map<Long, String> loadOrgNames(List<GovMetaDataSource> rows) {
        Set<Long> orgIds = new HashSet<>();
        for (GovMetaDataSource row : rows) {
            if (row.getOrgId() != null) {
                orgIds.add(row.getOrgId());
            }
        }
        if (orgIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (SysOrg org : orgMapper.selectBatchIds(orgIds)) {
            if (org != null && org.getId() != null && org.getOrgName() != null && !org.getOrgName().isBlank()) {
                names.put(org.getId(), org.getOrgName().trim());
            }
        }
        return names;
    }

    private Map<Long, String> loadDeptNamesFromIngSources(List<GovMetaDataSource> rows) {
        Map<Long, String> out = new HashMap<>();
        for (GovMetaDataSource row : rows) {
            if (row.getIngSourceId() == null || row.getIngSourceId() <= 0) {
                continue;
            }
            if (row.getDeptName() != null && !row.getDeptName().isBlank()) {
                continue;
            }
            if (row.getOrgId() != null) {
                continue;
            }
            IngDataSource ing = ingDataSourceMapper.selectById(row.getIngSourceId());
            if (ing == null) {
                continue;
            }
            IngProject project = resolveIngProject(ing);
            if (project == null) {
                continue;
            }
            String deptName = resolveOrgName(project.getBoundOrgId(), project.getBoundOrgName());
            if (deptName != null && !deptName.isBlank()) {
                out.put(row.getId(), deptName);
            }
        }
        return out;
    }

    private Map<String, Object> toView(GovMetaDataSource row, Map<Long, String> categoryNames,
                                       Map<Long, String> orgNames, Map<Long, String> ingDeptNames) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", row.getId());
        m.put("sourceCode", row.getSourceCode());
        m.put("sourceName", row.getSourceName());
        m.put("sortOrder", row.getSortOrder());
        String deptName = row.getDeptName();
        if (deptName == null || deptName.isBlank()) {
            deptName = row.getOrgName();
        }
        if ((deptName == null || deptName.isBlank()) && row.getOrgId() != null) {
            deptName = orgNames.get(row.getOrgId());
        }
        if (deptName == null || deptName.isBlank()) {
            deptName = ingDeptNames.get(row.getId());
        }
        m.put("deptName", deptName);
        m.put("orgId", row.getOrgId());
        m.put("orgName", row.getOrgName());
        m.put("categoryId", row.getCategoryId());
        m.put("categoryName", categoryNames.getOrDefault(row.getCategoryId(), "—"));
        m.put("tagCategoryId", row.getTagCategoryId());
        m.put("tagCategoryName", row.getTagCategoryId() == null ? "—" : categoryNames.getOrDefault(row.getTagCategoryId(), "—"));
        m.put("belongSystem", row.getBelongSystem());
        m.put("remarks", row.getRemarks());
        m.put("adapterType", row.getAdapterType());
        m.put("realtimeFlag", row.getRealtimeFlag());
        m.put("readOnlyFlag", row.getReadOnlyFlag());
        m.put("dbHost", row.getDbHost());
        m.put("dbPort", row.getDbPort());
        m.put("dbName", row.getDbName());
        m.put("dbSchema", row.getDbSchema());
        m.put("username", row.getUsername());
        m.put("ingSourceId", row.getIngSourceId());
        m.put("status", row.getStatus());
        m.put("createdAt", row.getCreatedAt());
        return m;
    }

    private GovMetaDataSource require(Long id) {
        GovMetaDataSource row = dataSourceMapper.selectById(id);
        if (row == null || "DELETED".equals(row.getStatus())) {
            throw new BusinessException(404, "数据源不存在");
        }
        return row;
    }

    private String generateCode(String name) {
        String base = "MDS_" + (name == null ? "SRC" : name.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5]", ""));
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        return base + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private void audit(UserPrincipal operator, String action, GovMetaDataSource row) {
        if (operator == null) {
            return;
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                action, "gov_meta_data_source", String.valueOf(row.getId()), row.getSourceName());
    }

    private static String normalizeAdapter(Object v) {
        String s = required(v, "适配器类型").trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "MYSQL", "ORACLE", "POSTGRESQL", "POSTGRES", "DM", "KINGBASE", "GBASE", "HBASE", "HIVE" -> s;
            default -> s;
        };
    }

    private static String required(Object v, String label) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, label + "不能为空");
        }
        return String.valueOf(v);
    }

    private static String str(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static int parseInt(Object v, Integer def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def == null ? 0 : def;
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private static int boolInt(Object v) {
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return "1".equals(String.valueOf(v)) || "true".equalsIgnoreCase(String.valueOf(v)) ? 1 : 0;
    }
}
