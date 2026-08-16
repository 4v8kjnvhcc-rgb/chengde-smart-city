package com.chengde.smartcity.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.IndArea;
import com.chengde.smartcity.analysis.entity.IndField;
import com.chengde.smartcity.analysis.entity.IndGroup;
import com.chengde.smartcity.analysis.entity.IndSql;
import com.chengde.smartcity.analysis.mapper.IndAreaMapper;
import com.chengde.smartcity.analysis.mapper.IndFieldMapper;
import com.chengde.smartcity.analysis.mapper.IndGroupMapper;
import com.chengde.smartcity.analysis.mapper.IndSqlMapper;
import com.chengde.smartcity.analysis.support.IndicatorOwnerCodes;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndicatorLedgerService {

    private static final Pattern IND_DB_NAME = Pattern.compile("^ind_[a-z0-9]+(?:_[a-z0-9]+)*$");
    private static final Set<String> BIZ_OWNER_DOMAINS = Set.of("population", "legal", "macro", "key");

    private final IndAreaMapper areaMapper;
    private final IndGroupMapper groupMapper;
    private final IndFieldMapper fieldMapper;
    private final IndSqlMapper sqlMapper;
    private final IndicatorTaskService indicatorTaskService;
    private final AuditService auditService;

    public IndicatorLedgerService(IndAreaMapper areaMapper,
                                  IndGroupMapper groupMapper,
                                  IndFieldMapper fieldMapper,
                                  IndSqlMapper sqlMapper,
                                  @Lazy IndicatorTaskService indicatorTaskService,
                                  AuditService auditService) {
        this.areaMapper = areaMapper;
        this.groupMapper = groupMapper;
        this.fieldMapper = fieldMapper;
        this.sqlMapper = sqlMapper;
        this.indicatorTaskService = indicatorTaskService;
        this.auditService = auditService;
    }

    public static boolean isUnified(String domain) {
        return "all".equals(domain) || "gov".equals(domain);
    }

    public List<IndArea> listDomains(String domain, String domainName, String domainDbName) {
        String d = norm(domain);
        List<IndArea> all = areaMapper.selectList(new LambdaQueryWrapper<IndArea>().orderByAsc(IndArea::getName));
        List<IndArea> out = new ArrayList<>();
        for (IndArea row : all) {
            decorate(row);
            if (!IndicatorOwnerCodes.matchesOwner(d, row.getName(), row.getDbSchema())) continue;
            if (domainName != null && !domainName.isBlank()
                    && (row.getName() == null || !row.getName().contains(domainName.trim()))) continue;
            if (domainDbName != null && !domainDbName.isBlank()
                    && (row.getDbSchema() == null || !row.getDbSchema().contains(domainDbName.trim()))) continue;
            out.add(row);
        }
        return out;
    }

    @Transactional
    public String createDomain(UserPrincipal operator, String domain, Map<String, Object> body) {
        String d = norm(domain);
        if (isUnified(d)) {
            String owner = str(body.get("ownerDomainCode"), null);
            if (owner == null || !BIZ_OWNER_DOMAINS.contains(owner.trim().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(400, "请选择所属业务支撑系统（population|legal|macro|key）");
            }
            d = owner.trim().toLowerCase(Locale.ROOT);
        }
        String name = required(body.get("domainName"), "domainName").toString().trim();
        String dbName = required(body.get("domainDbName"), "domainDbName").toString().trim().toLowerCase(Locale.ROOT);
        validateIndDbName(dbName);
        Long dup = areaMapper.selectCount(new LambdaQueryWrapper<IndArea>().eq(IndArea::getDbSchema, dbName));
        if (dup != null && dup > 0) throw new BusinessException(400, "指标域库名已存在");
        IndArea row = new IndArea();
        row.setUuid(UUID.randomUUID().toString());
        row.setName(name);
        row.setDbSchema(dbName);
        row.setRemark(str(body.get("remark"), null));
        areaMapper.insert(row);
        decorate(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_DOMAIN_CREATE", d, dbName, name);
        return row.getUuid();
    }

    @Transactional
    public void updateDomain(UserPrincipal operator, String id, Map<String, Object> body) {
        IndArea row = requireArea(id);
        if (body.get("domainName") != null) {
            String name = String.valueOf(body.get("domainName")).trim();
            if (name.isEmpty()) throw new BusinessException(400, "domainName required");
            row.setName(name);
        }
        if (body.get("domainDbName") != null) {
            String dbName = String.valueOf(body.get("domainDbName")).trim().toLowerCase(Locale.ROOT);
            validateIndDbName(dbName);
            Long dup = areaMapper.selectCount(new LambdaQueryWrapper<IndArea>()
                    .eq(IndArea::getDbSchema, dbName)
                    .ne(IndArea::getUuid, id));
            if (dup != null && dup > 0) throw new BusinessException(400, "指标域库名已存在");
            row.setDbSchema(dbName);
        }
        if (body.containsKey("remark")) row.setRemark(str(body.get("remark"), null));
        areaMapper.updateById(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_DOMAIN_UPDATE", row.getOwnerDomainCode(), row.getDbSchema(), row.getName());
    }

    @Transactional
    public void deleteDomain(UserPrincipal operator, String id) {
        IndArea row = requireArea(id);
        long groupCnt = groupMapper.selectCount(new LambdaQueryWrapper<IndGroup>()
                .eq(IndGroup::getAreaId, id)
                .ne(IndGroup::getPublishStatus, 2));
        if (groupCnt > 0) {
            throw new BusinessException(400, "该指标域下仍有指标组，请先删除指标组后再删除指标域");
        }
        areaMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_DOMAIN_DELETE", row.getOwnerDomainCode(), row.getDbSchema(), "DELETED");
    }

    @Transactional
    public Map<String, Object> publishDomain(UserPrincipal operator, String domainId, Map<String, Object> body) {
        requireArea(domainId);
        Map<String, Object> opts = body != null ? body : Map.of();
        String execCycle = str(opts.get("execCycle"), null);
        String cronExpr = str(opts.get("cronExpr"), null);
        if ((cronExpr == null || cronExpr.isBlank()) && (execCycle == null || execCycle.isBlank())) {
            throw new BusinessException(400, "请选择执行周期");
        }
        String taskNameOverride = str(opts.get("taskName"), null);
        String remark = str(opts.get("remark"), null);
        String executorAddress = str(opts.get("executorAddress"), "DEFAULT");
        String cycleName = str(opts.get("cycleName"), null);
        List<IndGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<IndGroup>()
                .eq(IndGroup::getAreaId, domainId)
                .ne(IndGroup::getPublishStatus, 2));
        int ok = 0;
        int skip = 0;
        List<String> messages = new ArrayList<>();
        String cycleKey = (execCycle != null && !execCycle.isBlank())
                ? execCycle.trim()
                : (cycleName != null && !cycleName.isBlank() ? cycleName.trim() : "CUSTOM");
        for (IndGroup g : groups) {
            decorateGroup(g);
            long cnt = fieldMapper.selectCount(new LambdaQueryWrapper<IndField>().eq(IndField::getGroupId, g.getUuid()));
            if (cnt == 0) {
                skip++;
                messages.add(g.getName() + "：无指标，已跳过");
                continue;
            }
            String taskName = (taskNameOverride != null && !taskNameOverride.isBlank() && groups.size() == 1)
                    ? taskNameOverride.trim() : g.getName();
            if (taskNameOverride != null && !taskNameOverride.isBlank() && groups.size() > 1) {
                taskName = taskNameOverride.trim() + "-" + g.getName();
            }
            publishGroup(operator, g.getUuid(), taskName, cycleKey, cronExpr, remark, executorAddress);
            ok++;
        }
        if (ok == 0 && groups.isEmpty()) throw new BusinessException(400, "该指标域下暂无指标组可发布");
        if (ok == 0) throw new BusinessException(400, "没有可发布的指标组（请先在组内新增指标）");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("domainId", domainId);
        out.put("published", ok);
        out.put("skipped", skip);
        out.put("messages", messages);
        out.put("execCycle", cycleKey);
        out.put("cronExpr", cronExpr);
        return out;
    }

    public List<IndGroup> listGroups(String domain, String indicatorDomainId,
                                    String groupName, String targetTable, String groupCategory) {
        String d = norm(domain);
        LambdaQueryWrapper<IndGroup> q = new LambdaQueryWrapper<IndGroup>()
                .ne(IndGroup::getPublishStatus, 2)
                .orderByDesc(IndGroup::getUuid);
        if (indicatorDomainId != null && !indicatorDomainId.isBlank()) {
            q.eq(IndGroup::getAreaId, indicatorDomainId.trim());
        }
        if (groupName != null && !groupName.isBlank()) q.like(IndGroup::getName, groupName.trim());
        if (targetTable != null && !targetTable.isBlank()) q.like(IndGroup::getTableName, targetTable.trim());
        if (groupCategory != null && !groupCategory.isBlank()) {
            q.eq(IndGroup::getType, IndGroup.categoryToType(groupCategory));
        }
        List<IndGroup> list = groupMapper.selectList(q);
        List<IndGroup> out = new ArrayList<>();
        for (IndGroup g : list) {
            decorateGroup(g);
            IndArea a = g.getAreaId() == null ? null : areaMapper.selectById(g.getAreaId());
            if (a != null && !IndicatorOwnerCodes.matchesOwner(d, a.getName(), a.getDbSchema())) continue;
            if (a == null && !isUnified(d)) continue;
            out.add(g);
        }
        return out;
    }

    public IndGroup getGroup(String id) {
        IndGroup g = groupMapper.selectById(id);
        if (g == null || (g.getPublishStatus() != null && g.getPublishStatus() == 2)) {
            throw new BusinessException(404, "指标组不存在");
        }
        decorateGroup(g);
        return g;
    }

    @Transactional
    public String createGroup(UserPrincipal operator, String domain, Map<String, Object> body) {
        String d = norm(domain);
        String domainId = strId(required(body.get("indicatorDomainId"), "indicatorDomainId"));
        IndArea indDomain = requireAreaForOwner(domainId, d);
        String name = required(body.get("groupName"), "groupName").toString().trim();
        String table = required(body.get("targetTable"), "targetTable").toString().trim().toLowerCase(Locale.ROOT);
        validateIndDbName(table);
        ensureUniqueGroupTable(table, null);
        IndGroup g = new IndGroup();
        g.setUuid(UUID.randomUUID().toString());
        g.setAreaId(indDomain.getUuid());
        g.setName(name);
        g.setTableName(table);
        g.setGroupCategory(str(body.get("groupCategory"), "UNIT"));
        g.setModelMethod(str(body.get("modelMethod"), "SQL"));
        g.setRemark(str(body.get("description"), null));
        g.setPublishStatus(0);
        groupMapper.insert(g);
        decorateGroup(g);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_GROUP_CREATE", g.getOwnerDomainCode(), table, name);
        return g.getUuid();
    }

    @Transactional
    public void updateGroup(UserPrincipal operator, String id, Map<String, Object> body) {
        IndGroup g = getGroup(id);
        if (body.get("groupName") != null) {
            String name = String.valueOf(body.get("groupName")).trim();
            if (name.isEmpty()) throw new BusinessException(400, "groupName required");
            g.setName(name);
        }
        if (body.get("targetTable") != null) {
            String table = String.valueOf(body.get("targetTable")).trim().toLowerCase(Locale.ROOT);
            validateIndDbName(table);
            ensureUniqueGroupTable(table, id);
            g.setTableName(table);
        }
        if (body.get("groupCategory") != null) {
            g.setGroupCategory(String.valueOf(body.get("groupCategory")));
        }
        if (body.containsKey("description")) g.setRemark(str(body.get("description"), null));
        if (body.get("indicatorDomainId") != null) {
            IndArea indDomain = requireAreaForOwner(strId(body.get("indicatorDomainId")), g.getOwnerDomainCode());
            g.setAreaId(indDomain.getUuid());
        }
        groupMapper.updateById(g);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_GROUP_UPDATE", g.getOwnerDomainCode(), g.getTableName(), g.getName());
    }

    @Transactional
    public void deleteGroup(UserPrincipal operator, String id) {
        IndGroup g = getGroup(id);
        g.setPublishStatus(2);
        groupMapper.updateById(g);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_GROUP_DELETE", g.getOwnerDomainCode(), g.getTableName(), "OFFLINE");
    }

    @Transactional
    public void publishGroup(UserPrincipal operator, String id, Map<String, Object> body) {
        Map<String, Object> opts = body != null ? body : Map.of();
        publishGroup(operator, id,
                str(opts.get("taskName"), null),
                str(opts.get("execCycle"), null),
                str(opts.get("cronExpr"), null),
                str(opts.get("remark"), null),
                str(opts.get("executorAddress"), "DEFAULT"));
    }

    @Transactional
    public void publishGroup(UserPrincipal operator, String id, String taskName,
                             String execCycle, String cronExpr, String remark, String executorAddress) {
        IndGroup g = getGroup(id);
        long cnt = fieldMapper.selectCount(new LambdaQueryWrapper<IndField>().eq(IndField::getGroupId, id));
        if (cnt == 0) throw new BusinessException(400, "请先新增指标后再发布");
        g.setPublishStatus(1);
        g.setPublishTime(LocalDateTime.now());
        g.setPublishBy(operator != null ? operator.getUsername() : "system");
        groupMapper.updateById(g);
        indicatorTaskService.ensureFromPublishedGroup(operator, g, taskName, execCycle, cronExpr, remark, executorAddress);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_GROUP_PUBLISH", g.getOwnerDomainCode(), g.getTableName(), g.getName());
    }

    public List<IndField> listFieldsByGroup(String groupId) {
        getGroup(groupId);
        List<IndField> list = fieldMapper.selectList(new LambdaQueryWrapper<IndField>()
                .eq(IndField::getGroupId, groupId)
                .orderByAsc(IndField::getFieldPosition)
                .orderByAsc(IndField::getUuid));
        for (IndField f : list) {
            if (f.getQueryNo() == null) f.setQueryNo(f.getSqlSerial());
            if (f.getResultField() == null || f.getResultField().isBlank()) f.setRsColumn(f.getFieldName());
        }
        return list;
    }

    public Map<String, Object> latestGroupSql(String groupId) {
        getGroup(groupId);
        List<IndSql> list = sqlMapper.selectList(new LambdaQueryWrapper<IndSql>()
                .eq(IndSql::getGroupId, groupId)
                .orderByDesc(IndSql::getUuid)
                .last("LIMIT 1"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sqlText", "");
        out.put("datasourceKey", "");
        out.put("datasourceName", "");
        out.put("timeoutSec", 60);
        if (list.isEmpty()) return out;
        IndSql q = list.get(0);
        out.put("sqlText", q.getContent() == null ? "" : q.getContent());
        out.put("datasourceKey", q.getDbMarkId() == null ? "" : q.getDbMarkId());
        out.put("datasourceName", q.getDbName() == null ? "" : q.getDbName());
        out.put("timeoutSec", q.getTimeout() == null ? 60 : q.getTimeout());
        return out;
    }

    @Transactional
    public String saveGroupSql(UserPrincipal operator, String domain, Map<String, Object> body,
                               List<Map<String, Object>> fields, String sql, String dsKey, String dsName, int timeout) {
        String groupId = strId(required(body.get("groupId"), "groupId"));
        IndGroup group = getGroup(groupId);
        String d = norm(domain);
        if (!isUnified(d)) {
            IndArea a = areaMapper.selectById(group.getAreaId());
            if (a == null || !IndicatorOwnerCodes.matchesOwner(d, a.getName(), a.getDbSchema())) {
                throw new BusinessException(400, "指标组不属于当前业务域");
            }
        }
        sqlMapper.delete(new LambdaQueryWrapper<IndSql>().eq(IndSql::getGroupId, groupId));
        fieldMapper.delete(new LambdaQueryWrapper<IndField>().eq(IndField::getGroupId, groupId));

        String slug = group.getTableName() == null ? "q" : group.getTableName().replaceFirst("^ind_", "");
        IndSql q = new IndSql();
        q.setUuid(UUID.randomUUID().toString());
        q.setGroupId(groupId);
        q.setSerial("undefined.ind_" + slug + "_sql0");
        q.setDbMarkId(dsKey);
        q.setDbName(dsName);
        q.setContent(sql);
        q.setTimeout(timeout);
        sqlMapper.insert(q);

        int i = 0;
        for (Map<String, Object> f : fields) {
            String resultField = required(f.get("resultField"), "resultField").toString();
            IndField ind = new IndField();
            ind.setUuid(UUID.randomUUID().toString());
            ind.setGroupId(groupId);
            ind.setSqlId(q.getUuid());
            ind.setSqlSerial(q.getSerial());
            ind.setName(str(f.get("indicatorName"), resultField));
            ind.setTag(str(f.get("indicatorFlag"), null));
            ind.setFieldName(str(f.get("fieldName"), "ind_" + resultField));
            ind.setFieldType(str(f.get("fieldType"), "字符串"));
            ind.setFieldLength(intVal(f.get("fieldLength")));
            ind.setFieldPrecision(intVal(f.get("fieldPrecision")));
            ind.setRsColumn(resultField);
            ind.setFieldPosition(++i);
            fieldMapper.insert(ind);
        }
        if (group.getPublishStatus() != null && group.getPublishStatus() == 1) {
            group.setPublishStatus(0);
            groupMapper.updateById(group);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_SQL_CREATE", group.getOwnerDomainCode(), q.getSerial(), "fields=" + fields.size());
        return q.getUuid();
    }

    @Transactional
    public boolean updateField(UserPrincipal operator, String id, Map<String, Object> body) {
        IndField ind = fieldMapper.selectById(id);
        if (ind == null) return false;
        if (body.get("indicatorName") != null) ind.setName(String.valueOf(body.get("indicatorName")));
        if (body.get("fieldName") != null) ind.setFieldName(String.valueOf(body.get("fieldName")));
        if (body.containsKey("indicatorFlag")) ind.setTag(str(body.get("indicatorFlag"), null));
        if (body.containsKey("fieldType")) ind.setFieldType(str(body.get("fieldType"), null));
        if (body.containsKey("resultField")) ind.setRsColumn(str(body.get("resultField"), null));
        fieldMapper.updateById(ind);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_UPDATE", "ind_field", id, ind.getName());
        return true;
    }

    @Transactional
    public boolean deleteField(UserPrincipal operator, String id) {
        IndField ind = fieldMapper.selectById(id);
        if (ind == null) return false;
        fieldMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_IND_DELETE", "ind_field", id, "DELETED");
        return true;
    }

    private void decorate(IndArea row) {
        if (row == null) return;
        row.setOwnerDomainCode(IndicatorOwnerCodes.derive(row.getName(), row.getDbSchema()));
    }

    private void decorateGroup(IndGroup g) {
        if (g == null) return;
        IndArea a = g.getAreaId() == null ? null : areaMapper.selectById(g.getAreaId());
        if (a != null) {
            g.setOwnerDomainCode(IndicatorOwnerCodes.derive(a.getName(), a.getDbSchema()));
            g.setIndicatorDomainName(a.getName());
        }
    }

    private IndArea requireArea(String id) {
        if (id == null || id.isBlank()) throw new BusinessException(400, "id required");
        IndArea row = areaMapper.selectById(id.trim());
        if (row == null) throw new BusinessException(404, "指标域不存在");
        decorate(row);
        return row;
    }

    private IndArea requireAreaForOwner(String domainId, String ownerDomain) {
        IndArea indDomain = requireArea(domainId);
        if (ownerDomain != null && !isUnified(ownerDomain)
                && !ownerDomain.equalsIgnoreCase(indDomain.getOwnerDomainCode())) {
            throw new BusinessException(400, "指标域不属于当前业务域");
        }
        return indDomain;
    }

    private void ensureUniqueGroupTable(String table, String excludeId) {
        LambdaQueryWrapper<IndGroup> q = new LambdaQueryWrapper<IndGroup>()
                .eq(IndGroup::getTableName, table)
                .ne(IndGroup::getPublishStatus, 2);
        if (excludeId != null) q.ne(IndGroup::getUuid, excludeId);
        Long dup = groupMapper.selectCount(q);
        if (dup != null && dup > 0) throw new BusinessException(400, "指标组结果表名已存在");
    }

    private static void validateIndDbName(String dbName) {
        if (dbName == null || !IND_DB_NAME.matcher(dbName).matches()) {
            throw new BusinessException(400, "指标域库名须以 ind_ 开头，支持小写字母、数字、下划线，不能以下划线结尾");
        }
    }

    private static String norm(String domain) {
        if (domain == null || domain.isBlank()) throw new BusinessException(400, "domain required");
        return domain.trim().toLowerCase(Locale.ROOT);
    }

    private static String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private static String strId(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) throw new BusinessException(400, field + " required");
        return v;
    }

    private static Integer intVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
