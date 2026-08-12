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
import com.chengde.smartcity.masterdata.support.LayerJdbcSupport;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final LayerJdbcSupport layerJdbc;

    public FusionModelService(GovFusionDomainMapper domainMapper,
                              GovFusionLogicEntityMapper entityMapper,
                              GovFusionFieldMapper fieldMapper,
                              GovFusionRelationMapper relationMapper,
                              GovFusionPhysicalMapper physicalMapper,
                              IngDataSourceMapper dataSourceMapper,
                              LayerJdbcSupport layerJdbc) {
        this.domainMapper = domainMapper;
        this.entityMapper = entityMapper;
        this.fieldMapper = fieldMapper;
        this.relationMapper = relationMapper;
        this.physicalMapper = physicalMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.layerJdbc = layerJdbc;
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

    /**
     * 模型报告：按主题域导出 Excel（概要 / 逻辑模型图 / 实体列表 / 实体属性 / 物理映射）。
     */
    public byte[] exportModelReport(Long domainId) {
        Map<String, Object> tree = getDomainTree(domainId);
        GovFusionDomain domain = (GovFusionDomain) tree.get("domain");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entityNodes = (List<Map<String, Object>>) tree.get("entities");
        @SuppressWarnings("unchecked")
        List<GovFusionRelation> relations = (List<GovFusionRelation>) tree.get("relations");
        if (entityNodes == null) entityNodes = List.of();
        if (relations == null) relations = List.of();

        Map<Long, GovFusionLogicEntity> entityById = new HashMap<>();
        for (Map<String, Object> node : entityNodes) {
            GovFusionLogicEntity e = (GovFusionLogicEntity) node.get("entity");
            if (e != null && e.getId() != null) entityById.put(e.getId(), e);
        }

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle wrapStyle = wb.createCellStyle();
            wrapStyle.setWrapText(true);

            writeSummarySheet(wb, titleStyle, headerStyle, domain, entityNodes, relations);
            writeDiagramSheet(wb, titleStyle, headerStyle, wrapStyle, domain, entityNodes, relations, entityById);
            writeEntityListSheet(wb, headerStyle, entityNodes);
            writeFieldListSheet(wb, headerStyle, entityNodes);
            writePhysicalSheet(wb, headerStyle, entityNodes);

            wb.write(bos);
            return bos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("export model report failed domainId={}: {}", domainId, e.getMessage());
            throw new BusinessException(500, "导出模型报告失败: " + e.getMessage());
        }
    }

    public String modelReportFileName(Long domainId) {
        GovFusionDomain d = requireDomain(domainId);
        String code = d.getDomainCode() == null ? "domain" : d.getDomainCode().replaceAll("[^A-Za-z0-9_\\-]", "_");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "模型报告_" + code + "_" + ts + ".xlsx";
    }

    private void writeSummarySheet(Workbook wb, CellStyle titleStyle, CellStyle headerStyle,
                                   GovFusionDomain domain,
                                   List<Map<String, Object>> entityNodes,
                                   List<GovFusionRelation> relations) {
        Sheet sheet = wb.createSheet("报告概要");
        int r = 0;
        Row title = sheet.createRow(r++);
        Cell tc = title.createCell(0);
        tc.setCellValue("数据仓库建设 · 模型报告");
        tc.setCellStyle(titleStyle);

        r++;
        putKv(sheet, r++, "主题域编码", nullToEmpty(domain.getDomainCode()), headerStyle);
        putKv(sheet, r++, "主题域名称", nullToEmpty(domain.getDomainName()), headerStyle);
        putKv(sheet, r++, "描述", nullToEmpty(domain.getDescription()), headerStyle);
        putKv(sheet, r++, "状态", statusZh(domain.getStatus()), headerStyle);
        putKv(sheet, r++, "逻辑实体数", String.valueOf(entityNodes.size()), headerStyle);
        putKv(sheet, r++, "实体关系数", String.valueOf(relations.size()), headerStyle);

        int fieldCount = 0;
        int physicalCount = 0;
        for (Map<String, Object> node : entityNodes) {
            @SuppressWarnings("unchecked")
            List<GovFusionField> fields = (List<GovFusionField>) node.get("fields");
            @SuppressWarnings("unchecked")
            List<GovFusionPhysical> physicals = (List<GovFusionPhysical>) node.get("physical");
            if (fields != null) fieldCount += fields.size();
            if (physicals != null) physicalCount += physicals.size();
        }
        putKv(sheet, r++, "实体属性数", String.valueOf(fieldCount), headerStyle);
        putKv(sheet, r++, "物理映射数", String.valueOf(physicalCount), headerStyle);
        putKv(sheet, r++, "生成时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), headerStyle);

        r++;
        Row tip = sheet.createRow(r);
        tip.createCell(0).setCellValue("说明：本报告由「数据仓库建设」当前主题域导出，含逻辑模型图（文本/Mermaid）、实体列表、实体属性列表与物理映射。");
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 60 * 256);
    }

    private void writeDiagramSheet(Workbook wb, CellStyle titleStyle, CellStyle headerStyle, CellStyle wrapStyle,
                                   GovFusionDomain domain,
                                   List<Map<String, Object>> entityNodes,
                                   List<GovFusionRelation> relations,
                                   Map<Long, GovFusionLogicEntity> entityById) {
        Sheet sheet = wb.createSheet("逻辑模型图");
        int r = 0;
        Row title = sheet.createRow(r++);
        Cell tc = title.createCell(0);
        tc.setCellValue("主题域「" + nullToEmpty(domain.getDomainName()) + "」逻辑模型");
        tc.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        r++;
        Row h1 = sheet.createRow(r++);
        Cell h1c = h1.createCell(0);
        h1c.setCellValue("一、Mermaid ER 图（可粘贴至支持 Mermaid 的文档/工具渲染）");
        h1c.setCellStyle(headerStyle);

        String mermaid = buildMermaidEr(entityNodes, relations, entityById);
        Row mrow = sheet.createRow(r++);
        Cell mc = mrow.createCell(0);
        mc.setCellValue(mermaid);
        mc.setCellStyle(wrapStyle);
        mrow.setHeightInPoints(Math.min(320, 18f * Math.max(6, mermaid.split("\n").length)));
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 3));

        r++;
        Row h2 = sheet.createRow(r++);
        Cell h2c = h2.createCell(0);
        h2c.setCellValue("二、实体关系清单");
        h2c.setCellStyle(headerStyle);

        Row rh = sheet.createRow(r++);
        String[] relHeaders = {"关系编码", "关系名称", "类型", "源实体", "目标实体"};
        for (int i = 0; i < relHeaders.length; i++) {
            Cell c = rh.createCell(i);
            c.setCellValue(relHeaders[i]);
            c.setCellStyle(headerStyle);
        }
        for (GovFusionRelation rel : relations) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(nullToEmpty(rel.getRelationCode()));
            row.createCell(1).setCellValue(nullToEmpty(rel.getRelationName()));
            row.createCell(2).setCellValue(relationTypeZh(rel.getRelationType()));
            row.createCell(3).setCellValue(entityLabel(entityById.get(rel.getFromEntityId())));
            row.createCell(4).setCellValue(entityLabel(entityById.get(rel.getToEntityId())));
        }
        if (relations.isEmpty()) {
            Row empty = sheet.createRow(r++);
            empty.createCell(0).setCellValue("（暂无实体关系）");
        }

        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 28 * 256);
        sheet.setColumnWidth(4, 28 * 256);
    }

    private void writeEntityListSheet(Workbook wb, CellStyle headerStyle, List<Map<String, Object>> entityNodes) {
        Sheet sheet = wb.createSheet("实体列表");
        Row rh = sheet.createRow(0);
        String[] headers = {"序号", "实体编码", "实体名称", "状态", "描述", "字段数", "物理映射数"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = rh.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        int idx = 1;
        for (Map<String, Object> node : entityNodes) {
            GovFusionLogicEntity e = (GovFusionLogicEntity) node.get("entity");
            if (e == null) continue;
            @SuppressWarnings("unchecked")
            List<GovFusionField> fields = (List<GovFusionField>) node.get("fields");
            @SuppressWarnings("unchecked")
            List<GovFusionPhysical> physicals = (List<GovFusionPhysical>) node.get("physical");
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(idx++);
            row.createCell(1).setCellValue(nullToEmpty(e.getEntityCode()));
            row.createCell(2).setCellValue(nullToEmpty(e.getEntityName()));
            row.createCell(3).setCellValue(statusZh(e.getStatus()));
            row.createCell(4).setCellValue(nullToEmpty(e.getDescription()));
            row.createCell(5).setCellValue(fields == null ? 0 : fields.size());
            row.createCell(6).setCellValue(physicals == null ? 0 : physicals.size());
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, (i == 4 ? 36 : 16) * 256);
        }
    }

    private void writeFieldListSheet(Workbook wb, CellStyle headerStyle, List<Map<String, Object>> entityNodes) {
        Sheet sheet = wb.createSheet("实体属性列表");
        Row rh = sheet.createRow(0);
        String[] headers = {"实体编码", "实体名称", "字段编码", "字段名称", "数据类型", "主键", "可空", "排序"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = rh.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        for (Map<String, Object> node : entityNodes) {
            GovFusionLogicEntity e = (GovFusionLogicEntity) node.get("entity");
            if (e == null) continue;
            @SuppressWarnings("unchecked")
            List<GovFusionField> fields = (List<GovFusionField>) node.get("fields");
            if (fields == null || fields.isEmpty()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(nullToEmpty(e.getEntityCode()));
                row.createCell(1).setCellValue(nullToEmpty(e.getEntityName()));
                row.createCell(2).setCellValue("（无字段）");
                continue;
            }
            for (GovFusionField f : fields) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(nullToEmpty(e.getEntityCode()));
                row.createCell(1).setCellValue(nullToEmpty(e.getEntityName()));
                row.createCell(2).setCellValue(nullToEmpty(f.getFieldCode()));
                row.createCell(3).setCellValue(nullToEmpty(f.getFieldName()));
                row.createCell(4).setCellValue(nullToEmpty(f.getDataType()));
                row.createCell(5).setCellValue(f.getPkFlag() != null && f.getPkFlag() == 1 ? "是" : "否");
                row.createCell(6).setCellValue(f.getNullableFlag() == null || f.getNullableFlag() == 1 ? "是" : "否");
                row.createCell(7).setCellValue(f.getSortOrder() == null ? 0 : f.getSortOrder());
            }
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, 16 * 256);
        }
    }

    private void writePhysicalSheet(Workbook wb, CellStyle headerStyle, List<Map<String, Object>> entityNodes) {
        Sheet sheet = wb.createSheet("物理映射");
        Row rh = sheet.createRow(0);
        String[] headers = {"实体编码", "实体名称", "物理编码", "物理表名", "数据源ID", "状态"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = rh.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        for (Map<String, Object> node : entityNodes) {
            GovFusionLogicEntity e = (GovFusionLogicEntity) node.get("entity");
            if (e == null) continue;
            @SuppressWarnings("unchecked")
            List<GovFusionPhysical> physicals = (List<GovFusionPhysical>) node.get("physical");
            if (physicals == null || physicals.isEmpty()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(nullToEmpty(e.getEntityCode()));
                row.createCell(1).setCellValue(nullToEmpty(e.getEntityName()));
                row.createCell(2).setCellValue("（未绑定）");
                continue;
            }
            for (GovFusionPhysical p : physicals) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(nullToEmpty(e.getEntityCode()));
                row.createCell(1).setCellValue(nullToEmpty(e.getEntityName()));
                row.createCell(2).setCellValue(nullToEmpty(p.getPhysicalCode()));
                row.createCell(3).setCellValue(nullToEmpty(p.getTableName()));
                row.createCell(4).setCellValue(p.getDatasourceId() == null ? "" : String.valueOf(p.getDatasourceId()));
                row.createCell(5).setCellValue(statusZh(p.getStatus()));
            }
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, 18 * 256);
        }
    }

    private static String buildMermaidEr(List<Map<String, Object>> entityNodes,
                                         List<GovFusionRelation> relations,
                                         Map<Long, GovFusionLogicEntity> entityById) {
        StringBuilder sb = new StringBuilder();
        sb.append("erDiagram\n");
        for (Map<String, Object> node : entityNodes) {
            GovFusionLogicEntity e = (GovFusionLogicEntity) node.get("entity");
            if (e == null) continue;
            String alias = mermaidAlias(e);
            sb.append("  ").append(alias).append(" {\n");
            @SuppressWarnings("unchecked")
            List<GovFusionField> fields = (List<GovFusionField>) node.get("fields");
            if (fields != null) {
                int n = 0;
                for (GovFusionField f : fields) {
                    if (n++ >= 12) {
                        sb.append("    string more \"…\"\n");
                        break;
                    }
                    String type = mermaidType(f.getDataType());
                    String pk = f.getPkFlag() != null && f.getPkFlag() == 1 ? " PK" : "";
                    sb.append("    ").append(type).append(' ')
                            .append(safeIdent(f.getFieldCode())).append(pk).append('\n');
                }
            }
            if (fields == null || fields.isEmpty()) {
                sb.append("    string id\n");
            }
            sb.append("  }\n");
        }
        for (GovFusionRelation rel : relations) {
            GovFusionLogicEntity from = entityById.get(rel.getFromEntityId());
            GovFusionLogicEntity to = entityById.get(rel.getToEntityId());
            if (from == null || to == null) continue;
            String card = mermaidCardinality(rel.getRelationType());
            sb.append("  ").append(mermaidAlias(from)).append(card).append(mermaidAlias(to))
                    .append(" : ").append(safeLabel(rel.getRelationName())).append('\n');
        }
        if (entityNodes.isEmpty()) {
            sb.append("  EMPTY {\n    string tip \"暂无逻辑实体\"\n  }\n");
        }
        return sb.toString();
    }

    private static void putKv(Sheet sheet, int rowIdx, String k, String v, CellStyle keyStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell kc = row.createCell(0);
        kc.setCellValue(k);
        kc.setCellStyle(keyStyle);
        row.createCell(1).setCellValue(v == null ? "" : v);
    }

    private static String entityLabel(GovFusionLogicEntity e) {
        if (e == null) return "—";
        return nullToEmpty(e.getEntityName()) + " (" + nullToEmpty(e.getEntityCode()) + ")";
    }

    private static String mermaidAlias(GovFusionLogicEntity e) {
        return safeIdent(e.getEntityCode() == null ? ("E" + e.getId()) : e.getEntityCode());
    }

    private static String safeIdent(String raw) {
        if (raw == null || raw.isBlank()) return "unnamed";
        String s = raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) s = "f_" + s;
        return s;
    }

    private static String safeLabel(String raw) {
        if (raw == null || raw.isBlank()) return "rel";
        return raw.replace('"', '\'').replace('\n', ' ');
    }

    private static String mermaidType(String dataType) {
        if (dataType == null) return "string";
        String t = dataType.toLowerCase(Locale.ROOT);
        if (t.contains("int") || t.contains("long") || t.contains("number") || t.contains("decimal")) return "int";
        if (t.contains("date") || t.contains("time")) return "datetime";
        if (t.contains("bool")) return "boolean";
        return "string";
    }

    private static String mermaidCardinality(String type) {
        if ("ONE_TO_ONE".equalsIgnoreCase(type)) return " ||--|| ";
        if ("MANY_TO_MANY".equalsIgnoreCase(type)) return " }o--o{ ";
        return " ||--o{ ";
    }

    private static String relationTypeZh(String type) {
        if ("ONE_TO_ONE".equalsIgnoreCase(type)) return "一对一";
        if ("MANY_TO_MANY".equalsIgnoreCase(type)) return "多对多";
        return "一对多";
    }

    private static String statusZh(String status) {
        if (status == null) return "—";
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> "启用";
            case "DRAFT" -> "草稿";
            case "INACTIVE", "DISABLED" -> "停用";
            default -> status;
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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
