package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataColumn;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.entity.IngUploadRecord;
import com.chengde.smartcity.exchange.entity.IngUploadTemplate;
import com.chengde.smartcity.exchange.mapper.IngDataColumnMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.exchange.mapper.IngUploadRecordMapper;
import com.chengde.smartcity.exchange.mapper.IngUploadTemplateMapper;
import com.chengde.smartcity.masterdata.service.MetadataSubsystemService;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 手动上传：部门模板绑定已登记数据资产 → 写入 smart_city_ods，并回写资产汇聚状态。
 */
@Service
public class ExcelManualUploadService {

    private static final Logger log = LoggerFactory.getLogger(ExcelManualUploadService.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Pattern DB_IN_URL = Pattern.compile("(jdbc:mysql://[^/]+/)([^?]+)(\\?.*)?", Pattern.CASE_INSENSITIVE);
    private static final int PREVIEW_LIMIT = 50;
    private static final int MAX_COMMIT_ROWS = 100_000;

    private final IngUploadRecordMapper uploadMapper;
    private final IngUploadTemplateMapper templateMapper;
    private final IngDataTableMapper dataTableMapper;
    private final IngDataColumnMapper dataColumnMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final IngProjectMapper projectMapper;
    private final AuditService auditService;
    private final MetadataSubsystemService metadataSubsystemService;
    private final String datasourceUrl;
    private final String datasourceUser;
    private final String datasourcePassword;
    private final ConcurrentHashMap<String, PendingFile> pending = new ConcurrentHashMap<>();

    public ExcelManualUploadService(IngUploadRecordMapper uploadMapper,
                                    IngUploadTemplateMapper templateMapper,
                                    IngDataTableMapper dataTableMapper,
                                    IngDataColumnMapper dataColumnMapper,
                                    IngDataSourceMapper dataSourceMapper,
                                    IngProjectMapper projectMapper,
                                    AuditService auditService,
                                    MetadataSubsystemService metadataSubsystemService,
                                    @Value("${spring.datasource.url}") String datasourceUrl,
                                    @Value("${spring.datasource.username}") String datasourceUser,
                                    @Value("${spring.datasource.password:}") String datasourcePassword) {
        this.uploadMapper = uploadMapper;
        this.templateMapper = templateMapper;
        this.dataTableMapper = dataTableMapper;
        this.dataColumnMapper = dataColumnMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.projectMapper = projectMapper;
        this.auditService = auditService;
        this.metadataSubsystemService = metadataSubsystemService;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUser = datasourceUser;
        this.datasourcePassword = datasourcePassword == null ? "" : datasourcePassword;
    }

    public Map<String, Object> inspect(UserPrincipal operator, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择文件");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
        String lower = original.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv")) {
            throw new BusinessException(400, "仅支持 .xlsx / .xls / .csv");
        }
        try {
            Path dir = Files.createTempDirectory("chengde-upload-");
            Path dest = dir.resolve(sanitizeFileName(original));
            file.transferTo(dest.toFile());
            String token = UUID.randomUUID().toString().replace("-", "");
            List<String> sheets = listSheets(dest, lower);
            if (sheets.isEmpty()) {
                throw new BusinessException(400, "未找到可用工作表或 CSV 内容为空");
            }
            cleanupOwnerPending(operator.getUsername());
            pending.put(token, new PendingFile(dest, original, lower, operator.getUsername()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uploadToken", token);
            m.put("fileName", original);
            m.put("sheets", sheets);
            m.put("suggestedSheet", sheets.get(0));
            m.put("suggestedTable", suggestTableName(original, sheets.get(0)));
            m.put("committedSheets", List.of());
            return m;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("inspect upload failed: {}", e.getMessage());
            throw new BusinessException(400, "解析文件失败: " + e.getMessage());
        }
    }

    /** 按指定表头行读取列名（1-based），用于建模板 */
    public Map<String, Object> previewHeader(UserPrincipal operator, Map<String, Object> body) {
        String token = requiredStr(body.get("uploadToken"), "uploadToken");
        String sheetName = requiredStr(body.get("sheetName"), "sheetName");
        int headerRow = parseHeaderRow(body.get("headerRow"));
        PendingFile pf = requirePending(token, operator);
        try {
            SheetData data = readSheet(pf.path(), pf.lowerName(), sheetName, 5, headerRow);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sheetName", sheetName);
            m.put("headerRow", headerRow);
            m.put("columns", data.columns());
            m.put("sampleRows", data.rows());
            m.put("suggestedTable", suggestTableName(pf.originalName(), sheetName));
            return m;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "读取表头失败: " + e.getMessage());
        }
    }

    public Map<String, Object> preview(UserPrincipal operator, Map<String, Object> body) {
        String token = requiredStr(body.get("uploadToken"), "uploadToken");
        String sheetName = requiredStr(body.get("sheetName"), "sheetName");
        String templateCode = requiredStr(body.get("templateCode"), "templateCode");
        TemplateBinding binding = requireBinding(templateCode, sheetName);
        PendingFile pf = requirePending(token, operator);
        int limit = body.get("limit") instanceof Number n ? Math.min(n.intValue(), PREVIEW_LIMIT) : PREVIEW_LIMIT;
        try {
            SheetData raw = readSheet(pf.path(), pf.lowerName(), sheetName, limit + 1, binding.headerRow());
            assertSchemaMatch(binding.columns(), raw.columns());
            SheetData projected = projectColumns(raw, binding.columns());
            List<Map<String, String>> rows = projected.rows().size() > limit
                    ? projected.rows().subList(0, limit)
                    : projected.rows();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sheetName", sheetName);
            m.put("headerRow", binding.headerRow());
            m.put("columns", projected.columns());
            m.put("rows", rows);
            m.put("previewRows", rows.size());
            m.put("truncated", projected.rows().size() > limit);
            m.put("targetTable", binding.targetTable());
            return m;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "预览失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> commitToOds(UserPrincipal operator, Map<String, Object> body) {
        String token = requiredStr(body.get("uploadToken"), "uploadToken");
        String sheetName = requiredStr(body.get("sheetName"), "sheetName");
        String templateCode = requiredStr(body.get("templateCode"), "templateCode");
        // 字段与模板一致后方可写入：APPEND=增量追加行；REPLACE=全量覆盖
        String writeMode = str(body.get("writeMode"), "APPEND").toUpperCase(Locale.ROOT);
        if (!"APPEND".equals(writeMode) && !"REPLACE".equals(writeMode)) {
            throw new BusinessException(400, "writeMode 仅支持 APPEND（增量）或 REPLACE（全量覆盖）");
        }
        TemplateBinding binding = requireBinding(templateCode, sheetName);
        PendingFile pf = requirePending(token, operator);
        try {
            SheetData raw = readSheet(pf.path(), pf.lowerName(), sheetName, MAX_COMMIT_ROWS + 1, binding.headerRow());
            assertSchemaMatch(binding.columns(), raw.columns());
            SheetData data = projectColumns(raw, binding.columns());
            if (data.rows().size() > MAX_COMMIT_ROWS) {
                throw new BusinessException(400, "行数超过上限 " + MAX_COMMIT_ROWS);
            }
            String targetTable = binding.targetTable();
            writeToOds(targetTable, data, writeMode);
            Long assetTableId = binding.tableId();
            if (assetTableId != null) {
                markAssetCollected(assetTableId, targetTable, data.rows().size());
                syncAssetColumns(assetTableId, binding.columns());
                try {
                    metadataSubsystemService.registerAfterCollect(operator, assetTableId, targetTable, "手动上传");
                } catch (Exception e) {
                    log.warn("手动上传后元数据登记失败 tableId={} ods={}: {}", assetTableId, targetTable, e.getMessage());
                }
            }
            IngUploadRecord r = new IngUploadRecord();
            r.setTemplateCode(templateCode);
            r.setFileName(pf.originalName());
            r.setSheetName(sheetName);
            r.setTargetTable(targetTable);
            r.setStoragePath(pf.path().toString());
            r.setRowCount(data.rows().size());
            r.setStatus("COMMITTED");
            Map<String, Object> previewPayload = new LinkedHashMap<>();
            previewPayload.put("columns", data.columns());
            previewPayload.put("headerRow", binding.headerRow());
            previewPayload.put("writeMode", writeMode);
            previewPayload.put("odsTable", "smart_city_ods." + targetTable);
            if (assetTableId != null) {
                previewPayload.put("tableId", assetTableId);
            }
            r.setPreviewJson(OM.writeValueAsString(previewPayload));
            r.setCreatedBy(operator.getUsername());
            uploadMapper.insert(r);
            pf.committedSheets().add(sheetName);
            String modeLabel = "APPEND".equals(writeMode) ? "增量写入" : "全量覆盖";
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "ING_UPLOAD_ODS", "ing_upload_record", String.valueOf(r.getId()),
                    templateCode + " / " + sheetName + " → " + targetTable + " [" + writeMode + "]");
            List<String> templateSheets = listTemplateSheets(templateCode);
            List<String> remaining = templateSheets.stream()
                    .filter(s -> !pf.committedSheets().contains(s))
                    .toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uploadId", r.getId());
            m.put("targetTable", targetTable);
            m.put("odsDatabase", "smart_city_ods");
            m.put("tableId", assetTableId);
            m.put("rowCount", data.rows().size());
            m.put("sheetName", sheetName);
            m.put("writeMode", writeMode);
            m.put("committedSheets", new ArrayList<>(pf.committedSheets()));
            m.put("remainingSheets", remaining);
            m.put("uploadToken", token);
            m.put("message", "已" + modeLabel + " smart_city_ods." + targetTable
                    + "（" + data.rows().size() + " 行）"
                    + (assetTableId != null ? "，已绑定资产#" + assetTableId : "")
                    + (remaining.isEmpty() ? "；可点「重新开始」结束"
                    : "；还可继续：" + String.join("、", remaining)));
            return m;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("commitToOds failed", e);
            throw new BusinessException(500, "写入 ODS 失败: " + e.getMessage());
        }
    }

    public Map<String, Object> finishSession(UserPrincipal operator, Map<String, Object> body) {
        String token = requiredStr(body.get("uploadToken"), "uploadToken");
        PendingFile pf = pending.remove(token);
        if (pf != null) {
            if (operator.getUsername() != null && pf.owner() != null
                    && !pf.owner().equals(operator.getUsername())) {
                pending.put(token, pf);
                throw new BusinessException(403, "无权结束该上传会话");
            }
            try {
                Files.deleteIfExists(pf.path());
            } catch (Exception ignore) {
                /* best-effort */
            }
        }
        return Map.of("message", "上传会话已结束", "ok", true);
    }

    /** 从 Excel 会话保存部门模板（多 sheet 绑定） */
    @Transactional
    public Long saveTemplate(UserPrincipal operator, Map<String, Object> body) {
        String name = requiredStr(body.get("templateName"), "templateName");
        String code = str(body.get("templateCode"), "TPL_" + System.currentTimeMillis());
        Object bindingsObj = body.get("bindings");
        if (!(bindingsObj instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(400, "请至少绑定一个 sheet 的字段作为模板");
        }
        try {
            ArrayNode arr = OM.createArrayNode();
            for (Object o : list) {
                @SuppressWarnings("unchecked")
                Map<String, Object> b = o instanceof Map ? (Map<String, Object>) o : OM.convertValue(o, Map.class);
                String sheetName = requiredStr(b.get("sheetName"), "sheetName");
                int headerRow = parseHeaderRow(b.get("headerRow"));
                Object cols = b.get("columns");
                if (!(cols instanceof List<?> colList) || colList.isEmpty()) {
                    throw new BusinessException(400, "sheet「" + sheetName + "」未选择字段");
                }
                List<String> columns = new ArrayList<>();
                for (Object c : colList) {
                    String cn = String.valueOf(c).trim();
                    if (!cn.isEmpty()) {
                        columns.add(cn);
                    }
                }
                if (columns.isEmpty()) {
                    throw new BusinessException(400, "sheet「" + sheetName + "」字段为空");
                }
                String targetTable = sanitizeIdent(str(b.get("targetTable"), suggestTableName(code, sheetName)));
                if (!targetTable.toLowerCase(Locale.ROOT).startsWith("ods_")) {
                    targetTable = "ods_" + targetTable;
                }
                // 优先沿用传入 tableId；否则自动登记为新数据资产并同步字段
                Long tableId = longVal(b.get("tableId"));
                IngDataTable asset;
                if (tableId != null) {
                    asset = dataTableMapper.selectById(tableId);
                    if (asset == null) {
                        throw new BusinessException(404, "数据资产不存在: " + tableId);
                    }
                } else {
                    String assetName = str(b.get("assetName"), name);
                    Long sourceId = longVal(b.get("sourceId"));
                    asset = createUploadAsset(operator, assetName, targetTable, sheetName, columns, sourceId);
                    tableId = asset.getId();
                }
                asset.setPhysicalTableName(targetTable);
                asset.setSourceTable(targetTable);
                asset.setCollectStatus("IDLE");
                dataTableMapper.updateById(asset);
                syncAssetColumns(tableId, columns);

                ObjectNode node = OM.createObjectNode();
                node.put("sheetName", sheetName);
                node.put("headerRow", headerRow);
                node.put("targetTable", targetTable);
                node.put("tableId", tableId);
                ArrayNode colArr = node.putArray("columns");
                columns.forEach(colArr::add);
                arr.add(node);
            }
            ObjectNode root = OM.createObjectNode();
            root.put("version", 2);
            root.set("bindings", arr);
            IngUploadTemplate t = new IngUploadTemplate();
            t.setTemplateCode(code);
            t.setTemplateName(name);
            t.setColumnMappingJson(OM.writeValueAsString(root));
            t.setValidateRulesJson("{\"schemaPolicy\":\"STRICT\",\"writeMode\":\"REPLACE\"}");
            t.setStatus("ACTIVE");
            templateMapper.insert(t);
            auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                    "ING_UPLOAD_TPL", "ing_upload_template", String.valueOf(t.getId()), name);
            return t.getId();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "保存模板失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> describeTemplate(String templateCode) {
        IngUploadTemplate t = findTemplate(templateCode);
        List<TemplateBinding> bindings = parseBindings(t.getColumnMappingJson());
        List<Map<String, Object>> list = new ArrayList<>();
        for (TemplateBinding b : bindings) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sheetName", b.sheetName());
            m.put("headerRow", b.headerRow());
            m.put("columns", b.columns());
            m.put("targetTable", b.targetTable());
            m.put("tableId", b.tableId());
            if (b.tableId() != null) {
                IngDataTable tb = dataTableMapper.selectById(b.tableId());
                if (tb != null) {
                    m.put("tableName", tb.getTableName());
                    m.put("tableCode", tb.getTableCode());
                }
            }
            list.add(m);
        }
        return list;
    }

    private TemplateBinding requireBinding(String templateCode, String sheetName) {
        IngUploadTemplate t = findTemplate(templateCode);
        if (t.getStatus() != null && !"ACTIVE".equalsIgnoreCase(t.getStatus())) {
            throw new BusinessException(400, "模板已停用，无法用于数据上传");
        }
        List<TemplateBinding> all = parseBindings(t.getColumnMappingJson());
        if (all.isEmpty()) {
            throw new BusinessException(400,
                    "该模板为旧版格式（无 sheet 绑定），不能用于上传。请用样例 Excel 重新录入：读取表头 → 加入绑定 → 保存模板");
        }
        String want = sheetName == null ? "" : sheetName.trim();
        return all.stream()
                .filter(b -> b.sheetName().equals(want))
                .findFirst()
                .or(() -> all.stream().filter(b -> b.sheetName().equalsIgnoreCase(want)).findFirst())
                .or(() -> all.size() == 1 ? java.util.Optional.of(all.get(0)) : java.util.Optional.empty())
                .orElseThrow(() -> new BusinessException(400,
                        "模板未绑定工作表「" + sheetName + "」。请确认 sheet 名称与录入模板时一致，或新建模板"));
    }

    private List<String> listTemplateSheets(String templateCode) {
        return parseBindings(findTemplate(templateCode).getColumnMappingJson()).stream()
                .map(TemplateBinding::sheetName)
                .toList();
    }

    private IngUploadTemplate findTemplate(String templateCode) {
        IngUploadTemplate t = templateMapper.selectOne(new LambdaQueryWrapper<IngUploadTemplate>()
                .eq(IngUploadTemplate::getTemplateCode, templateCode)
                .last("LIMIT 1"));
        if (t == null) {
            throw new BusinessException(404, "模板不存在: " + templateCode);
        }
        return t;
    }

    private List<TemplateBinding> parseBindings(String json) {
        List<TemplateBinding> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode root = OM.readTree(json);
            // 旧版种子：[{"col":"name","target":"entity_name"}] —— 无 sheet/表头，不可用于新上传
            if (root.isArray()) {
                return out;
            }
            JsonNode bindings = root.get("bindings");
            if (bindings == null || !bindings.isArray()) {
                return out;
            }
            for (JsonNode n : bindings) {
                String sheet = n.path("sheetName").asText("");
                int headerRow = n.path("headerRow").asInt(1);
                if (headerRow < 1) {
                    headerRow = 1;
                }
                String table = n.path("targetTable").asText("");
                Long tableId = n.has("tableId") && !n.path("tableId").isNull()
                        ? n.path("tableId").asLong() : null;
                List<String> cols = new ArrayList<>();
                JsonNode arr = n.get("columns");
                if (arr != null && arr.isArray()) {
                    for (JsonNode c : arr) {
                        cols.add(c.asText());
                    }
                }
                if (!sheet.isBlank() && !cols.isEmpty() && !table.isBlank()) {
                    out.add(new TemplateBinding(sheet, headerRow, cols, table, tableId));
                }
            }
        } catch (Exception e) {
            log.warn("parseBindings failed: {}", e.getMessage());
        }
        return out;
    }

