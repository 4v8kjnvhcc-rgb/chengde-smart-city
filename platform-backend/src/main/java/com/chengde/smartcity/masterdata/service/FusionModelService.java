package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
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
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FusionModelService {

    private static final Logger log = LoggerFactory.getLogger(FusionModelService.class);

    private final GovFusionDomainMapper domainMapper;
    private final GovFusionLogicEntityMapper entityMapper;
    private final GovFusionFieldMapper fieldMapper;
    private final GovFusionRelationMapper relationMapper;
    private final GovFusionPhysicalMapper physicalMapper;

    public FusionModelService(GovFusionDomainMapper domainMapper,
                              GovFusionLogicEntityMapper entityMapper,
                              GovFusionFieldMapper fieldMapper,
                              GovFusionRelationMapper relationMapper,
                              GovFusionPhysicalMapper physicalMapper) {
        this.domainMapper = domainMapper;
        this.entityMapper = entityMapper;
        this.fieldMapper = fieldMapper;
        this.relationMapper = relationMapper;
        this.physicalMapper = physicalMapper;
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
        if (entityId == null || code == null || tableName == null) {
            throw new BusinessException(400, "entityId/physicalCode/tableName 必填");
        }
        requireEntity(entityId);
        GovFusionPhysical p = new GovFusionPhysical();
        p.setEntityId(entityId);
        p.setPhysicalCode(code);
        p.setTableName(tableName);
        p.setDatasourceId(longVal(body.get("datasourceId")));
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
        if (body.containsKey("tableName")) p.setTableName(str(body.get("tableName")));
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
