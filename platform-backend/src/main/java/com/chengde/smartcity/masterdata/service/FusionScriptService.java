package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.ds.DolphinSchedulerClient;
import com.chengde.smartcity.masterdata.entity.GovFusionScript;
import com.chengde.smartcity.masterdata.entity.GovFusionScriptRun;
import com.chengde.smartcity.masterdata.entity.GovFusionScriptVersion;
import com.chengde.smartcity.masterdata.mapper.GovFusionScriptMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionScriptRunMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionScriptVersionMapper;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.masterdata.support.LayerJdbcSupport;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FusionScriptService {

    private static final Logger log = LoggerFactory.getLogger(FusionScriptService.class);
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE|DELETE\\s+FROM|INSERT\\s+INTO|REPLACE\\s+INTO|CALL\\s|EXEC\\s|EXECUTE\\s|--|;\\s*\\S)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final int SELECT_LIMIT = 100;
    private static final ObjectMapper OM = new ObjectMapper();
    private static final long PLATFORM_ODS_ID = -1L;
    private static final long PLATFORM_DWD_ID = -2L;
    private static final long PLATFORM_DWS_ID = -3L;
    private static final long PLATFORM_ADS_ID = -4L;
    private static final String DS_PROD_PROJECT = "chengde_fusion_script_prod";

    private final GovFusionScriptMapper scriptMapper;
    private final GovFusionScriptVersionMapper versionMapper;
    private final GovFusionScriptRunMapper runMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final LayerJdbcSupport layerJdbc;
    private final DolphinSchedulerClient dsClient;
    private final IntegrationProperties integrationProperties;

    public FusionScriptService(GovFusionScriptMapper scriptMapper,
                               GovFusionScriptVersionMapper versionMapper,
                               GovFusionScriptRunMapper runMapper,
                               IngDataSourceMapper dataSourceMapper,
                               LayerJdbcSupport layerJdbc,
                               DolphinSchedulerClient dsClient,
                               IntegrationProperties integrationProperties) {
        this.scriptMapper = scriptMapper;
        this.versionMapper = versionMapper;
        this.runMapper = runMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.layerJdbc = layerJdbc;
        this.dsClient = dsClient;
        this.integrationProperties = integrationProperties;
    }

    public List<Map<String, Object>> list() {
        List<GovFusionScript> scripts = scriptMapper.selectList(new LambdaQueryWrapper<GovFusionScript>()
                .orderByDesc(GovFusionScript::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovFusionScript s : scripts) {
            out.add(toMap(s));
        }
        return out;
    }

    public Map<String, Object> get(Long id) {
        return toMap(require(id));
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        String code = str(body.get("scriptCode"));
        String name = str(body.get("scriptName"));
        String content = str(body.get("scriptContent"));
        if (code == null || name == null || content == null) {
            throw new BusinessException(400, "scriptCode/scriptName/scriptContent 必填");
        }
        GovFusionScript s = new GovFusionScript();
        s.setScriptCode(code);
        s.setScriptName(name);
        s.setScriptType(str(body.get("scriptType"), "SELECT"));
        s.setScriptContent(content);
        s.setDatasourceId(longVal(body.get("datasourceId")));
        s.setPublishStatus("DRAFT");
        s.setVersionNo(1);
        s.setStatus(str(body.get("status"), "ACTIVE"));
        s.setEnvScope(normalizeEnv(str(body.get("envScope"), "DEV")));
        if (operator != null) s.setCreatedBy(operator.getUsername());
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.insert(s);
        return s.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovFusionScript s = require(id);
        assertEditable(operator, s);
        if (body.containsKey("scriptName")) s.setScriptName(str(body.get("scriptName")));
        if (body.containsKey("scriptType")) s.setScriptType(str(body.get("scriptType")));
        if (body.containsKey("scriptContent")) s.setScriptContent(str(body.get("scriptContent")));
        if (body.containsKey("datasourceId")) s.setDatasourceId(longVal(body.get("datasourceId")));
        if (body.containsKey("status")) s.setStatus(str(body.get("status")));
        if (body.containsKey("envScope")) s.setEnvScope(normalizeEnv(str(body.get("envScope"))));
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(s);
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovFusionScript s = require(id);
        if (isLockedByOther(operator, s)) {
            throw new BusinessException(403, "脚本已被 " + s.getLockedBy() + " 锁定，无法删除");
        }
        versionMapper.delete(new LambdaQueryWrapper<GovFusionScriptVersion>()
                .eq(GovFusionScriptVersion::getScriptId, id));
        scriptMapper.deleteById(id);
    }

    @Transactional
    public Map<String, Object> execute(UserPrincipal operator, Long id) {
        GovFusionScript s = require(id);
        String sql = s.getScriptContent();
        if (sql == null || sql.isBlank()) {
            throw new BusinessException(400, "脚本内容为空");
        }
        if (sql.contains("demo_col") || sql.trim().equalsIgnoreCase("SELECT 1 AS demo_col")) {
            throw new BusinessException(400, "请选择真实表后生成查询，禁止使用演示占位 SQL");
        }
        validateSql(sql, s.getScriptType());
        String normalized = normalizeSelect(sql);

        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime started = LocalDateTime.now();
        GovFusionScriptRun run = new GovFusionScriptRun();
        run.setScriptId(id);
        run.setStartedAt(started);
        run.setStatus("RUNNING");
        run.setCreatedAt(started);
        runMapper.insert(run);

        try (Connection conn = openConnection(s.getDatasourceId())) {
            String upper = sql.trim().toUpperCase(Locale.ROOT);
            if (upper.startsWith("UPDATE")) {
                try (Statement st = conn.createStatement()) {
                    int affected = st.executeUpdate(sql);
                    result.put("mode", "UPDATE");
                    result.put("affectedRows", affected);
                    result.put("status", "SUCCESS");
                    result.put("message", "影响行数: " + affected);
                    s.setLastMessage("UPDATE 影响 " + affected + " 行");
                }
            } else {
                try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(normalized)) {
                    List<Map<String, Object>> rows = readRows(rs, SELECT_LIMIT);
                    result.put("mode", "SELECT");
                    result.put("rows", rows);
                    result.put("rowCount", rows.size());
                    result.put("status", "SUCCESS");
                    result.put("message", "返回 " + rows.size() + " 行（最多 " + SELECT_LIMIT + "）");
                    s.setLastMessage("SELECT 返回 " + rows.size() + " 行");
                }
            }
            LocalDateTime ended = LocalDateTime.now();
            run.setEndedAt(ended);
            run.setDurationMs(Duration.between(started, ended).toMillis());
            run.setStatus("SUCCESS");
            run.setMessage(truncate(String.valueOf(result.get("message")), 500));
            runMapper.updateById(run);
            result.put("runId", run.getId());
        } catch (BusinessException e) {
            failRun(run, started, e.getMessage());
            s.setLastMessage(truncate(e.getMessage(), 500));
            s.setLastRunAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(s);
            throw e;
        } catch (Exception e) {
            log.warn("fusion script execute failed id={}: {}", id, e.getMessage());
            failRun(run, started, e.getMessage());
            s.setLastMessage(truncate(e.getMessage(), 500));
            s.setLastRunAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(s);
            throw new BusinessException(500, "执行失败: " + e.getMessage());
        }
        s.setLastRunAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(s);
        return result;
    }

    private void failRun(GovFusionScriptRun run, LocalDateTime started, String message) {
        LocalDateTime ended = LocalDateTime.now();
        run.setEndedAt(ended);
        run.setDurationMs(Duration.between(started, ended).toMillis());
        run.setStatus("FAILED");
        run.setMessage(truncate(message, 500));
        runMapper.updateById(run);
    }

    @Transactional
    public Map<String, Object> publish(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovFusionScript s = require(id);
        String summary = str(body.get("changeSummary"));
        int nextVer = (s.getVersionNo() == null ? 0 : s.getVersionNo()) + 1;
        GovFusionScriptVersion ver = new GovFusionScriptVersion();
        ver.setScriptId(id);
        ver.setVersionNo(nextVer);
        ver.setScriptContent(s.getScriptContent());
        ver.setChangeSummary(summary);
        ver.setPublishedBy(operator != null ? operator.getUsername() : "system");
        ver.setPublishedAt(LocalDateTime.now());
        versionMapper.insert(ver);
        s.setVersionNo(nextVer);
        s.setPublishStatus("PUBLISHED");
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(s);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("versionNo", nextVer);
        out.put("publishStatus", s.getPublishStatus());
        return out;
    }

    public List<GovFusionScriptVersion> listVersions(Long id) {
        require(id);
        return versionMapper.selectList(new LambdaQueryWrapper<GovFusionScriptVersion>()
                .eq(GovFusionScriptVersion::getScriptId, id)
                .orderByDesc(GovFusionScriptVersion::getVersionNo));
    }

    public List<Map<String, Object>> listRuns(Long scriptId) {
        LambdaQueryWrapper<GovFusionScriptRun> q = new LambdaQueryWrapper<GovFusionScriptRun>()
                .orderByDesc(GovFusionScriptRun::getId)
                .last("LIMIT 100");
        if (scriptId != null) {
            require(scriptId);
            q.eq(GovFusionScriptRun::getScriptId, scriptId);
        }
        List<GovFusionScriptRun> runs = runMapper.selectList(q);
        Map<Long, String> nameCache = new LinkedHashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovFusionScriptRun r : runs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("scriptId", r.getScriptId());
            String name = nameCache.computeIfAbsent(r.getScriptId(), sid -> {
                GovFusionScript s = scriptMapper.selectById(sid);
                return s != null ? s.getScriptName() : ("脚本#" + sid);
            });
            row.put("scriptName", name);
            row.put("startedAt", r.getStartedAt());
            row.put("endedAt", r.getEndedAt());
            row.put("durationMs", r.getDurationMs());
            row.put("status", r.getStatus());
            row.put("message", r.getMessage());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> rollback(UserPrincipal operator, Long id, Integer versionNo) {
        GovFusionScript s = require(id);
        assertEditable(operator, s);
        GovFusionScriptVersion ver = versionMapper.selectOne(new LambdaQueryWrapper<GovFusionScriptVersion>()
                .eq(GovFusionScriptVersion::getScriptId, id)
                .eq(GovFusionScriptVersion::getVersionNo, versionNo));
        if (ver == null) {
            throw new BusinessException(404, "版本不存在: v" + versionNo);
        }
        s.setScriptContent(ver.getScriptContent());
        s.setPublishStatus("DRAFT");
        s.setEnvScope("DEV");
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(s);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("versionNo", versionNo);
        out.put("publishStatus", s.getPublishStatus());
        out.put("envScope", s.getEnvScope());
        return out;
    }

    @Transactional
    public Map<String, Object> lock(UserPrincipal operator, Long id) {
        GovFusionScript s = require(id);
        String user = operator != null ? operator.getUsername() : "system";
        if (s.getLockedBy() != null && !s.getLockedBy().isBlank() && !s.getLockedBy().equals(user)) {
            throw new BusinessException(403, "脚本已被 " + s.getLockedBy() + " 锁定");
        }
        s.setLockedBy(user);
        s.setLockedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(s);
        return toMap(s);
    }

    @Transactional
    public Map<String, Object> unlock(UserPrincipal operator, Long id) {
        GovFusionScript s = require(id);
        String user = operator != null ? operator.getUsername() : "system";
        boolean privileged = operator != null && operator.isSystemAdmin();
        if (s.getLockedBy() != null && !s.getLockedBy().isBlank()
                && !s.getLockedBy().equals(user) && !privileged) {
            throw new BusinessException(403, "仅锁定人或系统管理员可解锁");
        }
        s.setLockedBy(null);
        s.setLockedAt(null);
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(s);
        return toMap(s);
    }

    @Transactional
    public Map<String, Object> setEnv(UserPrincipal operator, Long id, String envScope) {
        GovFusionScript s = require(id);
        assertEditable(operator, s);
        s.setEnvScope(normalizeEnv(envScope));
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(s);
        return toMap(s);
    }

    /**
     * 一键发布到生产调度：先落版本快照，再在 DS 生产项目创建可回调执行的流程定义。
     */
    @Transactional
    public Map<String, Object> deployToProduction(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovFusionScript s = require(id);
        assertEditable(operator, s);
        if (s.getScriptContent() == null || s.getScriptContent().isBlank()) {
            throw new BusinessException(400, "脚本内容为空，无法部署");
        }
        if (!isDsAvailable()) {
            throw new BusinessException(502, "DolphinScheduler 不可用，无法部署到生产调度");
        }
        Map<String, Object> published = publish(operator, id, body == null ? Map.of("changeSummary", "一键发布到生产") : body);
        int versionNo = ((Number) published.get("versionNo")).intValue();
        s = require(id);

        try {
            if (s.getDsProjectCode() != null && s.getDsDefinitionCode() != null) {
                try {
                    dsClient.releaseDefinition(s.getDsProjectCode(), s.getDsDefinitionCode(), "OFFLINE");
                    dsClient.deleteDefinition(s.getDsProjectCode(), s.getDsDefinitionCode());
                } catch (Exception e) {
                    log.warn("remove old fusion script DS def id={}: {}", id, e.getMessage());
                }
            }
            long projectCode = dsClient.ensureProject(DS_PROD_PROJECT);
            String tenant = dsClient.resolveTenant();
            String defName = "融合脚本_" + safeName(s.getScriptName()) + "_" + id + "_v" + versionNo;
            String shell = buildProdTriggerScript(id);
            long definitionCode = dsClient.createAndReleaseShellChain(
                    projectCode, defName, List.of("执行融合脚本"), List.of(shell), tenant);
            s.setDsProjectCode(projectCode);
            s.setDsDefinitionCode(definitionCode);
            s.setProdDeployedVersion(versionNo);
            s.setProdDeployedAt(LocalDateTime.now());
            s.setEnvScope("PROD");
            s.setPublishStatus("PUBLISHED");
            s.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(s);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("versionNo", versionNo);
            out.put("envScope", s.getEnvScope());
            out.put("projectCode", projectCode);
            out.put("definitionCode", definitionCode);
            out.put("prodDeployedVersion", versionNo);
            out.put("message", "已发布 v" + versionNo + " 并部署到生产调度项目 " + DS_PROD_PROJECT);
            return out;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "部署到生产调度失败: " + e.getMessage());
        }
    }

    public Map<String, Object> executeFromDsCallback(Long id, String token) {
        assertCallbackToken(token);
        return execute(null, id);
    }

    private String buildProdTriggerScript(Long scriptId) {
        String base = integrationProperties.getDs().getCallbackBaseUrl();
        if (base == null || base.isBlank()) {
            throw new BusinessException(500, "未配置 app.integration.ds.callback-base-url");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String token = resolveCallbackToken();
        String url = base + "/api/v1/governance/fusion/scripts/" + scriptId + "/ds-trigger";
        return "curl -sf -X POST \"" + url + "\" "
                + "-H \"X-Ds-Callback-Token: " + token + "\" "
                + "-H \"Content-Type: application/json\" "
                + "-d \"{}\" "
                + "|| exit 1";
    }

    private boolean isDsAvailable() {
        return integrationProperties.isEnabled() && dsClient.isHealthy();
    }

    private String resolveCallbackToken() {
        String token = integrationProperties.getDs().getCallbackToken();
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String pwd = integrationProperties.getDs().getPassword();
        return pwd == null ? "chengde-gov-callback" : pwd;
    }

    private void assertCallbackToken(String token) {
        String expected = resolveCallbackToken();
        if (token == null || !expected.equals(token.trim())) {
            throw new BusinessException(403, "DS 回调令牌无效");
        }
    }

    private void assertEditable(UserPrincipal operator, GovFusionScript s) {
        if (isLockedByOther(operator, s)) {
            throw new BusinessException(403, "脚本已被 " + s.getLockedBy() + " 锁定");
        }
        if ("PROD".equalsIgnoreCase(s.getEnvScope())) {
            String user = operator != null ? operator.getUsername() : null;
            boolean privileged = operator != null && operator.isSystemAdmin();
            boolean locker = s.getLockedBy() != null && s.getLockedBy().equals(user);
            if (!privileged && !locker) {
                throw new BusinessException(400, "生产环境脚本请先锁定后再编辑，或先回滚到开发环境");
            }
        }
    }

    private boolean isLockedByOther(UserPrincipal operator, GovFusionScript s) {
        if (s.getLockedBy() == null || s.getLockedBy().isBlank()) {
            return false;
        }
        String user = operator != null ? operator.getUsername() : null;
        if (user != null && user.equals(s.getLockedBy())) {
            return false;
        }
        return operator == null || !operator.isSystemAdmin();
    }

    private static String normalizeEnv(String env) {
        if (env == null || env.isBlank()) {
            return "DEV";
        }
        String e = env.trim().toUpperCase(Locale.ROOT);
        if (!"DEV".equals(e) && !"PROD".equals(e)) {
            throw new BusinessException(400, "envScope 仅支持 DEV/PROD");
        }
        return e;
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "script";
        }
        return name.replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5-]", "_");
    }

    private void validateSql(String sql, String scriptType) {
        String trimmed = sql.trim();
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new BusinessException(400, "脚本含禁止语句（DROP/DELETE/INSERT 等）");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        String type = scriptType == null ? "SELECT" : scriptType.toUpperCase(Locale.ROOT);
        if ("UPDATE".equals(type)) {
            if (!upper.startsWith("UPDATE")) {
                throw new BusinessException(400, "UPDATE 类型脚本须以 UPDATE 开头");
            }
        } else if (!upper.startsWith("SELECT")) {
            throw new BusinessException(400, "SELECT 类型脚本须以 SELECT 开头");
        }
    }

    private String normalizeSelect(String sql) {
        String trimmed = sql.trim();
        if (!trimmed.toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            return trimmed;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.contains(" LIMIT ") || upper.endsWith(" LIMIT")) {
            return trimmed;
        }
        return trimmed + " LIMIT " + SELECT_LIMIT;
    }

    private List<Map<String, Object>> readRows(ResultSet rs, int max) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        while (rs.next() && rows.size() < max) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private Connection openConnection(Long datasourceId) throws Exception {
        if (isPlatformLayerId(datasourceId)) {
            return layerJdbc.open(platformLayerDatabase(datasourceId));
        }
        if (datasourceId != null) {
            IngDataSource ds = dataSourceMapper.selectById(datasourceId);
            if (ds == null) {
                throw new BusinessException(400, "数据源不存在: " + datasourceId);
            }
            JsonNode n = OM.readTree(ds.getConnConfigJson());
            String host = text(n, "host");
            String port = text(n, "port");
            String database = text(n, "database");
            String username = text(n, "username");
            String password = text(n, "password");
            if (host == null || database == null) {
                throw new BusinessException(400, "数据源缺少 host/database");
            }
            String url = "jdbc:mysql://" + host + ":" + (port == null ? "3306" : port) + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
            return DriverManager.getConnection(url, username == null ? "" : username, password == null ? "" : password);
        }
        return layerJdbc.open(DataLayerSupport.ODS);
    }

    private static boolean isPlatformLayerId(Long id) {
        return id != null && (id == PLATFORM_ODS_ID || id == PLATFORM_DWD_ID
                || id == PLATFORM_DWS_ID || id == PLATFORM_ADS_ID);
    }

    private static String platformLayerDatabase(Long id) {
        if (id == PLATFORM_ODS_ID) return DataLayerSupport.ODS;
        if (id == PLATFORM_DWD_ID) return DataLayerSupport.DWD;
        if (id == PLATFORM_DWS_ID) return DataLayerSupport.DWS;
        if (id == PLATFORM_ADS_ID) return DataLayerSupport.ADS;
        throw new BusinessException(400, "非平台分层数据源");
    }

    private static void requireIdent(String name, String field) {
        if (name == null || name.isBlank() || !IDENT.matcher(name).matches()) {
            throw new BusinessException(400, field + " 非法: " + name);
        }
    }

    private Map<String, Object> toMap(GovFusionScript s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("scriptCode", s.getScriptCode());
        m.put("scriptName", s.getScriptName());
        m.put("scriptType", s.getScriptType());
        m.put("scriptContent", s.getScriptContent());
        m.put("datasourceId", s.getDatasourceId());
        m.put("publishStatus", s.getPublishStatus());
        m.put("versionNo", s.getVersionNo());
        m.put("status", s.getStatus());
        m.put("lastRunAt", s.getLastRunAt());
        m.put("lastMessage", s.getLastMessage());
        m.put("lockedBy", s.getLockedBy());
        m.put("lockedAt", s.getLockedAt());
        m.put("envScope", s.getEnvScope() == null ? "DEV" : s.getEnvScope());
        m.put("dsProjectCode", s.getDsProjectCode());
        m.put("dsDefinitionCode", s.getDsDefinitionCode());
        m.put("prodDeployedVersion", s.getProdDeployedVersion());
        m.put("prodDeployedAt", s.getProdDeployedAt());
        m.put("createdBy", s.getCreatedBy());
        m.put("createdAt", s.getCreatedAt());
        m.put("updatedAt", s.getUpdatedAt());
        return m;
    }

    private GovFusionScript require(Long id) {
        GovFusionScript s = scriptMapper.selectById(id);
        if (s == null) throw new BusinessException(404, "融合脚本不存在: " + id);
        return s;
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String str(Object v, String def) {
        String s = str(v);
        return s == null ? def : s;
    }

    private static Long longVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        String s = n.get(field).asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