    /** 模板字段须全部出现在文件表头中；缺列/改名视为新结构。多余列忽略（不写入）。 */
    private void assertSchemaMatch(List<String> expected, List<String> actual) {
        Set<String> act = actual.stream().map(this::normCol).collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> missing = expected.stream()
                .filter(c -> !act.contains(normCol(c)))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(400,
                    "字段与模板不一致，已视为新表结构。请重新录入模板后再上传；跨表整合请走数据治理。"
                            + " 缺失字段=" + missing + "；模板字段=" + expected + "；文件字段=" + actual);
        }
    }

    private SheetData projectColumns(SheetData raw, List<String> templateCols) {
        Map<String, String> normToActual = new LinkedHashMap<>();
        for (String c : raw.columns()) {
            normToActual.put(normCol(c), c);
        }
        List<String> ordered = new ArrayList<>();
        for (String tc : templateCols) {
            String actual = normToActual.get(normCol(tc));
            ordered.add(actual != null ? actual : tc);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, String> row : raw.rows()) {
            Map<String, String> m = new LinkedHashMap<>();
            for (String col : ordered) {
                m.put(col, row.getOrDefault(col, ""));
            }
            rows.add(m);
        }
        return new SheetData(ordered, rows);
    }

    private String normCol(String c) {
        return c == null ? "" : c.trim();
    }

    private int parseHeaderRow(Object v) {
        int row = 1;
        if (v instanceof Number n) {
            row = n.intValue();
        } else if (v != null) {
            try {
                row = Integer.parseInt(String.valueOf(v).trim());
            } catch (Exception ignore) {
                row = 1;
            }
        }
        if (row < 1) {
            row = 1;
        }
        if (row > 200) {
            throw new BusinessException(400, "表头行号过大");
        }
        return row;
    }

    private PendingFile requirePending(String token, UserPrincipal operator) {
        PendingFile pf = pending.get(token);
        if (pf == null) {
            throw new BusinessException(400, "上传会话已失效，请重新选择文件");
        }
        if (operator.getUsername() != null && pf.owner() != null
                && !pf.owner().equals(operator.getUsername())) {
            throw new BusinessException(403, "无权操作该上传会话");
        }
        return pf;
    }

    private void cleanupOwnerPending(String owner) {
        if (owner == null) {
            return;
        }
        List<String> keys = pending.entrySet().stream()
                .filter(e -> owner.equals(e.getValue().owner()))
                .map(Map.Entry::getKey)
                .toList();
        for (String k : keys) {
            PendingFile old = pending.remove(k);
            if (old != null) {
                try {
                    Files.deleteIfExists(old.path());
                } catch (Exception ignore) {
                    /* best-effort */
                }
            }
        }
    }

    private List<String> listSheets(Path path, String lowerName) throws Exception {
        if (lowerName.endsWith(".csv")) {
            return List.of("CSV");
        }
        try (InputStream in = Files.newInputStream(path); Workbook wb = WorkbookFactory.create(in)) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet s = wb.getSheetAt(i);
                if (s != null && !wb.isSheetHidden(i) && !wb.isSheetVeryHidden(i)) {
                    names.add(s.getSheetName());
                }
            }
            return names;
        }
    }

    /** headerRow 为 Excel 行号（从 1 起） */
    private SheetData readSheet(Path path, String lowerName, String sheetName, int maxDataRows, int headerRow)
            throws Exception {
        if (lowerName.endsWith(".csv")) {
            return readCsv(path, maxDataRows, headerRow);
        }
        DataFormatter fmt = new DataFormatter();
        try (InputStream in = Files.newInputStream(path); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                throw new BusinessException(400, "工作表不存在: " + sheetName);
            }
            int headerIdx = headerRow - 1;
            Row header = sheet.getRow(headerIdx);
            if (header == null) {
                throw new BusinessException(400, "第 " + headerRow + " 行无表头数据");
            }
            List<String> columns = new ArrayList<>();
            short lastCell = header.getLastCellNum();
            if (lastCell < 0) {
                throw new BusinessException(400, "表头为空");
            }
            for (int c = 0; c < lastCell; c++) {
                String h = cellText(header.getCell(c), fmt).trim();
                if (h.isEmpty()) {
                    h = "col_" + (c + 1);
                }
                columns.add(uniqueColumn(columns, h));
            }
            List<Map<String, String>> rows = new ArrayList<>();
            int last = sheet.getLastRowNum();
            for (int r = headerIdx + 1; r <= last && rows.size() < maxDataRows; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, columns.size(), fmt)) {
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                for (int c = 0; c < columns.size(); c++) {
                    map.put(columns.get(c), cellText(row.getCell(c), fmt));
                }
                rows.add(map);
            }
            return new SheetData(columns, rows);
        }
    }

    private SheetData readCsv(Path path, int maxDataRows, int headerRow) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        if (lines.isEmpty()) {
            throw new BusinessException(400, "CSV 为空");
        }
        int headerIdx = headerRow - 1;
        if (headerIdx >= lines.size()) {
            throw new BusinessException(400, "表头行超出 CSV 行数");
        }
        List<String> rawHeaders = splitCsvLine(lines.get(headerIdx));
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < rawHeaders.size(); i++) {
            String h = rawHeaders.get(i).trim();
            if (h.isEmpty()) {
                h = "col_" + (i + 1);
            }
            columns.add(uniqueColumn(columns, h));
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = headerIdx + 1; i < lines.size() && rows.size() < maxDataRows; i++) {
            List<String> cells = splitCsvLine(lines.get(i));
            Map<String, String> map = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                map.put(columns.get(c), c < cells.size() ? cells.get(c) : "");
            }
            rows.add(map);
        }
        return new SheetData(columns, rows);
    }

    /**
     * APPEND：字段一致时增量插入新行（不清空表）；REPLACE：清空后全量写入。
     * 字段不一致已在上层 assertSchemaMatch 拦截。
     */
    private void writeToOds(String table, SheetData data, String writeMode) throws Exception {
        String url = toOdsJdbcUrl(datasourceUrl);
        List<String> colIdents = data.columns().stream().map(this::sanitizeIdent).toList();
        try (Connection conn = DriverManager.getConnection(url, datasourceUser, datasourcePassword)) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS smart_city_ods DEFAULT CHARACTER SET utf8mb4");
            }
            StringBuilder ddl = new StringBuilder();
            ddl.append("CREATE TABLE IF NOT EXISTS `").append(table).append("` (");
            ddl.append("`id` BIGINT NOT NULL AUTO_INCREMENT,");
            for (String col : colIdents) {
                // TEXT 不计入 InnoDB 行内上限，避免多列 VARCHAR(1024) 触发 Row size too large
                ddl.append("`").append(col).append("` TEXT NULL,");
            }
            ddl.append("`_uploaded_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,");
            ddl.append("PRIMARY KEY (`id`)");
            ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            try (Statement st = conn.createStatement()) {
                st.execute(ddl.toString());
                if ("REPLACE".equals(writeMode)) {
                    st.execute("TRUNCATE TABLE `" + table + "`");
                }
            }
            StringBuilder insert = new StringBuilder("INSERT INTO `").append(table).append("` (");
            insert.append(String.join(",", colIdents.stream().map(c -> "`" + c + "`").toList()));
            insert.append(") VALUES (");
            insert.append("?,".repeat(colIdents.size()));
            insert.setLength(insert.length() - 1);
            insert.append(")");
            try (PreparedStatement ps = conn.prepareStatement(insert.toString())) {
                int batch = 0;
                for (Map<String, String> row : data.rows()) {
                    for (int i = 0; i < data.columns().size(); i++) {
                        String key = data.columns().get(i);
                        String val = row.get(key);
                        // TEXT 上限约 64KB；过长截断避免写入失败
                        if (val != null && val.length() > 60000) {
                            val = val.substring(0, 60000);
                        }
                        ps.setString(i + 1, val);
                    }
                    ps.addBatch();
                    if (++batch % 500 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }
            conn.commit();
        }
    }

    private String toOdsJdbcUrl(String url) {
        Matcher m = DB_IN_URL.matcher(url);
        if (m.find()) {
            String qs = m.group(3) != null ? m.group(3) : "";
            return m.group(1) + "smart_city_ods" + qs;
        }
        return url;
    }

    private static String cellText(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return "";
        }
        return fmt.formatCellValue(cell);
    }

    private static boolean isBlankRow(Row row, int cols, DataFormatter fmt) {
        for (int c = 0; c < cols; c++) {
            if (!cellText(row.getCell(c), fmt).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String uniqueColumn(List<String> existing, String name) {
        String base = name;
        String candidate = base;
        int i = 2;
        while (existing.contains(candidate)) {
            candidate = base + "_" + i++;
        }
        return candidate;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuote = !inQuote;
            } else if ((ch == ',' && !inQuote) || ch == '\t') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private String suggestTableName(String fileName, String sheet) {
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String sheetPart = "CSV".equalsIgnoreCase(sheet) ? "" : "_" + sheet;
        return "ods_" + sanitizeIdent(base + sheetPart);
    }

    private String sanitizeIdent(String raw) {
        String s = raw == null ? "col" : raw.trim().replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_");
        if (s.isEmpty()) {
            s = "col";
        }
        if (Character.isDigit(s.charAt(0))) {
            s = "c_" + s;
        }
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String str(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static String requiredStr(Object v, String field) {
        String s = str(v, "");
        if (s.isEmpty()) {
            throw new BusinessException(400, field + " 不能为空");
        }
        return s;
    }

    private static final class PendingFile {
        private final Path path;
        private final String originalName;
        private final String lowerName;
        private final String owner;
        private final Set<String> committedSheets = new LinkedHashSet<>();

        private PendingFile(Path path, String originalName, String lowerName, String owner) {
            this.path = path;
            this.originalName = originalName;
            this.lowerName = lowerName;
            this.owner = owner;
        }

        private Path path() { return path; }
        private String originalName() { return originalName; }
        private String lowerName() { return lowerName; }
        private String owner() { return owner; }
        private Set<String> committedSheets() { return committedSheets; }
    }

    private record SheetData(List<String> columns, List<Map<String, String>> rows) {}

    private record TemplateBinding(String sheetName, int headerRow, List<String> columns, String targetTable, Long tableId) {}

    private void markAssetCollected(Long tableId, String targetTable, int rowCount) {
        IngDataTable table = dataTableMapper.selectById(tableId);
        if (table == null) {
            return;
        }
        if (targetTable != null && !targetTable.isBlank()) {
            table.setPhysicalTableName(targetTable);
        }
        table.setCollectStatus("SUCCESS");
        table.setLastCollectAt(LocalDateTime.now());
        if (rowCount >= 0) {
            table.setSourceRowCount((long) rowCount);
        }
        dataTableMapper.updateById(table);
    }

    /** 将模板字段同步为资产列（按 columnCode 去重，仅补缺）。 */
    private void syncAssetColumns(Long tableId, List<String> columns) {
        if (tableId == null || columns == null || columns.isEmpty()) {
            return;
        }
        List<IngDataColumn> existing = dataColumnMapper.selectList(new LambdaQueryWrapper<IngDataColumn>()
                .eq(IngDataColumn::getTableId, tableId));
        Set<String> codes = existing.stream()
                .map(c -> normCol(c.getColumnCode()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int sort = existing.stream().mapToInt(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()).max().orElse(0);
        int added = 0;
        for (String col : columns) {
            String code = sanitizeIdent(col);
            if (code == null || code.isBlank()) {
                continue;
            }
            if (codes.contains(normCol(code))) {
                continue;
            }
            IngDataColumn c = new IngDataColumn();
            c.setTableId(tableId);
            c.setColumnCode(code);
            c.setColumnName(col.trim());
            c.setDataType("TEXT");
            c.setNullableFlag(1);
            c.setBuiltInFlag(0);
            c.setSortOrder(++sort);
            dataColumnMapper.insert(c);
            codes.add(normCol(code));
            added++;
        }
        if (added > 0) {
            IngDataTable table = dataTableMapper.selectById(tableId);
            if (table != null) {
                table.setColumnCount(codes.size());
                dataTableMapper.updateById(table);
            }
        }
    }

    private String suggestOdsFromAsset(IngDataTable asset) {
        String raw = asset.getSourceTable();
        if (raw == null || raw.isBlank()) {
            raw = asset.getTableName();
        }
        if (raw == null || raw.isBlank()) {
            raw = asset.getTableCode();
        }
        String sanitized = sanitizeIdent(raw == null ? "upload" : raw);
        if (sanitized.toLowerCase(Locale.ROOT).startsWith("ods_")) {
            return sanitized;
        }
        return "ods_" + sanitized;
    }

    /** 手动上传专用 FILE 数据源：挂在本部门「其他」项目下（不存在则创建）。 */
    private IngDataSource ensureManualUploadSource(UserPrincipal operator) {
        if (operator == null || operator.getOrgId() == null) {
            throw new BusinessException(400, "当前账号未绑定部门，无法使用手动上传默认项目");
        }
        Long orgId = operator.getOrgId();
        IngProject other = ensureOtherProject(operator);
        String sourceCode = "DS_MANUAL_UPLOAD_" + orgId;
        IngDataSource existing = dataSourceMapper.selectOne(new LambdaQueryWrapper<IngDataSource>()
                .eq(IngDataSource::getSourceCode, sourceCode)
                .last("LIMIT 1"));
        // 兼容尚未迁移的旧编码
        if (existing == null && orgId == 1L) {
            existing = dataSourceMapper.selectOne(new LambdaQueryWrapper<IngDataSource>()
                    .eq(IngDataSource::getSourceCode, "DS_MANUAL_UPLOAD")
                    .last("LIMIT 1"));
            if (existing != null) {
                existing.setSourceCode(sourceCode);
            }
        }
        if (existing != null) {
            if (!other.getId().equals(existing.getProjectId())) {
                existing.setProjectId(other.getId());
            }
            if (existing.getSystemName() == null || existing.getSystemName().isBlank()) {
                existing.setSystemName("其他");
            }
            if (existing.getSourceName() == null || existing.getSourceName().isBlank()) {
                existing.setSourceName("手动上传");
            }
            dataSourceMapper.updateById(existing);
            return existing;
        }
        IngDataSource ds = new IngDataSource();
        ds.setProjectId(other.getId());
        ds.setSourceCode(sourceCode);
        ds.setSourceName("手动上传");
        ds.setSystemName("其他");
        ds.setSourceType("FILE");
        ds.setConnStatus("OK");
        ds.setTableCount(0);
        ds.setConnConfigJson("{\"channel\":\"MANUAL_UPLOAD\",\"odsDb\":\"smart_city_ods\"}");
        ds.setSourceSchema("smart_city_ods");
        dataSourceMapper.insert(ds);
        return ds;
    }

    private IngProject ensureOtherProject(UserPrincipal operator) {
        if (operator == null || operator.getOrgId() == null) {
            throw new BusinessException(400, "当前账号未绑定部门，无法初始化「其他」项目");
        }
        Long orgId = operator.getOrgId();
        String projectCode = "PRJ_OTHER_" + orgId;
        IngProject project = projectMapper.selectOne(new LambdaQueryWrapper<IngProject>()
                .eq(IngProject::getProjectCode, projectCode)
                .last("LIMIT 1"));
        if (project == null && orgId == 1L) {
            project = projectMapper.selectOne(new LambdaQueryWrapper<IngProject>()
                    .eq(IngProject::getProjectCode, "PRJ_OTHER")
                    .last("LIMIT 1"));
            if (project != null) {
                project.setProjectCode(projectCode);
                project.setBoundOrgId(orgId);
                project.setProjectName("其他");
                projectMapper.updateById(project);
            }
        }
        if (project != null) {
            return project;
        }
        project = new IngProject();
        project.setProjectCode(projectCode);
        project.setProjectName("其他");
        project.setSystemName("其他");
        project.setBoundOrgId(orgId);
        project.setStatus("ACTIVE");
        project.setCreatedBy(operator.getUsername() != null ? operator.getUsername() : "system");
        projectMapper.insert(project);
        return project;
    }

    /** 按模板新建数据资产；sourceId 指定 FILE 数据源，缺省挂到本部门默认「手动上传」。 */
    private IngDataTable createUploadAsset(UserPrincipal operator, String assetName, String targetTable, String sheetName,
                                           List<String> columns, Long sourceId) {
        IngDataSource ds;
        if (sourceId != null) {
            ds = dataSourceMapper.selectById(sourceId);
            if (ds == null) {
                throw new BusinessException(404, "数据源不存在: " + sourceId);
            }
            if (!"FILE".equalsIgnoreCase(ds.getSourceType())) {
                throw new BusinessException(400, "手动上传资产只能挂到 FILE 类型数据源（系统）下");
            }
        } else {
            ds = ensureManualUploadSource(operator);
        }
        IngDataTable t = new IngDataTable();
        t.setSourceId(ds.getId());
        t.setTableCode("TBL_UP_" + System.currentTimeMillis());
        String name = assetName == null || assetName.isBlank() ? targetTable : assetName.trim();
        t.setTableName(name);
        t.setUsageDesc("手动上传 · " + sheetName);
        t.setPhysicalTableName(targetTable);
        t.setSourceSchema("smart_city_ods");
        t.setSourceTable(targetTable);
        t.setModelingMode("REVERSE");
        t.setColumnCount(0);
        t.setStatus("ACTIVE");
        t.setCollectStatus("IDLE");
        dataTableMapper.insert(t);

        Integer cnt = ds.getTableCount() == null ? 0 : ds.getTableCount();
        ds.setTableCount(cnt + 1);
        dataSourceMapper.updateById(ds);
        return t;
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
