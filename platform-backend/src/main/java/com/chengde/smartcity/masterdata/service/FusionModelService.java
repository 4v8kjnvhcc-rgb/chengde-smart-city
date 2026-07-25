package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.masterdata.entity.GovFusionDomain;
import com.chengde.smartcity.masterdata.entity.GovFusionField;
import com.chengde.smartcity.masterdata.entity.GovFusionLogicEntity;
import com.chengde.smartcity.masterdata.entity.GovFusionPhysical;
import com.chengde.smartcity.masterdata.entity.GovFusionRelation;
import com.chengde.smartcity.masterdata.mapper.GovFusionDomainMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionFieldMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionLogicEntityMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionPhysicalMapper;
import com.chengde.smartcity.masterdata.mapper.GovFusionRelationMapper;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FusionModelService {

    private static final Logger log = LoggerFactory.getLogger(FusionModelService.class);
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Set<String> PLACEHOLDER_TABLES = Set.of("table_name", "output_table", "demo_table", "xxx");
    private static final ObjectMapper OM = new ObjectMapper();
    private static final long PLATFORM_ODS_ID = -1L;
    private static final long PLATFORM_DWD_ID = -2L;
    private static final long PLATFORM_DWS_ID = -3L;
    private static final long PLATFORM_ADS_ID = -4L;
    private static final int PREVIEW_LIMIT = 50;

    private final GovFusionDomainMapper domainMapper;
    private final GovFusionLogicEntityMapper entityMapper;
    private final GovFusionFieldMapper fieldMapper;
    private final GovFusionRelationMapper relationMapper;
    private final GovFusionPhysicalMapper physicalMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final DataSource platformDataSource;

    public FusionModelService(GovFusionDomainMapper domainMapper,
                              GovFusionLogicEntityMapper entityMapper,
                              GovFusionFieldMapper fieldMapper,
                              GovFusionRelationMapper relationMapper,
                              GovFusionPhysicalMapper physicalMapper,
                              IngDataSourceMapper dataSourceMapper,
                              @Autowired(required = false) DataSource platformDataSource) {
        this.domainMapper = domainMapper;
        this.entityMapper = entityMapper;
        this.fieldMapper = fieldMapper;
        this.relationMapper = relationMapper;
        this.physicalMapper = physicalMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.platformDataSource = platformDataSource;
    }

    public List<Map<String, Object>> listDomains() {
        List<GovFusionDomain> domains = domainMapper.selectList(new LambdaQueryWrapper<GovFusionDomain>()
                .orderByAsc(GovFusionDomain::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovFusionDomain d : domains) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("domainCode", d.getDomainCode());
            m.put("domainName", d.getDomainName());
            m.put("description", d.getDescription());
            m.put("status", d.getStatus());
            long entityCount = entityMapper.selectCount(new LambdaQueryWrapper<GovFusionLogicEntity>()
                    .eq(GovFusionLogicEntity::getDomainId, d.getId()));
            m.put("entityCount", entityCount);
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> getDomainTree(Long domainId) {
        GovFusionDomain domain = requireDomain(domainId);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("domain", domain);
        List<GovFusionLogicEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<GovFusionLogicEntity>()
                .eq(GovFusionLogicEntity::getDomainId, domainId)
                .orderByAsc(GovFusionLogicEntity::getId));
        List<Map<String, Object>> entityNodes = new ArrayList<>();
        for (GovFusionLogicEntity e : entities) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("entity", e);
            em.put("fields", fieldMapper.selectList(new LambdaQueryWrapper<GovFusionField>()
                    .eq(GovFusionField::getEntityId, e.getId())
                    .orderByAsc(GovFusionField::getSortOrder)
                    .orderByAsc(GovFusionField::getId)));
            em.put("physical", physicalMapper.selectList(new LambdaQueryWrapper<GovFusionPhysical>()
                    .eq(GovFusionPhysical::getEntityId, e.getId())
                    .orderByAsc(GovFusionPhysical::getId)));
            entityNodes.add(em);
        }
        root.put("entities", entityNodes);
        root.put("relations", relationMapper.selectList(new LambdaQueryWrapper<GovFusionRelation>()
                .eq(GovFusionRelation::getDomainId, domainId)
                .orderByAsc(GovFusionRelation::getId)));
        return root;
    }

    @Transactional
    public Long createDomain(UserPrincipal operator, Map<String, Object> body) {
        String code = str(body.get("domainCode"));
        String name = str(body.get("domainName"));
        if (code == null || name == null) {
            throw new BusinessException(400, "domainCode/domainName 必填");
        }
        GovFusionDomain d = new GovFusionDomain();
        d.setDomainCode(code);
        d.setDomainName(name);
        d.setDescription(str(body.get("description")));
        d.setStatus(str(body.get("status"), "ACTIVE"));
        if (operator != null) d.setCreatedBy(operator.getUsername());
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        domainMapper.insert(d);
        return d.getId();
    }

    @Transactional
    public void updateDomain(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovFusionDomain d = requireDomain(id);
        if (body.containsKey("domainName")) d.setDomainName(str(body.get("domainName")));
        if (body.containsKey("description")) d.setDescription(str(body.get("description")));
        if (body.containsKey("status")) d.setStatus(str(body.get("status")));
        d.setUpdatedAt(LocalDateTime.now());
        domainMapper.updateById(d);
    }

    @Transactional
    public void deleteDomain(UserPrincipal operator, Long id) {
        requireDomain(id);
        List<GovFusionLogicEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<GovFusionLogicEntity>()
                .eq(GovFusionLogicEntity::getDomainId, id));
        for (GovFusionLogicEntity e : entities) {
            fieldMapper.delete(new LambdaQueryWrapper<GovFusionField>().eq(GovFusionField::getEntityId, e.getId()));
            physicalMapper.delete(new LambdaQueryWrapper<GovFusionPhysical>().eq(GovFusionPhysical::getEntityId, e.getId()));
        }
        entityMapper.delete(new LambdaQueryWrapper<GovFusionLogicEntity>().eq(GovFusionLogicEntity::getDomainId, id));
        relationMapper.delete(new LambdaQueryWrapper<GovFusionRelation>().eq(GovFusionRelation::getDomainId, id));
        domainMapper.deleteById(id);
    }

    public List<GovFusionLogicEntity> listEntities(Long domainId) {
        return entityMapper.selectList(new LambdaQueryWrapper<GovFusionLogicEntity>()
                .eq(GovFusionLogicEntity::getDomainId, domainId)
                .orderByAsc(GovFusionLogicEntity::getId));
    }

    @Transactional
    public Long createEntity(UserPrincipal operator, Map<String, Object> body) {
        Long domainId = longVal(body.get("domainId"));
        String code = str(body.get("entityCode"));
        String name = str(body.get("entityName"));
        if (domainId == null || code == null || name == null) {
            throw new BusinessException(400, "domainId/entityCode/entityName 必填");
        }
        requireDomain(domainId);
        GovFusionLogicEntity e = new GovFusionLogicEntity();
        e.setDomainId(domainId);
        e.setEntityCode(code);
        e.setEntityName(name);
        e.setDescription(str(body.get("description")));
        e.setStatus(str(body.get("status"), "ACTIVE"));
        if (operator != null) e.setCreatedBy(operator.getUsername());
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        entityMapper.insert(e);
        return e.getId();
    }

    @Transactional
    public void updateEntity(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovFusionLogicEntity e = requireEntity(id);
        if (body.containsKey("entityName")) e.setEntityName(str(body.get("entityName")));
        if (body.containsKey("description")) e.setDescription(str(body.get("description")));
        if (body.containsKey("status")) e.setStatus(str(body.get("status")));
        e.setUpdatedAt(LocalDateTime.now());
        entityMapper.updateById(e);
    }

    @Transactional
    public void deleteEntity(UserPrincipal operator, Long id) {
        requireEntity(id);
        fieldMapper.delete(new LambdaQueryWrapper<GovFusionField>().eq(GovFusionField::getEntityId, id));
        physicalMapper.delete(new LambdaQueryWrapper<GovFusionPhysical>().eq(GovFusionPhysical::getEntityId, id));
        relationMapper.delete(new LambdaQueryWrapper<GovFusionRelation>()
                .eq(GovFusionRelation::getFromEntityId, id)
                .or().eq(GovFusionRelation::getToEntityId, id));
        entityMapper.deleteById(id);
    }

    public List<GovFusionField> listFields(Long entityId) {
        return fieldMapper.selectList(new LambdaQueryWrapper<GovFusionField>()
                .eq(GovFusionField::getEntityId, entityId)
                .orderByAsc(GovFusionField::getSortOrder)
                .orderByAsc(GovFusionField::getId));
    }

    @Transactional
    public Long createField(UserPrincipal operator, Map<String, Object> body) {
        Long entityId = longVal(body.get("entityId"));
        String code = str(body.get("fieldCode"));
        String name = str(body.get("fieldName"));
        if (entityId == null || code == null || name == null) {
            throw new BusinessException(400, "entityId/fieldCode/fieldName 必填");
        }
        requireEntity(entityId);
        GovFusionField f = new GovFusionField();
        f.setEntityId(entityId);
        f.setFieldCode(code);
        f.setFieldName(name);
        f.setDataType(str(body.get("dataType"), "VARCHAR"));
        f.setNullableFlag(boolInt(body.get("nullableFlag"), 1));
        f.setPkFlag(boolInt(body.get("pkFlag"), 0));
        f.setDescription(str(body.get("description")));
        f.setSortOrder(intVal(body.get("sortOrder"), 0));
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        fieldMapper.insert(f);
        return f.getId();
    }

    @Transactional
    public void updateField(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovFusionField f = requireField(id);
        if (body.containsKey("fieldName")) f.setFieldName(str(body.get("fieldName")));
        if (body.containsKey("dataType")) f.setDataType(str(body.get("dataType")));
        if (body.containsKey("nullableFlag")) f.setNullableFlag(boolInt(body.get("nullableFlag"), f.getNullableFlag()));
        if (body.containsKey("pkFlag")) f.setPkFlag(boolInt(body.get("pkFlag"), f.getPkFlag()));
        if (body.containsKey("description")) f.setDescription(str(body.get("description")));
        if (body.containsKey("sortOrder")) f.setSortOrder(intVal(body.get("sortOrder"), f.getSortOrder()));
        f.setUpdatedAt(LocalDateTime.now());
        fieldMapper.updateById(f);
    }

    @Transactional
    public void deleteField(UserPrincipal operator, Long id) {
        fieldMapper.deleteById(id);
    }

    public List<GovFusionRelation> listRelations(Long domainId) {
        return relationMapper.selectList(new LambdaQueryWrapper<GovFusionRelation>()
                .eq(GovFusionRelation::getDomainId, domainId)
                .orderByAsc(GovFusionRelation::getId));
    }

    @Transactional
    public Long createRelation(UserPrincipal operator, Map<String, Object> body) {
        Long domainId = longVal(body.get("domainId"));
        String code = str(body.get("relationCode"));
        String name = str(body.get("relationName"));
        Long fromId = longVal(body.get("fromEntityId"));
        Long toId = longVal(body.get("toEntityId"));
        if (domainId == null || code == null || name == null || fromId == null || toId == null) {
            throw new BusinessException(400, "domainId/relationCode/relationName/fromEntityId/toEntityId 必填");
        }
        requireDomain(domainId);
        GovFusionRelation r = new GovFusionRelation();
        r.setDomainId(domainId);
        r.setRelationCode(code);
        r.setRelationName(name);
        r.setFromEntityId(fromId);
        r.setToEntityId(toId);
        r.setRelationType(str(body.get("relationType"), "ONE_TO_MANY"));
        r.setDescription(str(body.get("description")));
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        relationMapper.insert(r);
        return r.getId();
    }

    @Transactional
    public void deleteRelation(UserPrincipal operator, Long id) {
        relationMapper.deleteById(id);
    }

    public List<GovFusionPhysical> listPhysical(Long entityId) {
        return physicalMapper.selectList(new LambdaQueryWrapper<GovFusionPhysical>()
                .eq(GovFusionPhysical::getEntityId, entityId)
                .orderByAsc(GovFusionPhysical::getId));
    }

    @Transactional
    public Long createPhysical(UserPrincipal operator, Map<String, Object> body) {
        Long entityId = longVal(body.get("entityId"));
        String code = str(body.get("physicalCode"));
        String tableName = str(body.get("tableName"));
        Long datasourceId = longVal(body.get("datasourceId"));
        if (entityId == null || code == null || tableName == null) {
            throw new BusinessException(400, "entityId/physicalCode/tableName 必填");
        }
        if (datasourceId == null) {
            throw new BusinessException(400, "请选择来源库（平台分层或登记源）");
        }
        requireValidTableName(tableName);
        requireEntity(entityId);
        GovFusionPhysical p = new GovFusionPhysical();
        p.setEntityId(entityId);
        p.setPhysicalCode(code);
        p.setTableName(tableName);
        p.setDatasourceId(datasourceId);
        p.setDdlSql(str(body.get("ddlSql")));
        p.setStatus(str(body.get("status"), "DRAFT"));
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        physicalMapper.insert(p);
        return p.getId();
    }

    @Transactional
    public void updatePhysical(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovFusionPhysical p = requirePhysical(id);
        if (body.containsKey("tableName")) {
            String tableName = str(body.get("tableName"));
            requireValidTableName(tableName);
            p.setTableName(tableName);
        }
        if (body.containsKey("datasourceId")) p.setDatasourceId(longVal(body.get("datasourceId")));
        if (body.containsKey("ddlSql")) p.setDdlSql(str(body.get("ddlSql")));
        if (body.containsKey("status")) p.setStatus(str(body.get("status")));
        p.setUpdatedAt(LocalDateTime.now());
        physicalMapper.updateById(p);
    }

    @Transactional
    public void deletePhysical(UserPrincipal operator, Long id) {
        physicalMapper.deleteById(id);
    }

    /** 从登记/分层库探列，导入为逻辑字段（已存在编码则跳过）。 */
    @Transactional
    public Map<String, Object> importFieldsFromTable(UserPrincipal operator, Long entityId, Map<String, Object> body) {
        requireEntity(entityId);
        Long datasourceId = longVal(body.get("datasourceId"));
        String tableName = str(body.get("tableName"));
        if (datasourceId == null || tableName == null) {
            throw new BusinessException(400, "datasourceId/tableName 必填");
        }
        requireValidTableName(tableName);
        List<Map<String, String>> columns;
        try (Connection conn = openConnection(datasourceId)) {
            columns = loadColumns(conn, resolveCatalog(datasourceId, conn), tableName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "探列失败: " + e.getMessage());
        }
        int imported = 0;
        int skipped = 0;
        int order = fieldMapper.selectCount(new LambdaQueryWrapper<GovFusionField>()
                .eq(GovFusionField::getEntityId, entityId)).intValue();
        for (Map<String, String> col : columns) {
            String code = col.get("columnName");
            Long exists = fieldMapper.selectCount(new LambdaQueryWrapper<GovFusionField>()
                    .eq(GovFusionField::getEntityId, entityId)
                    .eq(GovFusionField::getFieldCode, code));
            if (exists != null && exists > 0) {
                skipped++;
                continue;
            }
            GovFusionField f = new GovFusionField();
            f.setEntityId(entityId);
            f.setFieldCode(code);
            f.setFieldName(col.get("comment") != null && !col.get("comment").isBlank()
                    ? col.get("comment") : code);
            f.setDataType(mapSqlType(col.get("dataType")));
            f.setNullableFlag("YES".equalsIgnoreCase(col.get("nullable")) ? 1 : 0);
            f.setPkFlag(0);
            f.setSortOrder(++order);
            f.setCreatedAt(LocalDateTime.now());
            f.setUpdatedAt(LocalDateTime.now());
            fieldMapper.insert(f);
            imported++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("imported", imported);
        out.put("skipped", skipped);
        out.put("tableName", tableName);
        return out;
    }

    /** 预览物理映射表数据（DWS/ADS/DWD 等真实层，只读）。 */
    public Map<String, Object> previewPhysical(Long physicalId) {
        GovFusionPhysical p = requirePhysical(physicalId);
        if (p.getDatasourceId() == null) {
            throw new BusinessException(400, "物理映射未绑定来源库");
        }
        requireValidTableName(p.getTableName());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("physicalId", physicalId);
        out.put("tableName", p.getTableName());
        out.put("datasourceId", p.getDatasourceId());
        try (Connection conn = openConnection(p.getDatasourceId());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `" + p.getTableName() + "` LIMIT " + PREVIEW_LIMIT)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next() && rows.size() < PREVIEW_LIMIT) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            out.put("rows", rows);
            out.put("rowCount", rows.size());
            out.put("status", "SUCCESS");
            out.put("message", "预览 " + rows.size() + " 行（最多 " + PREVIEW_LIMIT + "）");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "预览失败: " + e.getMessage());
        }
        return out;
    }

    private static void requireValidTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new BusinessException(400, "表名不能为空");
        }
        if (!IDENT.matcher(tableName).matches()) {
            throw new BusinessException(400, "表名非法: " + tableName);
        }
        if (PLACEHOLDER_TABLES.contains(tableName.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(400, "禁止使用占位表名，请从登记库/分层库选择实表");
        }
    }

    private Connection openConnection(Long datasourceId) throws Exception {
        if (isPlatformLayerId(datasourceId)) {
            if (platformDataSource == null) {
                throw new BusinessException(500, "平台库数据源不可用");
            }
            Connection conn = platformDataSource.getConnection();
            conn.setCatalog(platformLayerDatabase(datasourceId));
            return conn;
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
        throw new BusinessException(400, "请选择来源库");
    }

    private String resolveCatalog(Long datasourceId, Connection conn) throws Exception {
        if (isPlatformLayerId(datasourceId)) {
            return platformLayerDatabase(datasourceId);
        }
        String cat = conn.getCatalog();
        return cat == null || cat.isBlank() ? DataLayerSupport.CONTROL : cat;
    }

    private List<Map<String, String>> loadColumns(Connection conn, String schema, String tableName) throws Exception {
        List<Map<String, String>> cols = new ArrayList<>();
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> col = new LinkedHashMap<>();
                    col.put("columnName", rs.getString("COLUMN_NAME"));
                    col.put("dataType", rs.getString("DATA_TYPE"));
                    col.put("nullable", rs.getString("IS_NULLABLE"));
                    col.put("comment", rs.getString("COLUMN_COMMENT"));
                    cols.add(col);
                }
            }
        }
        if (cols.isEmpty()) {
            throw new BusinessException(404, "表不存在或无字段: " + schema + "." + tableName);
        }
        return cols;
    }

    private static String mapSqlType(String sqlType) {
        if (sqlType == null) return "VARCHAR";
        String t = sqlType.toLowerCase(Locale.ROOT);
        if (t.contains("int")) return "BIGINT";
        if (t.contains("decimal") || t.contains("numeric") || t.contains("double") || t.contains("float")) return "DECIMAL";
        if (t.contains("date") || t.contains("time")) return "DATETIME";
        if (t.contains("text") || t.contains("blob")) return "TEXT";
        return "VARCHAR";
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

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        String s = n.get(field).asText();
        return s == null || s.isBlank() ? null : s;
    }

    private GovFusionDomain requireDomain(Long id) {
        GovFusionDomain d = domainMapper.selectById(id);
        if (d == null) throw new BusinessException(404, "业务域不存在: " + id);
        return d;
    }

    private GovFusionLogicEntity requireEntity(Long id) {
        GovFusionLogicEntity e = entityMapper.selectById(id);
        if (e == null) throw new BusinessException(404, "逻辑实体不存在: " + id);
        return e;
    }

    private GovFusionField requireField(Long id) {
        GovFusionField f = fieldMapper.selectById(id);
        if (f == null) throw new BusinessException(404, "字段不存在: " + id);
        return f;
    }

    private GovFusionPhysical requirePhysical(Long id) {
        GovFusionPhysical p = physicalMapper.selectById(id);
        if (p == null) throw new BusinessException(404, "物理映射不存在: " + id);
        return p;
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

    private static int intVal(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private static int boolInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof Number n) return n.intValue() != 0 ? 1 : 0;
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v)) ? 1 : 0;
    }
}
