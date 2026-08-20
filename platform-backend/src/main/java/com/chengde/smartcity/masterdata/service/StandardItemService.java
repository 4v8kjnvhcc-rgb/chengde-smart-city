package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.masterdata.entity.GovNamingStandard;
import com.chengde.smartcity.masterdata.entity.GovStandardCodebook;
import com.chengde.smartcity.masterdata.entity.GovStandardItem;
import com.chengde.smartcity.masterdata.entity.GovStandardItemLink;
import com.chengde.smartcity.masterdata.entity.GovStandardItemVersion;
import com.chengde.smartcity.masterdata.entity.GovStandardMapping;
import com.chengde.smartcity.masterdata.mapper.GovNamingStandardMapper;
import com.chengde.smartcity.masterdata.mapper.GovStandardCodebookMapper;
import com.chengde.smartcity.masterdata.mapper.GovStandardItemLinkMapper;
import com.chengde.smartcity.masterdata.mapper.GovStandardItemMapper;
import com.chengde.smartcity.masterdata.mapper.GovStandardItemVersionMapper;
import com.chengde.smartcity.masterdata.mapper.GovStandardMappingMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StandardItemService {

    private static final Logger log = LoggerFactory.getLogger(StandardItemService.class);
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final GovStandardItemMapper itemMapper;
    private final GovStandardItemVersionMapper versionMapper;
    private final GovStandardCodebookMapper codebookMapper;
    private final GovNamingStandardMapper namingMapper;
    private final GovStandardMappingMapper mappingMapper;
    private final GovStandardItemLinkMapper itemLinkMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final DataSource platformDataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StandardItemService(GovStandardItemMapper itemMapper,
                               GovStandardItemVersionMapper versionMapper,
                               GovStandardCodebookMapper codebookMapper,
                               GovNamingStandardMapper namingMapper,
                               GovStandardMappingMapper mappingMapper,
                               GovStandardItemLinkMapper itemLinkMapper,
                               IngDataSourceMapper dataSourceMapper,
                               @Autowired(required = false) DataSource platformDataSource) {
        this.itemMapper = itemMapper;
        this.versionMapper = versionMapper;
        this.codebookMapper = codebookMapper;
        this.namingMapper = namingMapper;
        this.mappingMapper = mappingMapper;
        this.itemLinkMapper = itemLinkMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.platformDataSource = platformDataSource;
    }

    public List<GovStandardItem> list(String itemType, String publishStatus, String keyword) {
        LambdaQueryWrapper<GovStandardItem> q = new LambdaQueryWrapper<GovStandardItem>()
                .orderByDesc(GovStandardItem::getId);
        if (itemType != null && !itemType.isBlank()) {
            q.eq(GovStandardItem::getItemType, itemType);
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            q.eq(GovStandardItem::getPublishStatus, publishStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(GovStandardItem::getItemCode, keyword)
                    .or().like(GovStandardItem::getItemName, keyword)
                    .or().like(GovStandardItem::getBusinessDefinition, keyword));
        }
        return itemMapper.selectList(q);
    }

    public GovStandardItem get(Long id) {
        return require(id);
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        GovStandardItem item = new GovStandardItem();
        applyBody(item, body, true);
        item.setPublishStatus("DRAFT");
        item.setVersionNo(1);
        item.setStatus(str(body.get("status"), "ACTIVE"));
        if (operator != null) {
            item.setCreatedBy(operator.getUsername());
        }
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.insert(item);
        return item.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovStandardItem item = require(id);
        if ("PUBLISHED".equalsIgnoreCase(item.getPublishStatus())) {
            throw new BusinessException(400, "已发布不可编辑，请先下线");
        }
        applyBody(item, body, false);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        GovStandardItem item = require(id);
        String st = item.getPublishStatus() == null ? "" : item.getPublishStatus();
        if ("PUBLISHED".equalsIgnoreCase(st)) {
            throw new BusinessException(400, "已发布不可直接删除，请先下线");
        }
        codebookMapper.delete(new LambdaQueryWrapper<GovStandardCodebook>()
                .eq(GovStandardCodebook::getStandardItemId, id));
        mappingMapper.delete(new LambdaQueryWrapper<GovStandardMapping>()
                .eq(GovStandardMapping::getStandardItemId, id));
        itemMapper.deleteById(id);
    }

    @Transactional
    public GovStandardItem publish(UserPrincipal operator, Long id) {
        GovStandardItem item = require(id);
        if ("PUBLISHED".equalsIgnoreCase(item.getPublishStatus())) {
            throw new BusinessException(400, "当前已是发布状态");
        }
        long versionCount = versionMapper.selectCount(new LambdaQueryWrapper<GovStandardItemVersion>()
                .eq(GovStandardItemVersion::getStandardItemId, id));
        int nextVersion;
        if (versionCount == 0) {
            nextVersion = item.getVersionNo() == null || item.getVersionNo() < 1 ? 1 : item.getVersionNo();
        } else {
            nextVersion = (item.getVersionNo() == null ? 1 : item.getVersionNo()) + 1;
        }
        item.setVersionNo(nextVersion);
        item.setPublishStatus("PUBLISHED");
        item.setPublishedBy(operator != null ? operator.getUsername() : null);
        item.setPublishedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);

        GovStandardItemVersion ver = new GovStandardItemVersion();
        ver.setStandardItemId(item.getId());
        ver.setVersionNo(nextVersion);
        ver.setSnapshotJson(toSnapshotJson(item));
        ver.setChangeSummary("发布数据元 v" + nextVersion);
        ver.setPublishedBy(item.getPublishedBy());
        ver.setPublishedAt(item.getPublishedAt());
        versionMapper.insert(ver);
        log.info("standard item {} published as v{}", id, nextVersion);
        return item;
    }

    @Transactional
    public GovStandardItem offline(UserPrincipal operator, Long id) {
        GovStandardItem item = require(id);
        if (!"PUBLISHED".equalsIgnoreCase(item.getPublishStatus())) {
            throw new BusinessException(400, "仅已发布状态可下线");
        }
        item.setPublishStatus("OFFLINE");
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return item;
    }

    public List<GovStandardItemVersion> listVersions(Long id) {
        require(id);
        return versionMapper.selectList(new LambdaQueryWrapper<GovStandardItemVersion>()
                .eq(GovStandardItemVersion::getStandardItemId, id)
                .orderByDesc(GovStandardItemVersion::getVersionNo));
    }

    public GovStandardItemVersion getVersion(Long id, Integer versionNo) {
        require(id);
        GovStandardItemVersion ver = versionMapper.selectOne(new LambdaQueryWrapper<GovStandardItemVersion>()
                .eq(GovStandardItemVersion::getStandardItemId, id)
                .eq(GovStandardItemVersion::getVersionNo, versionNo));
        if (ver == null) {
            throw new BusinessException(404, "版本不存在: " + versionNo);
        }
        return ver;
    }

    // ---------- A3 codebook ----------

    public List<GovStandardCodebook> listCodebook(Long itemId) {
        require(itemId);
        return codebookMapper.selectList(new LambdaQueryWrapper<GovStandardCodebook>()
                .eq(GovStandardCodebook::getStandardItemId, itemId)
                .orderByAsc(GovStandardCodebook::getSortOrder)
                .orderByAsc(GovStandardCodebook::getId));
    }

    @Transactional
    public Long createCodebook(UserPrincipal operator, Long itemId, Map<String, Object> body) {
        require(itemId);
        String codeValue = str(body.get("codeValue"), null);
        String codeName = str(body.get("codeName"), null);
        if (codeValue == null || codeName == null) {
            throw new BusinessException(400, "codeValue/codeName 不能为空");
        }
        GovStandardCodebook row = new GovStandardCodebook();
        row.setStandardItemId(itemId);
        row.setCodeValue(codeValue);
        row.setCodeName(codeName);
        row.setCodeDesc(str(body.get("codeDesc"), null));
        row.setSortOrder(toInt(body.get("sortOrder"), 0));
        codebookMapper.insert(row);
        return row.getId();
    }

    @Transactional
    public void updateCodebook(UserPrincipal operator, Long codeId, Map<String, Object> body) {
        GovStandardCodebook row = codebookMapper.selectById(codeId);
        if (row == null) {
            throw new BusinessException(404, "码表项不存在: " + codeId);
        }
        if (body.containsKey("codeValue")) {
            String v = str(body.get("codeValue"), null);
            if (v == null) throw new BusinessException(400, "codeValue 不能为空");
            row.setCodeValue(v);
        }
        if (body.containsKey("codeName")) {
            String v = str(body.get("codeName"), null);
            if (v == null) throw new BusinessException(400, "codeName 不能为空");
            row.setCodeName(v);
        }
        if (body.containsKey("codeDesc")) {
            row.setCodeDesc(str(body.get("codeDesc"), null));
        }
        if (body.containsKey("sortOrder")) {
            row.setSortOrder(toInt(body.get("sortOrder"), row.getSortOrder() == null ? 0 : row.getSortOrder()));
        }
        codebookMapper.updateById(row);
    }

    @Transactional
    public void deleteCodebook(UserPrincipal operator, Long codeId) {
        GovStandardCodebook row = codebookMapper.selectById(codeId);
        if (row == null) {
            throw new BusinessException(404, "码表项不存在: " + codeId);
        }
        codebookMapper.deleteById(codeId);
    }

    @Transactional
    public Map<String, Object> importCodebook(UserPrincipal operator, Long itemId, Map<String, Object> body) {
        require(itemId);
        boolean replace = Boolean.TRUE.equals(body.get("replace")) || "true".equalsIgnoreCase(String.valueOf(body.get("replace")));
        Object itemsObj = body.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            throw new BusinessException(400, "items 不能为空");
        }
        if (replace) {
            codebookMapper.delete(new LambdaQueryWrapper<GovStandardCodebook>()
                    .eq(GovStandardCodebook::getStandardItemId, itemId));
        }
        int ok = 0;
        int skip = 0;
        int order = 0;
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> m)) {
                skip++;
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) m;
            String codeValue = str(map.get("codeValue"), null);
            String codeName = str(map.get("codeName"), null);
            if (codeValue == null || codeName == null) {
                skip++;
                continue;
            }
            if (!replace) {
                Long exists = codebookMapper.selectCount(new LambdaQueryWrapper<GovStandardCodebook>()
                        .eq(GovStandardCodebook::getStandardItemId, itemId)
                        .eq(GovStandardCodebook::getCodeValue, codeValue));
                if (exists != null && exists > 0) {
                    skip++;
                    continue;
                }
            }
            GovStandardCodebook row = new GovStandardCodebook();
            row.setStandardItemId(itemId);
            row.setCodeValue(codeValue);
            row.setCodeName(codeName);
            row.setCodeDesc(str(map.get("codeDesc"), null));
            row.setSortOrder(toInt(map.get("sortOrder"), order++));
            codebookMapper.insert(row);
            ok++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("imported", ok);
        out.put("skipped", skip);
        out.put("total", listCodebook(itemId).size());
        return out;
    }

    public List<GovStandardCodebook> exportCodebook(Long itemId) {
        return listCodebook(itemId);
    }

    @Transactional
    public Map<String, Object> fromDict(UserPrincipal operator, Long itemId, Map<String, Object> body) {
        require(itemId);
        String table = str(body.get("table"), null);
        String codeColumn = str(body.get("codeColumn"), null);
        String nameColumn = str(body.get("nameColumn"), null);
        String descColumn = str(body.get("descColumn"), null);
        if (table == null || codeColumn == null || nameColumn == null) {
            throw new BusinessException(400, "table/codeColumn/nameColumn 不能为空");
        }
        requireIdent(table, "table");
        requireIdent(codeColumn, "codeColumn");
        requireIdent(nameColumn, "nameColumn");
        if (descColumn != null) {
            requireIdent(descColumn, "descColumn");
        }
        boolean replace = !Boolean.FALSE.equals(body.get("replace"));
        Long datasourceId = toLong(body.get("datasourceId"));

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = openConnection(datasourceId)) {
            String sql = "SELECT `" + codeColumn + "` AS c, `" + nameColumn + "` AS n"
                    + (descColumn != null ? ", `" + descColumn + "` AS d" : "")
                    + " FROM `" + table + "` LIMIT 5000";
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object c = rs.getObject(1);
                    Object n = rs.getObject(2);
                    if (c == null || n == null) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("codeValue", String.valueOf(c).trim());
                    row.put("codeName", String.valueOf(n).trim());
                    if (descColumn != null && rs.getObject(3) != null) {
                        row.put("codeDesc", String.valueOf(rs.getObject(3)).trim());
                    }
                    rows.add(row);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "从字典读取失败: " + e.getMessage());
        }
        Map<String, Object> importBody = new LinkedHashMap<>();
        importBody.put("replace", replace);
        importBody.put("items", rows);
        Map<String, Object> result = importCodebook(operator, itemId, importBody);
        result.put("readCount", rows.size());
        return result;
    }

    // ---------- A4 naming ----------

    public List<GovNamingStandard> listNaming(String namingType, String status) {
        LambdaQueryWrapper<GovNamingStandard> q = new LambdaQueryWrapper<GovNamingStandard>()
                .orderByDesc(GovNamingStandard::getId);
        if (namingType != null && !namingType.isBlank()) {
            q.eq(GovNamingStandard::getNamingType, namingType);
        }
        if (status != null && !status.isBlank()) {
            q.eq(GovNamingStandard::getStatus, status);
        }
        return namingMapper.selectList(q);
    }

    @Transactional
    public Long createNaming(UserPrincipal operator, Map<String, Object> body) {
        String namingName = str(body.get("namingName"), null);
        String standardContent = str(body.get("standardContent"), null);
        if (namingName == null || standardContent == null) {
            throw new BusinessException(400, "namingName/standardContent 不能为空");
        }
        GovNamingStandard row = new GovNamingStandard();
        row.setNamingType(str(body.get("namingType"), "TABLE"));
        row.setNamingName(namingName);
        row.setStandardContent(standardContent);
        row.setDescription(str(body.get("description"), null));
        row.setStatus(str(body.get("status"), "ACTIVE"));
        if (operator != null) {
            row.setCreatedBy(operator.getUsername());
        }
        row.setCreatedAt(LocalDateTime.now());
        namingMapper.insert(row);
        return row.getId();
    }

    @Transactional
    public void updateNaming(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovNamingStandard row = namingMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "命名规范不存在: " + id);
        }
        if (body.containsKey("namingType")) {
            row.setNamingType(str(body.get("namingType"), row.getNamingType()));
        }
        if (body.containsKey("namingName")) {
            String v = str(body.get("namingName"), null);
            if (v == null) throw new BusinessException(400, "namingName 不能为空");
            row.setNamingName(v);
        }
        if (body.containsKey("standardContent")) {
            String v = str(body.get("standardContent"), null);
            if (v == null) throw new BusinessException(400, "standardContent 不能为空");
            row.setStandardContent(v);
        }
        if (body.containsKey("description")) {
            row.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("status")) {
            row.setStatus(str(body.get("status"), row.getStatus()));
        }
        namingMapper.updateById(row);
    }

    @Transactional
    public void deleteNaming(UserPrincipal operator, Long id) {
        GovNamingStandard row = namingMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "命名规范不存在: " + id);
        }
        namingMapper.deleteById(id);
    }

    public Map<String, Object> validateNaming(Map<String, Object> body) {
        String name = str(body.get("name"), null);
        if (name == null) {
            throw new BusinessException(400, "name 不能为空");
        }
        String namingType = str(body.get("namingType"), "TABLE");
        String patternText = str(body.get("pattern"), null);
        Long namingId = toLong(body.get("namingId"));
        if (namingId != null) {
            GovNamingStandard ns = namingMapper.selectById(namingId);
            if (ns == null) {
                throw new BusinessException(404, "命名规范不存在: " + namingId);
            }
            namingType = ns.getNamingType() != null ? ns.getNamingType() : namingType;
            patternText = ns.getStandardContent();
        }
        if (patternText == null || patternText.isBlank()) {
            patternText = defaultNamingPattern(namingType);
        }
        boolean valid;
        String message;
        try {
            Pattern p = Pattern.compile(patternText);
            valid = p.matcher(name).matches();
            message = valid ? "命名符合规范" : "命名不符合规范: " + patternText;
        } catch (PatternSyntaxException e) {
            // 非正则时按前缀/关键词简单包含校验
            valid = name.toLowerCase().matches("^[a-z][a-z0-9_]*$") && name.contains(patternText.toLowerCase().replaceAll("[^a-z0-9_]", ""));
            if (patternText.length() > 40) {
                valid = name.matches(defaultNamingPattern(namingType));
            }
            message = valid ? "命名校验通过（宽松模式）" : "命名不符合默认规则: " + defaultNamingPattern(namingType);
            patternText = defaultNamingPattern(namingType);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valid", valid);
        out.put("name", name);
        out.put("namingType", namingType);
        out.put("pattern", patternText);
        out.put("message", message);
        return out;
    }

    /** 按任务命名规范生成任务名称（优先读取 gov_naming_standard 中 TASK 类型配置） */
    public Map<String, Object> generateTaskName(Map<String, Object> body) {
        String category = str(body.get("taskCategory"), "GJ").trim().toUpperCase(Locale.ROOT);
        String targetTable = sanitizeIdent(str(body.get("targetTable"), ""));
        String dataSourceName = sanitizeIdent(str(body.get("dataSourceName"), ""));
        int sequenceNo = toInt(body.get("sequenceNo"), 1);
        if (sequenceNo < 1) {
            sequenceNo = 1;
        }
        String seq = String.format(Locale.ROOT, "%03d", sequenceNo);

        String template = resolveTaskNamingTemplate(category);
        String taskName = applyTaskNamingTemplate(template, targetTable, dataSourceName, seq);
        if (taskName == null || taskName.isBlank()) {
            throw new BusinessException(400, "无法生成任务名称，请提供目标表或数据源名称");
        }
        if (taskName.length() > 80) {
            taskName = taskName.substring(0, 80);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskName", taskName);
        out.put("taskCategory", category);
        out.put("template", template);
        return out;
    }

    private String resolveTaskNamingTemplate(String category) {
        List<GovNamingStandard> rules = listNaming("TASK", "ACTIVE");
        for (GovNamingStandard rule : rules) {
            if (rule == null || rule.getStandardContent() == null || rule.getStandardContent().isBlank()) {
                continue;
            }
            String name = rule.getNamingName() == null ? "" : rule.getNamingName();
            String content = rule.getStandardContent().trim();
            if (matchesTaskCategory(name, content, category)) {
                return content;
            }
        }
        return defaultTaskNamingTemplate(category);
    }

    private static boolean matchesTaskCategory(String namingName, String standardContent, String category) {
        String upperName = namingName.toUpperCase(Locale.ROOT);
        String upperContent = standardContent.toUpperCase(Locale.ROOT);
        return switch (category) {
            case "GJ" -> upperName.contains("归集") || upperContent.startsWith("T_GJ_");
            case "ZL" -> upperName.contains("治理") || upperContent.startsWith("T_ZL_");
            case "RH" -> upperName.contains("融合") || upperContent.startsWith("T_RH_");
            case "META" -> upperName.contains("元数据") || upperContent.startsWith("T_META_");
            case "Q" -> upperName.contains("质量") || upperContent.startsWith("T_Q_");
            default -> false;
        };
    }

    private static String defaultTaskNamingTemplate(String category) {
        return switch (category) {
            case "GJ" -> "t_gj_{targetTable}";
            case "ZL" -> "t_zl_{targetTable}";
            case "RH" -> "t_rh_{targetTable}";
            case "META" -> "t_meta_{dataSourceName}{seq}";
            case "Q" -> "t_q_{dataSourceName}{seq}";
            default -> "t_gj_{targetTable}";
        };
    }

    private static String applyTaskNamingTemplate(String template, String targetTable,
                                                  String dataSourceName, String seq) {
        String result = template
                .replace("{targetTable}", targetTable == null ? "" : targetTable)
                .replace("{dataSourceName}", dataSourceName == null ? "" : dataSourceName)
                .replace("{seq}", seq == null ? "001" : seq)
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (result.isBlank()) {
            return null;
        }
        return result.toLowerCase(Locale.ROOT);
    }

    private static String sanitizeIdent(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim()
                .replaceAll("[^A-Za-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toLowerCase(Locale.ROOT);
    }

    // ---------- A10 mapping ----------

    public List<GovStandardMapping> listMappings(Long standardItemId, String mappingStatus) {
        LambdaQueryWrapper<GovStandardMapping> q = new LambdaQueryWrapper<GovStandardMapping>()
                .orderByDesc(GovStandardMapping::getId);
        if (standardItemId != null) {
            q.eq(GovStandardMapping::getStandardItemId, standardItemId);
        }
        if (mappingStatus != null && !mappingStatus.isBlank()) {
            q.eq(GovStandardMapping::getMappingStatus, mappingStatus);
        }
        return mappingMapper.selectList(q);
    }

    @Transactional
    public Long createMapping(UserPrincipal operator, Map<String, Object> body) {
        Long itemId = toLong(body.get("standardItemId"));
        if (itemId == null) {
            throw new BusinessException(400, "standardItemId 不能为空");
        }
        require(itemId);
        GovStandardMapping row = new GovStandardMapping();
        row.setStandardItemId(itemId);
        row.setSourceSystem(str(body.get("sourceSystem"), null));
        row.setSourceTable(str(body.get("sourceTable"), null));
        row.setSourceColumn(str(body.get("sourceColumn"), null));
        row.setMappingStatus(str(body.get("mappingStatus"), "MAPPED"));
        row.setMatchScore(toDecimal(body.get("matchScore")));
        row.setRemark(str(body.get("remark"), null));
        if (operator != null) {
            row.setCreatedBy(operator.getUsername());
        }
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        mappingMapper.insert(row);
        return row.getId();
    }

    @Transactional
    public void updateMapping(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovStandardMapping row = mappingMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "对标映射不存在: " + id);
        }
        if (body.containsKey("sourceSystem")) row.setSourceSystem(str(body.get("sourceSystem"), null));
        if (body.containsKey("sourceTable")) row.setSourceTable(str(body.get("sourceTable"), null));
        if (body.containsKey("sourceColumn")) row.setSourceColumn(str(body.get("sourceColumn"), null));
        if (body.containsKey("mappingStatus")) row.setMappingStatus(str(body.get("mappingStatus"), row.getMappingStatus()));
        if (body.containsKey("matchScore")) row.setMatchScore(toDecimal(body.get("matchScore")));
        if (body.containsKey("remark")) row.setRemark(str(body.get("remark"), null));
        row.setUpdatedAt(LocalDateTime.now());
        mappingMapper.updateById(row);
    }

    @Transactional
    public void deleteMapping(UserPrincipal operator, Long id) {
        GovStandardMapping row = mappingMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "对标映射不存在: " + id);
        }
        mappingMapper.deleteById(id);
    }

    private Connection openConnection(Long datasourceId) throws Exception {
        if (datasourceId != null) {
            IngDataSource ds = dataSourceMapper.selectById(datasourceId);
            if (ds == null) {
                throw new BusinessException(400, "数据源不存在: " + datasourceId);
            }
            String cfg = ds.getConnConfigJson();
            if (cfg == null || cfg.isBlank()) {
                throw new BusinessException(400, "数据源未配置连接信息");
            }
            JsonNode n = objectMapper.readTree(cfg);
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

    private GovStandardItem require(Long id) {
        GovStandardItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "数据元不存在: " + id);
        }
        return item;
    }

    private void applyBody(GovStandardItem item, Map<String, Object> body, boolean creating) {
        if (creating || body.containsKey("itemCode")) {
            item.setItemCode(str(body.get("itemCode"), creating ? "STD_" + System.currentTimeMillis() : item.getItemCode()));
        }
        if (creating || body.containsKey("itemName")) {
            Object name = body.get("itemName");
            if (name == null || String.valueOf(name).isBlank()) {
                throw new BusinessException(400, "itemName 不能为空");
            }
            item.setItemName(String.valueOf(name).trim());
        }
        if (creating || body.containsKey("itemType")) {
            item.setItemType(str(body.get("itemType"), creating ? "ELEMENT" : item.getItemType()));
        }
        if (body.containsKey("standardRef")) {
            item.setStandardRef(str(body.get("standardRef"), null));
        }
        if (body.containsKey("dataType")) {
            item.setDataType(str(body.get("dataType"), null));
        }
        if (body.containsKey("dataFormat")) {
            item.setDataFormat(str(body.get("dataFormat"), null));
        }
        if (body.containsKey("valueDomain")) {
            item.setValueDomain(str(body.get("valueDomain"), null));
        }
        if (body.containsKey("businessDefinition")) {
            item.setBusinessDefinition(str(body.get("businessDefinition"), null));
        }
        if (body.containsKey("businessRule")) {
            item.setBusinessRule(str(body.get("businessRule"), null));
        }
        if (body.containsKey("referenceStandard")) {
            item.setReferenceStandard(str(body.get("referenceStandard"), null));
        } else if (creating && body.get("standardRef") != null) {
            item.setReferenceStandard(str(body.get("standardRef"), null));
        }
        if (body.containsKey("category")) {
            item.setCategory(str(body.get("category"), null));
        }
        if (body.containsKey("sensitivity")) {
            item.setSensitivity(str(body.get("sensitivity"), null));
        }
    }

    private String toSnapshotJson(GovStandardItem item) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("id", item.getId());
        snap.put("itemCode", item.getItemCode());
        snap.put("itemName", item.getItemName());
        snap.put("itemType", item.getItemType());
        snap.put("standardRef", item.getStandardRef());
        snap.put("dataType", item.getDataType());
        snap.put("dataFormat", item.getDataFormat());
        snap.put("valueDomain", item.getValueDomain());
        snap.put("businessDefinition", item.getBusinessDefinition());
        snap.put("businessRule", item.getBusinessRule());
        snap.put("referenceStandard", item.getReferenceStandard());
        snap.put("category", item.getCategory());
        snap.put("sensitivity", item.getSensitivity());
        snap.put("publishStatus", item.getPublishStatus());
        snap.put("versionNo", item.getVersionNo());
        snap.put("publishedBy", item.getPublishedBy());
        snap.put("publishedAt", item.getPublishedAt() == null ? null : item.getPublishedAt().toString());
        snap.put("status", item.getStatus());
        try {
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            throw new BusinessException(500, "快照序列化失败: " + e.getMessage());
        }
    }

    private static String defaultNamingPattern(String namingType) {
        if ("API".equalsIgnoreCase(namingType)) {
            return "^[A-Za-z][A-Za-z0-9_/.-]*$";
        }
        if ("COLUMN".equalsIgnoreCase(namingType) || "TABLE".equalsIgnoreCase(namingType)) {
            return "^[a-z][a-z0-9_]*$";
        }
        return "^[A-Za-z][A-Za-z0-9_]*$";
    }

    // ---- 标准项比对 & 标准↔标准映射 ----

    public Map<String, Object> compareItems(Long leftId, Long rightId) {
        if (leftId == null || rightId == null) {
            throw new BusinessException(400, "请选择两个标准项");
        }
        if (leftId.equals(rightId)) {
            throw new BusinessException(400, "比对两侧不能为同一标准项");
        }
        GovStandardItem left = get(leftId);
        GovStandardItem right = get(rightId);
        List<Map<String, Object>> diffs = new ArrayList<>();
        addDiff(diffs, "编码", left.getItemCode(), right.getItemCode());
        addDiff(diffs, "名称", left.getItemName(), right.getItemName());
        addDiff(diffs, "类型", left.getItemType(), right.getItemType());
        addDiff(diffs, "数据类型", left.getDataType(), right.getDataType());
        addDiff(diffs, "数据格式", left.getDataFormat(), right.getDataFormat());
        addDiff(diffs, "值域", left.getValueDomain(), right.getValueDomain());
        addDiff(diffs, "业务定义", left.getBusinessDefinition(), right.getBusinessDefinition());
        addDiff(diffs, "业务规则", left.getBusinessRule(), right.getBusinessRule());
        addDiff(diffs, "引用标准", left.getReferenceStandard(), right.getReferenceStandard());
        addDiff(diffs, "分类", left.getCategory(), right.getCategory());
        addDiff(diffs, "敏感级", left.getSensitivity(), right.getSensitivity());
        addDiff(diffs, "发布状态", left.getPublishStatus(), right.getPublishStatus());
        addDiff(diffs, "版本号",
                left.getVersionNo() == null ? null : String.valueOf(left.getVersionNo()),
                right.getVersionNo() == null ? null : String.valueOf(right.getVersionNo()));
        long changed = diffs.stream().filter(d -> Boolean.TRUE.equals(d.get("changed"))).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("left", left);
        out.put("right", right);
        out.put("diffs", diffs);
        out.put("changedCount", changed);
        out.put("same", changed == 0);
        return out;
    }

    public List<Map<String, Object>> listItemLinks(Long itemId, String linkType, String status) {
        LambdaQueryWrapper<GovStandardItemLink> q = new LambdaQueryWrapper<GovStandardItemLink>()
                .orderByDesc(GovStandardItemLink::getId);
        if (itemId != null) {
            q.and(w -> w.eq(GovStandardItemLink::getSourceItemId, itemId)
                    .or().eq(GovStandardItemLink::getTargetItemId, itemId));
        }
        if (linkType != null && !linkType.isBlank()) {
            q.eq(GovStandardItemLink::getLinkType, linkType);
        }
        if (status != null && !status.isBlank()) {
            q.eq(GovStandardItemLink::getStatus, status);
        }
        List<GovStandardItemLink> links = itemLinkMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovStandardItemLink link : links) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", link.getId());
            row.put("sourceItemId", link.getSourceItemId());
            row.put("targetItemId", link.getTargetItemId());
            row.put("linkType", link.getLinkType());
            row.put("remark", link.getRemark());
            row.put("status", link.getStatus());
            row.put("createdBy", link.getCreatedBy());
            row.put("createdAt", link.getCreatedAt());
            row.put("updatedAt", link.getUpdatedAt());
            GovStandardItem src = itemMapper.selectById(link.getSourceItemId());
            GovStandardItem tgt = itemMapper.selectById(link.getTargetItemId());
            row.put("sourceItemCode", src == null ? null : src.getItemCode());
            row.put("sourceItemName", src == null ? null : src.getItemName());
            row.put("targetItemCode", tgt == null ? null : tgt.getItemCode());
            row.put("targetItemName", tgt == null ? null : tgt.getItemName());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Long createItemLink(UserPrincipal principal, Map<String, Object> body) {
        Long sourceId = toLong(body.get("sourceItemId"));
        Long targetId = toLong(body.get("targetItemId"));
        if (sourceId == null || targetId == null) {
            throw new BusinessException(400, "源/目标标准项不能为空");
        }
        if (sourceId.equals(targetId)) {
            throw new BusinessException(400, "源与目标不能为同一标准项");
        }
        if (itemMapper.selectById(sourceId) == null || itemMapper.selectById(targetId) == null) {
            throw new BusinessException(404, "标准项不存在");
        }
        String linkType = str(body.get("linkType"), "EQUIVALENT");
        Long exists = itemLinkMapper.selectCount(new LambdaQueryWrapper<GovStandardItemLink>()
                .eq(GovStandardItemLink::getSourceItemId, sourceId)
                .eq(GovStandardItemLink::getTargetItemId, targetId)
                .eq(GovStandardItemLink::getLinkType, linkType));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "该映射关系已存在");
        }
        GovStandardItemLink link = new GovStandardItemLink();
        link.setSourceItemId(sourceId);
        link.setTargetItemId(targetId);
        link.setLinkType(linkType);
        link.setRemark(str(body.get("remark"), null));
        link.setStatus(str(body.get("status"), "ACTIVE"));
        link.setCreatedBy(principal == null ? null : principal.getUsername());
        link.setCreatedAt(LocalDateTime.now());
        itemLinkMapper.insert(link);
        return link.getId();
    }

    @Transactional
    public void deleteItemLink(Long id) {
        if (itemLinkMapper.selectById(id) == null) {
            throw new BusinessException(404, "映射关系不存在");
        }
        itemLinkMapper.deleteById(id);
    }

    private static void addDiff(List<Map<String, Object>> diffs, String field, String left, String right) {
        String l = left == null ? "" : left.trim();
        String r = right == null ? "" : right.trim();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("field", field);
        row.put("left", l.isEmpty() ? "—" : l);
        row.put("right", r.isEmpty() ? "—" : r);
        row.put("changed", !l.equals(r));
        diffs.add(row);
    }

    private static void requireIdent(String name, String field) {
        if (name == null || name.isBlank() || !IDENT.matcher(name).matches()) {
            throw new BusinessException(400, field + " 非法或为空: " + name);
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        String s = n.get(field).asText();
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String str(Object v, String defaultVal) {
        if (v == null) {
            return defaultVal;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? defaultVal : s;
    }

    private static Long toLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(v));
    }

    private static int toInt(Object v, int def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return new BigDecimal(String.valueOf(v)).setScale(2, RoundingMode.HALF_UP);
    }
}
