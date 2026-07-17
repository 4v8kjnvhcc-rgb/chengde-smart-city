package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.masterdata.entity.GovFusionScript;
import com.chengde.smartcity.masterdata.entity.GovFusionScriptVersion;
import com.chengde.smartcity.masterdata.mapper.GovFusionScriptMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionScriptVersionMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FusionScriptService {

    private static final Logger log = LoggerFactory.getLogger(FusionScriptService.class);
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE|DELETE\\s+FROM|INSERT\\s+INTO|REPLACE\\s+INTO|CALL\\s|EXEC\\s|EXECUTE\\s|--|;\\s*\\S)",
            Pattern.CASE_INSENSITIVE);
    private static final int SELECT_LIMIT = 100;
    private static final ObjectMapper OM = new ObjectMapper();

    private final GovFusionScriptMapper scriptMapper;
    private final GovFusionScriptVersionMapper versionMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final DataSource platformDataSource;

    public FusionScriptService(GovFusionScriptMapper scriptMapper,
                               GovFusionScriptVersionMapper versionMapper,
                               IngDataSourceMapper dataSourceMapper,
                               @Autowired(required = false) DataSource platformDataSource) {
        this.scriptMapper = scriptMapper;
        this.versionMapper = versionMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.platformDataSource = platformDataSource;
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
        if (operator != null) s.setCreatedBy(operator.getUsername());
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.insert(s);
        return s.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovFusionScript s = require(id);
        if (body.containsKey("scriptName")) s.setScriptName(str(body.get("scriptName")));
        if (body.containsKey("scriptType")) s.setScriptType(str(body.get("scriptType")));
        if (body.containsKey("scriptContent")) s.setScriptContent(str(body.get("scriptContent")));
        if (body.containsKey("datasourceId")) s.setDatasourceId(longVal(body.get("datasourceId")));
        if (body.containsKey("status")) s.setStatus(str(body.get("status")));
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(s);
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        require(id);
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
        validateSql(sql, s.getScriptType());
        String normalized = normalizeSelect(sql);

        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
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
                    result.put("message", "返回 " + rows.size() + " 行");
                    s.setLastMessage("SELECT 返回 " + rows.size() + " 行");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("fusion script execute failed id={}: {}", id, e.getMessage());
            s.setLastMessage(truncate(e.getMessage(), 500));
            s.setLastRunAt(now);
            s.setUpdatedAt(now);
            scriptMapper.updateById(s);
            throw new BusinessException(500, "执行失败: " + e.getMessage());
        }
        s.setLastRunAt(now);
        s.setUpdatedAt(now);
        scriptMapper.updateById(s);
        return result;
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

    @Transactional
    public Map<String, Object> rollback(UserPrincipal operator, Long id, Integer versionNo) {
        GovFusionScript s = require(id);
        GovFusionScriptVersion ver = versionMapper.selectOne(new LambdaQueryWrapper<GovFusionScriptVersion>()
                .eq(GovFusionScriptVersion::getScriptId, id)
                .eq(GovFusionScriptVersion::getVersionNo, versionNo));
        if (ver == null) {
            throw new BusinessException(404, "版本不存在: v" + versionNo);
        }
        s.setScriptContent(ver.getScriptContent());
        s.setPublishStatus("DRAFT");
        s.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(s);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("versionNo", versionNo);
        out.put("publishStatus", s.getPublishStatus());
        return out;
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
        if (platformDataSource != null) {
            return platformDataSource.getConnection();
        }
        throw new BusinessException(500, "无可用 JDBC 数据源");
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
