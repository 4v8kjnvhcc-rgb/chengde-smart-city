package com.chengde.smartcity.masterdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.storage.StorageIntegrationClient;
import com.chengde.smartcity.system.entity.AuditLog;
import com.chengde.smartcity.system.mapper.AuditLogMapper;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.RcAssetCatalogEntry;
import com.chengde.smartcity.masterdata.entity.RcBackupArtifact;
import com.chengde.smartcity.masterdata.entity.RcBackupJob;
import com.chengde.smartcity.masterdata.entity.RcBaseLibrary;
import com.chengde.smartcity.masterdata.entity.RcCatalogExchangeJob;
import com.chengde.smartcity.masterdata.entity.RcManagedTable;
import com.chengde.smartcity.masterdata.entity.RcMonitorMetric;
import com.chengde.smartcity.masterdata.entity.RcPartitionDef;
import com.chengde.smartcity.masterdata.entity.RcPartitionOp;
import com.chengde.smartcity.masterdata.entity.RcPolicyRunLog;
import com.chengde.smartcity.masterdata.entity.RcStoragePolicy;
import com.chengde.smartcity.masterdata.entity.RcThemeLibrary;
import com.chengde.smartcity.masterdata.entity.UnsDocument;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.RcAssetCatalogEntryMapper;
import com.chengde.smartcity.masterdata.mapper.RcBackupArtifactMapper;
import com.chengde.smartcity.masterdata.mapper.RcBackupJobMapper;
import com.chengde.smartcity.masterdata.mapper.RcBaseLibraryMapper;
import com.chengde.smartcity.masterdata.mapper.RcCatalogExchangeJobMapper;
import com.chengde.smartcity.masterdata.mapper.RcManagedTableMapper;
import com.chengde.smartcity.masterdata.mapper.RcMonitorMetricMapper;
import com.chengde.smartcity.masterdata.mapper.RcPartitionDefMapper;
import com.chengde.smartcity.masterdata.mapper.RcPartitionOpMapper;
import com.chengde.smartcity.masterdata.mapper.RcPolicyRunLogMapper;
import com.chengde.smartcity.masterdata.mapper.RcStoragePolicyMapper;
import com.chengde.smartcity.masterdata.mapper.RcThemeLibraryMapper;
import com.chengde.smartcity.masterdata.mapper.UnsDocumentMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceCenterPlatformService {

    private static final Logger log = LoggerFactory.getLogger(ResourceCenterPlatformService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Path BACKUP_ROOT = Path.of("data", "nas-demo", "backups");
    private static final Path ARCHIVE_ROOT = Path.of("data", "nas-demo", "archives");
    private static final Path OBJECT_ROOT = Path.of("data", "nas-demo", "object-backups");

    /** 门户子系统编码（公开目录共享；未公开目录按子系统隔离） */
    private static final List<String[]> CATALOG_SUBSYSTEMS = List.of(
            new String[]{"SHARED", "共享公开目录"},
            new String[]{"RESOURCE", "大数据平台资源中心"},
            new String[]{"EXCHANGE", "数据共享交换平台"},
            new String[]{"GOVERNANCE", "数据融合治理平台"},
            new String[]{"CATALOG", "数据目录管理系统"},
            new String[]{"UNSTRUCTURED", "非结构数据融合治理平台"},
            new String[]{"ANALYTICS", "大数据挖掘分析平台"}
    );
    private static final Set<String> ENCRYPT_ALGOS = Set.of("NONE", "AES256", "SM4");

    /** V3.0 数据资产中心固定模块（zone_code） */
    private static final List<String[]> ASSET_MODULES = List.of(
            new String[]{"MODULE_POPULATION", "MOD_POPULATION", "人口库数据中心"},
            new String[]{"MODULE_LEGAL", "MOD_LEGAL", "法人库数据中心"},
            new String[]{"MODULE_LICENSE", "MOD_LICENSE", "电子证照库数据中心"},
            new String[]{"MODULE_MACRO", "MOD_MACRO", "宏观经济库数据中心"},
            new String[]{"MODULE_ENTERPRISE", "MOD_ENTERPRISE", "企业经济库数据中心"},
            new String[]{"MODULE_GEO", "MOD_GEO", "地理信息库数据中心"},
            new String[]{"MODULE_CITYPART", "MOD_CITYPART", "城市部件库数据中心"},
            new String[]{"MODULE_TECH", "MOD_TECH", "科技资源库数据中心"},
            new String[]{"MODULE_OTHER", "MOD_OTHER", "其他业务基础库数据中心"},
            new String[]{"MODULE_APPROVAL", "MOD_APPROVAL", "行政审批库数据中心"}
    );

    private final RcBaseLibraryMapper libraryMapper;
    private final RcPartitionDefMapper partitionMapper;
    private final RcPartitionOpMapper partitionOpMapper;
    private final RcStoragePolicyMapper policyMapper;
    private final RcAssetCatalogEntryMapper catalogMapper;
    private final RcMonitorMetricMapper monitorMapper;
    private final RcThemeLibraryMapper themeMapper;
    private final RcManagedTableMapper managedTableMapper;
    private final RcBackupJobMapper backupJobMapper;
    private final RcBackupArtifactMapper backupArtifactMapper;
    private final RcPolicyRunLogMapper policyRunLogMapper;
    private final RcCatalogExchangeJobMapper catalogExchangeJobMapper;
    private final GovMetadataRegistryMapper registryMapper;
    private final GovCatalogResourceMapper catalogResourceMapper;
    private final UnsDocumentMapper unsDocumentMapper;
    private final AuditService auditService;
    private final AuditLogMapper auditLogMapper;
    private final StorageIntegrationClient storageIntegrationClient;
    private final DataSource platformDataSource;
    private final StorageLifecycleService storageLifecycleService;

    public ResourceCenterPlatformService(RcBaseLibraryMapper libraryMapper,
                                         RcPartitionDefMapper partitionMapper,
                                         RcPartitionOpMapper partitionOpMapper,
                                         RcStoragePolicyMapper policyMapper,
                                         RcAssetCatalogEntryMapper catalogMapper,
                                         RcMonitorMetricMapper monitorMapper,
                                         RcThemeLibraryMapper themeMapper,
                                         RcManagedTableMapper managedTableMapper,
                                         RcBackupJobMapper backupJobMapper,
                                         RcBackupArtifactMapper backupArtifactMapper,
                                         RcPolicyRunLogMapper policyRunLogMapper,
                                         RcCatalogExchangeJobMapper catalogExchangeJobMapper,
                                         GovMetadataRegistryMapper registryMapper,
                                         GovCatalogResourceMapper catalogResourceMapper,
                                         UnsDocumentMapper unsDocumentMapper,
                                         AuditService auditService,
                                         AuditLogMapper auditLogMapper,
                                         StorageIntegrationClient storageIntegrationClient,
                                         DataSource platformDataSource,
                                         @Lazy StorageLifecycleService storageLifecycleService) {
        this.libraryMapper = libraryMapper;
        this.partitionMapper = partitionMapper;
        this.partitionOpMapper = partitionOpMapper;
        this.policyMapper = policyMapper;
        this.catalogMapper = catalogMapper;
        this.monitorMapper = monitorMapper;
        this.themeMapper = themeMapper;
        this.managedTableMapper = managedTableMapper;
        this.backupJobMapper = backupJobMapper;
        this.backupArtifactMapper = backupArtifactMapper;
        this.policyRunLogMapper = policyRunLogMapper;
        this.catalogExchangeJobMapper = catalogExchangeJobMapper;
        this.registryMapper = registryMapper;
        this.catalogResourceMapper = catalogResourceMapper;
        this.unsDocumentMapper = unsDocumentMapper;
        this.auditService = auditService;
        this.auditLogMapper = auditLogMapper;
        this.storageIntegrationClient = storageIntegrationClient;
        this.platformDataSource = platformDataSource;
        this.storageLifecycleService = storageLifecycleService;
    }

    public List<Map<String, String>> listLifecycleDatabases() {
        return storageLifecycleService.listSourceDatabases();
    }

    public List<String> listLifecycleTables(String database) {
        return storageLifecycleService.listTables(database);
    }

    public Map<String, Object> libraryOverview() {
        List<RcBaseLibrary> base = libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>()
                .eq(RcBaseLibrary::getLibType, "BASE")
                .ne(RcBaseLibrary::getStatus, "OFFLINE")
                .orderByAsc(RcBaseLibrary::getSortOrder).orderByAsc(RcBaseLibrary::getId));
        List<RcBaseLibrary> semi = libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>()
                .eq(RcBaseLibrary::getLibType, "SEMI")
                .ne(RcBaseLibrary::getStatus, "OFFLINE")
                .orderByAsc(RcBaseLibrary::getSortOrder).orderByAsc(RcBaseLibrary::getId));
        List<RcBaseLibrary> unstruct = libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>()
                .eq(RcBaseLibrary::getLibType, "UNSTRUCT")
                .ne(RcBaseLibrary::getStatus, "OFFLINE")
                .orderByAsc(RcBaseLibrary::getSortOrder).orderByAsc(RcBaseLibrary::getId));
        List<Map<String, Object>> managed = listManagedTables(null);
        Map<String, Object> inventory = buildInventory(base, semi, unstruct, managed);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseLibraries", enrichLibraries(base, managed));
        out.put("semiLibraries", enrichLibraries(semi, managed));
        out.put("unstructLibraries", enrichLibraries(unstruct, managed));
        out.put("themes", listThemes(null));
        out.put("managedTables", managed);
        out.put("modules", listAssetModules());
        out.put("inventory", inventory);
        out.put("lifecycleHints", Map.of(
                "backup", "过期数据定时迁入同机备份库（*_bak），请到「数据备份」配置周期",
                "archive", "从备份库定时打成 gzip 落到宿主机归档盘",
                "restore", "备份产物可恢复到独立表，不覆盖源表",
                "migrate", "销毁只删备份库过期行与归档文件，不删源业务表"
        ));
        return out;
    }

    /** 多角度盘点：按库类型 / 数据中心模块 / 纳管表 */
    public Map<String, Object> assetInventory() {
        return libraryOverview();
    }

    public List<Map<String, Object>> listAssetModules() {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<Long> claimedThemeIds = new HashSet<>();
        for (String[] def : ASSET_MODULES) {
            String zone = def[0];
            String code = def[1];
            String name = def[2];
            // 同一数据中心可能有多条主题/专题库（种子 MOD_* + 用户新建同名/同库区），须全部聚合纳管表
            List<RcThemeLibrary> matched = themeMapper.selectList(new LambdaQueryWrapper<RcThemeLibrary>()
                    .and(w -> w.eq(RcThemeLibrary::getThemeCode, code)
                            .or().eq(RcThemeLibrary::getZoneCode, zone)
                            .or().eq(RcThemeLibrary::getThemeName, name))
                    .ne(RcThemeLibrary::getStatus, "OFFLINE")
                    .orderByAsc(RcThemeLibrary::getId));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("moduleCode", code);
            m.put("moduleName", name);
            m.put("zoneCode", zone);
            if (!matched.isEmpty()) {
                for (RcThemeLibrary t : matched) {
                    claimedThemeIds.add(t.getId());
                }
                List<Long> themeIds = matched.stream().map(RcThemeLibrary::getId).toList();
                List<Map<String, Object>> tables = listManagedTablesByThemeIds(themeIds);
                RcThemeLibrary primary = matched.stream()
                        .filter(t -> code.equalsIgnoreCase(t.getThemeCode()))
                        .findFirst()
                        .orElse(matched.get(0));
                m.put("themeId", primary.getId());
                m.put("themeCode", primary.getThemeCode());
                m.put("themeName", primary.getThemeName());
                m.put("ownerOrg", primary.getOwnerOrg());
                m.put("description", primary.getDescription());
                m.put("status", primary.getStatus());
                m.put("themeIds", themeIds);
                m.put("managedCount", tables.size());
                m.put("tables", tables);
            } else {
                m.put("themeId", null);
                m.put("status", "DRAFT");
                m.put("managedCount", 0);
                m.put("tables", List.of());
                m.put("themeIds", List.of());
            }
            out.add(m);
        }
        // 未归入十大中心的纳管表（如挂在 ZONE_THEME 企业主题库）单独列出，避免「纳管成功但模块页看不到」
        List<RcManagedTable> allActive = managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getStatus, "ACTIVE")
                .orderByDesc(RcManagedTable::getId));
        List<Map<String, Object>> orphanTables = new ArrayList<>();
        for (RcManagedTable mt : allActive) {
            if (mt.getThemeId() == null || !claimedThemeIds.contains(mt.getThemeId())) {
                orphanTables.add(managedRow(mt));
            }
        }
        Map<String, Object> orphan = new LinkedHashMap<>();
        orphan.put("moduleCode", "MOD_UNASSIGNED");
        orphan.put("moduleName", "未归入数据中心");
        orphan.put("zoneCode", "ZONE_UNASSIGNED");
        orphan.put("themeId", null);
        orphan.put("ownerOrg", "—");
        orphan.put("description", "已纳管但主题未挂到人口/法人等数据中心库区；请在「数据资产管理与分类」将主题选为对应中心后再纳管，或调整主题库区");
        orphan.put("status", orphanTables.isEmpty() ? "ACTIVE" : "WARN");
        orphan.put("managedCount", orphanTables.size());
        orphan.put("tables", orphanTables);
        orphan.put("themeIds", List.of());
        out.add(orphan);
        return out;
    }

    private List<Map<String, Object>> listManagedTablesByThemeIds(List<Long> themeIds) {
        if (themeIds == null || themeIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        List<RcManagedTable> rows = managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                .in(RcManagedTable::getThemeId, themeIds)
                .eq(RcManagedTable::getStatus, "ACTIVE")
                .orderByDesc(RcManagedTable::getId));
        for (RcManagedTable mt : rows) {
            out.add(managedRow(mt));
        }
        return out;
    }

    /** 文件目录库 / 文件索引库 + 非结构化关联结构化表 */
    public Map<String, Object> fileLibrariesOverview() {
        List<UnsDocument> docs = unsDocumentMapper.selectList(new LambdaQueryWrapper<UnsDocument>()
                .orderByDesc(UnsDocument::getId).last("LIMIT 200"));
        List<Map<String, Object>> catalog = new ArrayList<>();
        List<Map<String, Object>> index = new ArrayList<>();
        for (UnsDocument d : docs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("title", d.getTitle());
            row.put("docCode", d.getDocCode());
            row.put("storageKey", d.getStorageKey());
            row.put("categoryCode", d.getCategoryCode());
            row.put("publishStatus", d.getPublishStatus());
            row.put("indexStatus", d.getIndexStatus());
            row.put("linkedDocId", d.getLinkedDocId());
            row.put("updatedAt", d.getUpdatedAt());
            if (d.getStorageKey() != null && !d.getStorageKey().isBlank()) {
                catalog.add(row);
            }
            if ("INDEXED".equalsIgnoreCase(d.getIndexStatus()) || "SUCCESS".equalsIgnoreCase(d.getIndexStatus())) {
                index.add(row);
            }
        }
        List<Map<String, Object>> relatedStructured = listManagedTables(null).stream()
                .filter(t -> {
                    String at = String.valueOf(t.getOrDefault("assetType", ""));
                    String kind = String.valueOf(t.getOrDefault("libraryKind", ""));
                    return "UNSTRUCT".equalsIgnoreCase(at) || "SEMI".equalsIgnoreCase(at)
                            || "TOPIC".equalsIgnoreCase(kind);
                })
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("catalogLib", libraryMapper.selectOne(new LambdaQueryWrapper<RcBaseLibrary>()
                .eq(RcBaseLibrary::getLibCode, "LIB_UNS_CATALOG").last("LIMIT 1")));
        out.put("indexLib", libraryMapper.selectOne(new LambdaQueryWrapper<RcBaseLibrary>()
                .eq(RcBaseLibrary::getLibCode, "LIB_UNS_INDEX").last("LIMIT 1")));
        out.put("catalogCount", catalog.size());
        out.put("indexCount", index.size());
        out.put("documentCount", docs.size());
        out.put("catalogDocs", catalog);
        out.put("indexDocs", index);
        out.put("relatedStructuredTables", relatedStructured);
        out.put("hint", "目录库维护存储键与发布态；索引库维护检索态；关联结构化表用于非结构化与结构化互查");
        return out;
    }

    public List<RcBaseLibrary> listLibraries(String libType) {
        LambdaQueryWrapper<RcBaseLibrary> q = new LambdaQueryWrapper<RcBaseLibrary>()
                .ne(RcBaseLibrary::getStatus, "OFFLINE")
                .orderByAsc(RcBaseLibrary::getSortOrder)
                .orderByAsc(RcBaseLibrary::getId);
        if (libType != null && !libType.isBlank()) q.eq(RcBaseLibrary::getLibType, libType);
        return libraryMapper.selectList(q);
    }

    @Transactional
    public Long createLibrary(UserPrincipal operator, Map<String, Object> body) {
        RcBaseLibrary lib = new RcBaseLibrary();
        lib.setLibCode(str(body.get("libCode"), "LIB_" + System.currentTimeMillis()));
        lib.setLibName(required(body.get("libName"), "libName").toString());
        lib.setLibType(str(body.get("libType"), "BASE").toUpperCase(Locale.ROOT));
        lib.setRecordCount(0);
        lib.setStatus(str(body.get("status"), "ACTIVE").toUpperCase(Locale.ROOT));
        lib.setDescription(str(body.get("description"), null));
        lib.setOwnerOrg(str(body.get("ownerOrg"), null));
        lib.setSortOrder(intVal(body.get("sortOrder"), 999));
        libraryMapper.insert(lib);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_LIB_CREATE", "rc_base_library", String.valueOf(lib.getId()), lib.getLibCode());
        return lib.getId();
    }

    @Transactional
    public RcBaseLibrary updateLibrary(UserPrincipal operator, Long id, Map<String, Object> body) {
        RcBaseLibrary lib = libraryMapper.selectById(id);
        if (lib == null) {
            throw new BusinessException(404, "库不存在");
        }
        if (body.get("libName") != null) {
            lib.setLibName(required(body.get("libName"), "libName").toString());
        }
        if (body.get("libType") != null) {
            lib.setLibType(str(body.get("libType"), lib.getLibType()).toUpperCase(Locale.ROOT));
        }
        if (body.containsKey("description")) {
            lib.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("ownerOrg")) {
            lib.setOwnerOrg(str(body.get("ownerOrg"), null));
        }
        if (body.get("sortOrder") != null) {
            lib.setSortOrder(intVal(body.get("sortOrder"), lib.getSortOrder()));
        }
        if (body.get("status") != null) {
            lib.setStatus(str(body.get("status"), lib.getStatus()).toUpperCase(Locale.ROOT));
        }
        if (body.get("libCode") != null) {
            String code = String.valueOf(body.get("libCode")).trim();
            if (!code.isEmpty() && !code.equals(lib.getLibCode())) {
                Long dup = libraryMapper.selectCount(new LambdaQueryWrapper<RcBaseLibrary>()
                        .eq(RcBaseLibrary::getLibCode, code)
                        .ne(RcBaseLibrary::getId, id));
                if (dup != null && dup > 0) {
                    throw new BusinessException(400, "库编码已存在");
                }
                lib.setLibCode(code);
            }
        }
        libraryMapper.updateById(lib);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_LIBRARY_UPDATE", "rc_base_library", String.valueOf(id), lib.getLibName());
        return lib;
    }

    @Transactional
    public void deleteLibrary(UserPrincipal operator, Long id) {
        RcBaseLibrary lib = libraryMapper.selectById(id);
        if (lib == null) {
            throw new BusinessException(404, "库不存在");
        }
        long bound = managedTableMapper.selectCount(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getLibId, id)
                .eq(RcManagedTable::getStatus, "ACTIVE"));
        if (bound > 0) {
            throw new BusinessException(400, "库下仍有 " + bound + " 张纳管表，请先解绑或调整所属库后再删除");
        }
        lib.setStatus("OFFLINE");
        libraryMapper.updateById(lib);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_LIBRARY_DELETE", "rc_base_library", String.valueOf(id), lib.getLibName());
    }

    public List<Map<String, Object>> listThemes(String libraryKind) {
        LambdaQueryWrapper<RcThemeLibrary> q = new LambdaQueryWrapper<RcThemeLibrary>()
                .ne(RcThemeLibrary::getStatus, "OFFLINE")
                .orderByAsc(RcThemeLibrary::getId);
        if (libraryKind != null && !libraryKind.isBlank()) q.eq(RcThemeLibrary::getLibraryKind, libraryKind);
        List<Map<String, Object>> out = new ArrayList<>();
        for (RcThemeLibrary t : themeMapper.selectList(q)) {
            Map<String, Object> m = themeRow(t);
            m.put("managedCount", managedTableMapper.selectCount(new LambdaQueryWrapper<RcManagedTable>()
                    .eq(RcManagedTable::getThemeId, t.getId())
                    .eq(RcManagedTable::getStatus, "ACTIVE")));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Long createTheme(UserPrincipal operator, Map<String, Object> body) {
        RcThemeLibrary t = new RcThemeLibrary();
        t.setThemeCode(str(body.get("themeCode"), "THEME_" + System.currentTimeMillis()));
        t.setThemeName(required(body.get("themeName"), "themeName").toString());
        t.setLibraryKind(str(body.get("libraryKind"), "THEME").toUpperCase(Locale.ROOT));
        t.setZoneCode(str(body.get("zoneCode"), "ZONE_" + t.getLibraryKind()));
        t.setOwnerOrg(str(body.get("ownerOrg"), null));
        t.setDescription(str(body.get("description"), null));
        t.setPartitionKey(str(body.get("partitionKey"), null));
        t.setStatus("ACTIVE");
        t.setCreatedBy(operator.getUsername());
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        themeMapper.insert(t);
        return t.getId();
    }

    @Transactional
    public Map<String, Object> updateTheme(UserPrincipal operator, Long id, Map<String, Object> body) {
        RcThemeLibrary t = themeMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "主题/专题库不存在");
        }
        if (body.get("themeName") != null) {
            t.setThemeName(required(body.get("themeName"), "themeName").toString());
        }
        if (body.get("libraryKind") != null) {
            t.setLibraryKind(str(body.get("libraryKind"), t.getLibraryKind()).toUpperCase(Locale.ROOT));
        }
        if (body.containsKey("zoneCode")) {
            t.setZoneCode(str(body.get("zoneCode"), t.getZoneCode()));
        }
        if (body.containsKey("ownerOrg")) {
            t.setOwnerOrg(str(body.get("ownerOrg"), null));
        }
        if (body.containsKey("description")) {
            t.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("partitionKey")) {
            t.setPartitionKey(str(body.get("partitionKey"), null));
        }
        if (body.get("status") != null) {
            t.setStatus(str(body.get("status"), t.getStatus()).toUpperCase(Locale.ROOT));
        }
        t.setUpdatedAt(LocalDateTime.now());
        themeMapper.updateById(t);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_THEME_UPDATE", "rc_theme_library", String.valueOf(id), t.getThemeName());
        return themeRow(t);
    }

    @Transactional
    public void deleteTheme(UserPrincipal operator, Long id) {
        RcThemeLibrary t = themeMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "主题/专题库不存在");
        }
        long bound = managedTableMapper.selectCount(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getThemeId, id)
                .eq(RcManagedTable::getStatus, "ACTIVE"));
        if (bound > 0) {
            throw new BusinessException(400, "主题下仍有 " + bound + " 张纳管表，请先解绑后再删除");
        }
        t.setStatus("OFFLINE");
        t.setUpdatedAt(LocalDateTime.now());
        themeMapper.updateById(t);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_THEME_DELETE", "rc_theme_library", String.valueOf(id), t.getThemeName());
    }

    /** 调整纳管表所属主题/资源类型（资产分类纠偏）。 */
    @Transactional
    public Map<String, Object> updateManagedTable(UserPrincipal operator, Long id, Map<String, Object> body) {
        RcManagedTable mt = managedTableMapper.selectById(id);
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(404, "纳管表不存在");
        }
        if (body.get("themeId") != null) {
            Long themeId = longVal(body.get("themeId"));
            if (themeId == null || themeMapper.selectById(themeId) == null) {
                throw new BusinessException(404, "主题/专题库不存在");
            }
            mt.setThemeId(themeId);
        }
        if (body.containsKey("libId")) {
            Long libId = longVal(body.get("libId"));
            if (libId != null) {
                RcBaseLibrary lib = libraryMapper.selectById(libId);
                if (lib == null) {
                    throw new BusinessException(404, "关联库不存在");
                }
                mt.setLibId(libId);
                if (body.get("assetType") == null || str(body.get("assetType"), "").isBlank()) {
                    mt.setAssetType(lib.getLibType());
                }
            } else {
                mt.setLibId(null);
            }
        }
        if (body.get("assetType") != null && !str(body.get("assetType"), "").isBlank()) {
            mt.setAssetType(str(body.get("assetType"), "BASE").toUpperCase(Locale.ROOT));
        }
        mt.setUpdatedAt(LocalDateTime.now());
        managedTableMapper.updateById(mt);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_MANAGED_UPDATE", "rc_managed_table", String.valueOf(id), mt.getPhysicalTable());
        return managedRow(mt);
    }

    public List<Map<String, Object>> listManagedTables(Long themeId) {
        LambdaQueryWrapper<RcManagedTable> q = new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getStatus, "ACTIVE")
                .orderByDesc(RcManagedTable::getId);
        if (themeId != null) q.eq(RcManagedTable::getThemeId, themeId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (RcManagedTable mt : managedTableMapper.selectList(q)) {
            out.add(managedRow(mt));
        }
        return out;
    }

    public List<Map<String, Object>> candidateProduceTables() {
        List<GovMetadataRegistry> entries = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .orderByDesc(GovMetadataRegistry::getId)
                .last("LIMIT 100"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetadataRegistry e : entries) {
            if (e.getPhysicalTableName() == null || e.getPhysicalTableName().isBlank()) continue;
            String layer = e.getDataLayer();
            if (layer == null || layer.isBlank()) {
                layer = e.getDatabaseName() != null
                        ? com.chengde.smartcity.masterdata.support.DataLayerSupport.layerForDatabase(e.getDatabaseName())
                        : com.chengde.smartcity.masterdata.support.DataLayerSupport.layerForTableName(e.getPhysicalTableName());
            }
            // 资源中心纳管：源/主题/专题；过程 DWD 不作为可共享资产纳管首选
            if ("DWD".equalsIgnoreCase(layer)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entryCode", e.getEntryCode());
            m.put("entryName", e.getEntryName());
            m.put("physicalTable", e.getPhysicalTableName());
            m.put("dataLayer", layer);
            m.put("managed", managedTableMapper.selectCount(new LambdaQueryWrapper<RcManagedTable>()
                    .eq(RcManagedTable::getPhysicalTable, e.getPhysicalTableName())
                    .eq(RcManagedTable::getStatus, "ACTIVE")) > 0);
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Long autoManageAfterFusion(UserPrincipal operator, String themeCode, String physicalTable, String metaEntryCode) {
        if (themeCode == null || themeCode.isBlank() || physicalTable == null || physicalTable.isBlank()) {
            return null;
        }
        RcThemeLibrary theme = themeMapper.selectOne(new LambdaQueryWrapper<RcThemeLibrary>()
                .eq(RcThemeLibrary::getThemeCode, themeCode)
                .last("LIMIT 1"));
        if (theme == null) return null;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("themeId", theme.getId());
        body.put("physicalTable", physicalTable);
        if (metaEntryCode != null && !metaEntryCode.isBlank()) {
            body.put("metaEntryCode", metaEntryCode);
        }
        return manageTable(operator, body);
    }

    @Transactional
    public Long manageTable(UserPrincipal operator, Map<String, Object> body) {
        Long themeId = longVal(body.get("themeId"));
        String physical = requireIdent(str(body.get("physicalTable"), null), "physicalTable");
        if (themeId == null) throw new BusinessException(400, "请选择主题/专题库");
        if (themeMapper.selectById(themeId) == null) throw new BusinessException(404, "主题/专题库不存在");
        if (!tableExists(physical)) throw new BusinessException(404, "物理表不存在: " + physical);
        String meta = str(body.get("metaEntryCode"), findMetaEntry(physical));
        if (meta == null || meta.isBlank()) {
            throw new BusinessException(400, "须选择已登记元数据条目（metaEntryCode），禁止空挂载");
        }
        GovMetadataRegistry entry = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, meta)
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .last("LIMIT 1"));
        if (entry == null) {
            throw new BusinessException(400, "元数据条目不存在或已下线：" + meta);
        }

        RcManagedTable existing = managedTableMapper.selectOne(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getPhysicalTable, physical).last("LIMIT 1"));
        boolean creating = existing == null;
        RcManagedTable mt = creating ? new RcManagedTable() : existing;
        mt.setThemeId(themeId);
        mt.setPhysicalTable(physical);
        mt.setMetaEntryCode(meta);
        Long libId = longVal(body.get("libId"));
        String assetType = str(body.get("assetType"), null);
        if (libId != null) {
            RcBaseLibrary lib = libraryMapper.selectById(libId);
            if (lib == null) throw new BusinessException(404, "关联库不存在");
            mt.setLibId(libId);
            if (assetType == null || assetType.isBlank()) {
                assetType = lib.getLibType();
            }
        }
        if (assetType != null && !assetType.isBlank()) {
            mt.setAssetType(assetType.toUpperCase(Locale.ROOT));
        } else if (mt.getAssetType() == null) {
            mt.setAssetType("BASE");
        }
        mt.setCatalogResourceCode(str(body.get("catalogResourceCode"), findCatalogCode(physical)));
        mt.setFusionPhysicalId(longVal(body.get("fusionPhysicalId")));
        mt.setStatus("ACTIVE");
        mt.setUpdatedAt(LocalDateTime.now());
        if (creating) {
            mt.setCreatedBy(operator.getUsername());
            mt.setCreatedAt(LocalDateTime.now());
            managedTableMapper.insert(mt);
        } else {
            managedTableMapper.updateById(mt);
        }
        refreshTableStats(mt);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_MANAGE_TABLE", "rc_managed_table", String.valueOf(mt.getId()), physical);
        return mt.getId();
    }

    @Transactional
    public void unmanageTable(UserPrincipal operator, Long id) {
        RcManagedTable mt = managedTableMapper.selectById(id);
        if (mt == null) throw new BusinessException(404, "纳管表不存在");
        mt.setStatus("OFFLINE");
        mt.setUpdatedAt(LocalDateTime.now());
        managedTableMapper.updateById(mt);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_UNMANAGE_TABLE", "rc_managed_table", String.valueOf(id), mt.getPhysicalTable());
    }

    public Map<String, Object> partitionOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("partitions", partitionMapper.selectList(new LambdaQueryWrapper<RcPartitionDef>()
                .ne(RcPartitionDef::getStatus, "OFFLINE")
                .orderByAsc(RcPartitionDef::getId)));
        out.put("ops", partitionOpMapper.selectList(new LambdaQueryWrapper<RcPartitionOp>()
                .orderByDesc(RcPartitionOp::getId).last("LIMIT 100")));
        out.put("policies", policyMapper.selectList(new LambdaQueryWrapper<RcStoragePolicy>().orderByAsc(RcStoragePolicy::getId)));
        out.put("backups", backupJobMapper.selectList(new LambdaQueryWrapper<RcBackupJob>().orderByDesc(RcBackupJob::getId)));
        out.put("artifacts", backupArtifactMapper.selectList(new LambdaQueryWrapper<RcBackupArtifact>()
                .orderByDesc(RcBackupArtifact::getId).last("LIMIT 50")));
        out.put("monitorSummary", summarizePartitionMonitor());
        return out;
    }

    @Transactional
    public Long createPartition(UserPrincipal operator, Map<String, Object> body) {
        String tableName = str(body.get("tableName"), null);
        if (tableName == null || tableName.isBlank()) {
            throw new BusinessException(400, "请选择已纳管的目标表");
        }
        requireIdent(tableName, "tableName");
        RcManagedTable mt = requireActiveManagedByTable(tableName);
        String type = str(body.get("partitionType"), "RANGE").toUpperCase(Locale.ROOT);
        if (!Set.of("RANGE", "HASH", "LIST").contains(type)) {
            throw new BusinessException(400, "分区类型须为 RANGE / HASH / LIST");
        }
        String column = str(body.get("partitionColumn"), null);
        if (column != null && !column.isBlank()) {
            requireIdent(column, "partitionColumn");
        }
        RcPartitionDef p = new RcPartitionDef();
        p.setPartitionCode(str(body.get("partitionCode"), "PART_" + System.currentTimeMillis()));
        p.setPartitionName(required(body.get("partitionName"), "partitionName").toString());
        p.setPartitionType(type);
        p.setThemeId(longVal(body.get("themeId")) != null ? longVal(body.get("themeId")) : mt.getThemeId());
        p.setTableName(tableName);
        p.setPartitionColumn(column);
        p.setExpressionText(str(body.get("expressionText"), null));
        p.setRemark(str(body.get("remark"), null));
        p.setPretestStatus("DRAFT");
        p.setStatus("ACTIVE");
        p.setUpdatedAt(LocalDateTime.now());
        partitionMapper.insert(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_PARTITION_CREATE", "rc_partition_def", String.valueOf(p.getId()), tableName);
        return p.getId();
    }

    @Transactional
    public void updatePartition(UserPrincipal operator, Long id, Map<String, Object> body) {
        RcPartitionDef p = partitionMapper.selectById(id);
        if (p == null || "OFFLINE".equalsIgnoreCase(p.getStatus())) {
            throw new BusinessException(404, "分区策略不存在");
        }
        if (body.get("partitionName") != null) {
            p.setPartitionName(required(body.get("partitionName"), "partitionName").toString());
        }
        if (body.get("partitionType") != null) {
            String type = str(body.get("partitionType"), p.getPartitionType()).toUpperCase(Locale.ROOT);
            if (!Set.of("RANGE", "HASH", "LIST").contains(type)) {
                throw new BusinessException(400, "分区类型须为 RANGE / HASH / LIST");
            }
            p.setPartitionType(type);
        }
        if (body.get("tableName") != null) {
            String tableName = requireIdent(str(body.get("tableName"), null), "tableName");
            RcManagedTable mt = requireActiveManagedByTable(tableName);
            p.setTableName(tableName);
            p.setThemeId(mt.getThemeId());
        }
        if (body.get("partitionColumn") != null) {
            String column = str(body.get("partitionColumn"), null);
            if (column != null && !column.isBlank()) {
                requireIdent(column, "partitionColumn");
            }
            p.setPartitionColumn(column);
        }
        if (body.containsKey("expressionText")) {
            p.setExpressionText(str(body.get("expressionText"), null));
        }
        if (body.containsKey("remark")) {
            p.setRemark(str(body.get("remark"), null));
        }
        p.setPretestStatus("DRAFT");
        p.setPretestMessage("策略已修改，请重新预检");
        p.setPreviewDdl(null);
        p.setUpdatedAt(LocalDateTime.now());
        partitionMapper.updateById(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_PARTITION_UPDATE", "rc_partition_def", String.valueOf(id), p.getTableName());
    }

    @Transactional
    public void deletePartition(UserPrincipal operator, Long id) {
        RcPartitionDef p = partitionMapper.selectById(id);
        if (p == null) throw new BusinessException(404, "分区策略不存在");
        p.setStatus("OFFLINE");
        p.setUpdatedAt(LocalDateTime.now());
        partitionMapper.updateById(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_PARTITION_DELETE", "rc_partition_def", String.valueOf(id), p.getTableName());
    }

    @Transactional
    public Map<String, Object> pretestPartition(UserPrincipal operator, Long id) {
        RcPartitionDef p = partitionMapper.selectById(id);
        if (p == null || "OFFLINE".equalsIgnoreCase(p.getStatus())) {
            throw new BusinessException(404, "分区策略不存在");
        }
        String table = p.getTableName();
        String column = p.getPartitionColumn();
        String type = p.getPartitionType() == null ? "RANGE" : p.getPartitionType().toUpperCase(Locale.ROOT);
        String expr = p.getExpressionText() == null ? "" : p.getExpressionText().trim();
        List<String> risks = new ArrayList<>();
        String status = "READY";
        if (table == null || !IDENT.matcher(table).matches() || !tableExists(table)) {
            status = "BLOCKED";
            risks.add("目标表不存在或非法");
        } else if (column == null || !IDENT.matcher(column).matches() || !columnExists(table, column)) {
            status = "BLOCKED";
            risks.add("分区列不存在或非法");
        } else {
            if (hasUniqueOrPrimary(table)) {
                risks.add("表存在主键/唯一键，执行物理分区前需评估键与分区键兼容性（本阶段不自动执行）");
            }
            if (isAlreadyPartitioned(table)) {
                risks.add("表已分区，重复执行 ALTER 可能失败；建议走迁移预检");
            }
            if (expr.isBlank()) {
                risks.add("未填写分区表达式，建议补充范围边界/哈希份数/列表值");
            } else if ("RANGE".equals(type) && !expr.toUpperCase(Locale.ROOT).contains("VALUES LESS THAN")
                    && !expr.toUpperCase(Locale.ROOT).contains("RANGE")) {
                risks.add("范围分区表达式建议包含 VALUES LESS THAN 或 RANGE 边界说明");
            } else if ("HASH".equals(type) && !expr.matches("(?i).*\\b\\d+\\b.*")
                    && !expr.toUpperCase(Locale.ROOT).contains("PARTITIONS")) {
                risks.add("哈希分区建议标明分区份数（如 PARTITIONS 4）");
            } else if ("LIST".equals(type) && !expr.toUpperCase(Locale.ROOT).contains("IN")
                    && !expr.toUpperCase(Locale.ROOT).contains("LIST")) {
                risks.add("列表分区表达式建议包含 IN (...) 值列表");
            }
        }
        String ddl = buildPartitionByDdl(table, type, column, expr);
        p.setPreviewDdl(ddl);
        p.setPretestStatus(status);
        p.setPretestMessage(String.join("; ", risks.isEmpty() ? List.of("预检通过，未执行物理DDL") : risks));
        p.setPretestAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        partitionMapper.updateById(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_PARTITION_PRETEST", "rc_partition_def", String.valueOf(id), p.getPretestStatus());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.getId());
        out.put("pretestStatus", p.getPretestStatus());
        out.put("pretestMessage", p.getPretestMessage());
        out.put("previewDdl", p.getPreviewDdl());
        out.put("executed", false);
        return out;
    }

    @Transactional
    public Map<String, Object> migratePartition(UserPrincipal operator, Long id, Map<String, Object> body) {
        RcPartitionDef p = partitionMapper.selectById(id);
        if (p == null || "OFFLINE".equalsIgnoreCase(p.getStatus())) {
            throw new BusinessException(404, "分区策略不存在");
        }
        String table = requireIdent(p.getTableName(), "tableName");
        String action = str(body.get("migrateAction"), "ADD").toUpperCase(Locale.ROOT);
        if (!Set.of("ADD", "DROP", "REORGANIZE").contains(action)) {
            throw new BusinessException(400, "migrateAction 须为 ADD / DROP / REORGANIZE");
        }
        String partName = str(body.get("partitionName"), "p_new");
        if (!IDENT.matcher(partName).matches()) {
            throw new BusinessException(400, "分区名非法");
        }
        String detail = str(body.get("detail"), "");
        String sql;
        if ("ADD".equals(action)) {
            sql = "ALTER TABLE `" + table + "` ADD PARTITION (PARTITION `" + partName + "` "
                    + (detail.isBlank() ? "VALUES LESS THAN (MAXVALUE)" : detail) + ")";
        } else if ("DROP".equals(action)) {
            sql = "ALTER TABLE `" + table + "` DROP PARTITION `" + partName + "`";
        } else {
            String target = str(body.get("targetPartition"), "p_reorg");
            sql = "ALTER TABLE `" + table + "` REORGANIZE PARTITION `" + partName + "` INTO ("
                    + (detail.isBlank() ? ("PARTITION `" + target + "` VALUES LESS THAN (MAXVALUE)") : detail) + ")";
        }
        sql = sql + " -- DRY-RUN ONLY, NOT EXECUTED";
        RcManagedTable mt = findActiveManagedByTable(table);
        RcPartitionOp op = new RcPartitionOp();
        op.setPartitionDefId(p.getId());
        op.setManagedTableId(mt == null ? null : mt.getId());
        op.setPhysicalTable(table);
        op.setOpType("MIGRATE");
        op.setOpStatus("LEDGER");
        op.setPreviewSql(sql);
        op.setMessage("分区迁移候选DDL已登记，未执行物理变更；动作=" + action);
        op.setCreatedBy(operator.getUsername());
        op.setCreatedAt(LocalDateTime.now());
        partitionOpMapper.insert(op);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_PARTITION_MIGRATE", "rc_partition_op", String.valueOf(op.getId()), action);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("opId", op.getId());
        out.put("opStatus", op.getOpStatus());
        out.put("previewSql", op.getPreviewSql());
        out.put("message", op.getMessage());
        out.put("executed", false);
        return out;
    }

    public List<Map<String, Object>> listManagedTableColumns(Long managedTableId) {
        RcManagedTable mt = managedTableMapper.selectById(managedTableId);
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(404, "纳管表不存在");
        }
        String table = requireIdent(mt.getPhysicalTable(), "physicalTable");
        List<Map<String, Object>> cols = new ArrayList<>();
        try (Connection c = platformDataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(c.getCatalog(), null, table, null)) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("columnName", rs.getString("COLUMN_NAME"));
                    row.put("dataType", rs.getString("TYPE_NAME"));
                    row.put("columnSize", rs.getInt("COLUMN_SIZE"));
                    row.put("nullable", "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                    row.put("ordinal", rs.getInt("ORDINAL_POSITION"));
                    cols.add(row);
                }
            }
        } catch (Exception e) {
            throw new BusinessException(500, "读取表列失败: " + e.getMessage());
        }
        return cols;
    }

    public Map<String, Object> livePartitions(Long managedTableId) {
        RcManagedTable mt = managedTableMapper.selectById(managedTableId);
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(404, "纳管表不存在");
        }
        String table = requireIdent(mt.getPhysicalTable(), "physicalTable");
        List<Map<String, Object>> parts = new ArrayList<>();
        long totalRows = 0;
        try (Connection c = platformDataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT PARTITION_NAME, PARTITION_METHOD, PARTITION_EXPRESSION, PARTITION_DESCRIPTION, "
                             + "TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH, CREATE_TIME, UPDATE_TIME "
                             + "FROM information_schema.PARTITIONS "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                             + "ORDER BY PARTITION_ORDINAL_POSITION")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pname = rs.getString("PARTITION_NAME");
                    long rows = rs.getLong("TABLE_ROWS");
                    totalRows += Math.max(rows, 0);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("partitionName", pname == null ? "(未分区)" : pname);
                    row.put("partitionMethod", rs.getString("PARTITION_METHOD"));
                    row.put("partitionExpression", rs.getString("PARTITION_EXPRESSION"));
                    row.put("partitionDescription", rs.getString("PARTITION_DESCRIPTION"));
                    row.put("tableRows", rows);
                    row.put("dataBytes", rs.getLong("DATA_LENGTH"));
                    row.put("indexBytes", rs.getLong("INDEX_LENGTH"));
                    row.put("partitioned", pname != null);
                    parts.add(row);
                }
            }
        } catch (Exception e) {
            throw new BusinessException(500, "读取分区信息失败: " + e.getMessage());
        }
        String alertLevel = "OK";
        String alertMessage = "分布正常或尚未物理分区";
        List<Map<String, Object>> realParts = parts.stream().filter(r -> Boolean.TRUE.equals(r.get("partitioned"))).toList();
        if (realParts.isEmpty()) {
            alertLevel = "WARN";
            alertMessage = "表尚未物理分区，仅有策略台账";
        } else if (totalRows > 0 && realParts.size() >= 2) {
            double avg = (double) totalRows / realParts.size();
            double maxShare = 0;
            for (Map<String, Object> r : realParts) {
                long rows = ((Number) r.get("tableRows")).longValue();
                double share = rows * 1.0 / totalRows;
                r.put("rowShare", Math.round(share * 10000) / 100.0);
                if (share > maxShare) maxShare = share;
                if (avg > 0 && rows > avg * 2.5) {
                    r.put("balanceStatus", "UNEVEN");
                } else {
                    r.put("balanceStatus", "OK");
                }
            }
            if (maxShare >= 0.7) {
                alertLevel = "UNEVEN";
                alertMessage = "检测到数据分布不均（最大分区占比≥70%），建议评估迁移或重建分区";
            }
        } else {
            for (Map<String, Object> r : realParts) {
                r.put("rowShare", 100.0);
                r.put("balanceStatus", "OK");
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("managedTableId", managedTableId);
        out.put("physicalTable", table);
        out.put("partitioned", !realParts.isEmpty());
        out.put("partitionCount", realParts.size());
        out.put("totalRows", totalRows);
        out.put("alertLevel", alertLevel);
        out.put("alertMessage", alertMessage);
        out.put("partitions", parts);
        return out;
    }

    public List<RcPartitionOp> listPartitionOps(Long partitionDefId, Long managedTableId) {
        LambdaQueryWrapper<RcPartitionOp> q = new LambdaQueryWrapper<RcPartitionOp>().orderByDesc(RcPartitionOp::getId);
        if (partitionDefId != null) q.eq(RcPartitionOp::getPartitionDefId, partitionDefId);
        if (managedTableId != null) q.eq(RcPartitionOp::getManagedTableId, managedTableId);
        q.last("LIMIT 200");
        return partitionOpMapper.selectList(q);
    }

    @Transactional
    public Map<String, Object> createPartitionOp(UserPrincipal operator, Map<String, Object> body) {
        String opType = str(body.get("opType"), "").toUpperCase(Locale.ROOT);
        if (!Set.of("COMPRESS", "REBUILD_INDEX", "CLEANUP", "ANALYZE", "BACKUP", "RESTORE_PLAN").contains(opType)) {
            throw new BusinessException(400, "opType 须为 COMPRESS / REBUILD_INDEX / CLEANUP / ANALYZE / BACKUP / RESTORE_PLAN");
        }
        Long defId = longVal(body.get("partitionDefId"));
        Long managedId = longVal(body.get("managedTableId"));
        RcPartitionDef def = null;
        if (defId != null) {
            def = partitionMapper.selectById(defId);
            if (def == null || "OFFLINE".equalsIgnoreCase(def.getStatus())) {
                throw new BusinessException(404, "分区策略不存在");
            }
        }
        RcManagedTable mt = null;
        if (managedId != null) {
            mt = managedTableMapper.selectById(managedId);
        } else if (def != null && def.getTableName() != null) {
            mt = findActiveManagedByTable(def.getTableName());
        }
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(400, "请选择已纳管目标表或绑定策略");
        }
        String table = requireIdent(mt.getPhysicalTable(), "physicalTable");
        String remark = str(body.get("remark"), null);

        if ("BACKUP".equals(opType)) {
            Map<String, Object> backup = runLogicalBackup(operator, mt.getId(),
                    body.get("retentionDays") == null ? 30 : Integer.valueOf(String.valueOf(body.get("retentionDays"))));
            RcPartitionOp op = new RcPartitionOp();
            op.setPartitionDefId(def == null ? null : def.getId());
            op.setManagedTableId(mt.getId());
            op.setPhysicalTable(table);
            op.setOpType("BACKUP");
            op.setOpStatus("SUCCESS");
            op.setPreviewSql("-- logical backup artifactId=" + backup.get("artifactId"));
            op.setMessage("逻辑备份完成，行数=" + backup.get("rowCount"));
            op.setCreatedBy(operator.getUsername());
            op.setCreatedAt(LocalDateTime.now());
            partitionOpMapper.insert(op);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("opId", op.getId());
            out.put("opStatus", op.getOpStatus());
            out.put("message", op.getMessage());
            out.put("backup", backup);
            out.put("executed", true);
            return out;
        }

        String previewSql;
        String message;
        String opStatus = "LEDGER";
        boolean executed = false;
        switch (opType) {
            case "ANALYZE" -> {
                previewSql = "ANALYZE TABLE `" + table + "`";
                try (Connection c = platformDataSource.getConnection(); Statement st = c.createStatement()) {
                    st.execute(previewSql);
                    opStatus = "SUCCESS";
                    message = "已执行统计信息更新（ANALYZE TABLE）";
                    executed = true;
                } catch (Exception e) {
                    opStatus = "FAILED";
                    message = "ANALYZE 执行失败: " + e.getMessage();
                }
            }
            case "COMPRESS" -> {
                previewSql = "OPTIMIZE TABLE `" + table + "` -- LEDGER ONLY, NOT EXECUTED（压缩/碎片整理）";
                message = "数据压缩/整理建议已登记为台账，未自动执行 OPTIMIZE（避免长锁表）";
            }
            case "REBUILD_INDEX" -> {
                previewSql = "ALTER TABLE `" + table + "` ENGINE=InnoDB -- LEDGER ONLY, NOT EXECUTED（重建索引建议）";
                message = "重建索引建议已登记为台账，未自动执行物理重建";
            }
            case "CLEANUP" -> {
                previewSql = "-- CLEANUP LEDGER: 请按分区策略清理历史分区或过期数据，禁止自动 DROP PARTITION";
                message = "数据清理计划已登记，未执行物理删除";
            }
            case "RESTORE_PLAN" -> {
                Long artifactId = longVal(body.get("artifactId"));
                previewSql = "-- RESTORE PLAN LEDGER: artifactId=" + (artifactId == null ? "?" : artifactId)
                        + " table=" + table + "；禁止自动覆写生产表，需人工校验后回灌";
                message = remark != null && !remark.isBlank()
                        ? remark
                        : "恢复计划已登记：校验备份产物 SHA-256 后手工回灌，不自动覆写";
            }
            default -> throw new BusinessException(400, "不支持的操作类型");
        }

        RcPartitionOp op = new RcPartitionOp();
        op.setPartitionDefId(def == null ? null : def.getId());
        op.setManagedTableId(mt.getId());
        op.setPhysicalTable(table);
        op.setOpType(opType);
        op.setOpStatus(opStatus);
        op.setPreviewSql(previewSql);
        op.setMessage(message);
        op.setCreatedBy(operator.getUsername());
        op.setCreatedAt(LocalDateTime.now());
        partitionOpMapper.insert(op);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_PARTITION_OP", "rc_partition_op", String.valueOf(op.getId()), opType + "/" + opStatus);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("opId", op.getId());
        out.put("opType", opType);
        out.put("opStatus", opStatus);
        out.put("previewSql", previewSql);
        out.put("message", message);
        out.put("executed", executed);
        return out;
    }

    private List<Map<String, Object>> summarizePartitionMonitor() {
        List<Map<String, Object>> summary = new ArrayList<>();
        List<RcPartitionDef> defs = partitionMapper.selectList(new LambdaQueryWrapper<RcPartitionDef>()
                .ne(RcPartitionDef::getStatus, "OFFLINE")
                .orderByAsc(RcPartitionDef::getId));
        Set<String> seen = new java.util.HashSet<>();
        for (RcPartitionDef d : defs) {
            if (d.getTableName() == null || !seen.add(d.getTableName())) continue;
            RcManagedTable mt = findActiveManagedByTable(d.getTableName());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("partitionDefId", d.getId());
            row.put("partitionName", d.getPartitionName());
            row.put("tableName", d.getTableName());
            row.put("partitionType", d.getPartitionType());
            row.put("pretestStatus", d.getPretestStatus());
            row.put("managedTableId", mt == null ? null : mt.getId());
            if (mt != null) {
                try {
                    Map<String, Object> live = livePartitions(mt.getId());
                    row.put("partitioned", live.get("partitioned"));
                    row.put("partitionCount", live.get("partitionCount"));
                    row.put("alertLevel", live.get("alertLevel"));
                    row.put("alertMessage", live.get("alertMessage"));
                } catch (Exception e) {
                    row.put("alertLevel", "WARN");
                    row.put("alertMessage", "监控读取失败");
                }
            } else {
                row.put("alertLevel", "BLOCKED");
                row.put("alertMessage", "目标表未纳管或已解绑");
            }
            summary.add(row);
        }
        return summary;
    }

    private String buildPartitionByDdl(String table, String type, String column, String expr) {
        String t = table == null ? "?" : table;
        String c = column == null ? "?" : column;
        String e = expr == null ? "" : expr.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE `").append(t).append("` PARTITION BY ").append(type);
        sb.append(" (`").append(c).append("`) ");
        if (!e.isBlank()) {
            if ("HASH".equalsIgnoreCase(type) && e.toUpperCase(Locale.ROOT).startsWith("PARTITIONS")) {
                sb.append(e);
            } else if ("HASH".equalsIgnoreCase(type) && e.matches("\\d+")) {
                sb.append("PARTITIONS ").append(e);
            } else {
                sb.append("/* ").append(e).append(" */");
            }
        }
        sb.append(" -- DRY-RUN ONLY, NOT EXECUTED");
        return sb.toString();
    }

    private RcManagedTable requireActiveManagedByTable(String tableName) {
        RcManagedTable mt = findActiveManagedByTable(tableName);
        if (mt == null) {
            throw new BusinessException(400, "目标表须为已纳管物理表：" + tableName);
        }
        return mt;
    }

    private RcManagedTable findActiveManagedByTable(String tableName) {
        if (tableName == null || tableName.isBlank()) return null;
        return managedTableMapper.selectOne(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getPhysicalTable, tableName)
                .eq(RcManagedTable::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    @Transactional
    public Long createPolicy(UserPrincipal operator, Map<String, Object> body) {
        String action = str(body.get("actionType"), "BACKUP").toUpperCase(Locale.ROOT);
        if (!Set.of("BACKUP", "ARCHIVE", "DESTROY").contains(action)) {
            throw new BusinessException(400, "actionType 须为 BACKUP / ARCHIVE / DESTROY");
        }
        Long managedId = longVal(body.get("managedTableId"));
        String sourceDb = str(body.get("sourceDb"), null);
        List<String> tableNames = extractLifecycleTableNames(body);
        String tableName = tableNames.isEmpty() ? str(body.get("tableName"), null) : tableNames.get(0);
        RcManagedTable mt = null;
        if (managedId != null) {
            mt = managedTableMapper.selectById(managedId);
            if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
                throw new BusinessException(400, "纳管表不存在或已解绑");
            }
            if (tableNames.isEmpty() && (tableName == null || tableName.isBlank())) {
                tableName = mt.getPhysicalTable();
                tableNames = List.of(tableName);
            }
            if (sourceDb == null || sourceDb.isBlank()) {
                sourceDb = com.chengde.smartcity.masterdata.support.DataLayerSupport.databaseForLayer(
                        com.chengde.smartcity.masterdata.support.DataLayerSupport.layerForTableName(tableName));
            }
        }
        if (managedId == null && tableName != null) {
            RcManagedTable found = findActiveManagedByTable(tableName);
            if (found != null) {
                mt = found;
                managedId = found.getId();
            }
        }
        if (sourceDb == null || tableNames.isEmpty()) {
            throw new BusinessException(400, "请选择源库和表");
        }
        StorageLifecycleService.requireSourceDb(sourceDb);
        for (String tn : tableNames) {
            StorageLifecycleService.requireIdent(tn, "tableName");
        }
        String cron = str(body.get("scheduleCron"), null);
        validateCron(cron);
        String tableRule = mergeLifecycleRule(body, sourceDb, tableNames);
        String compressType = str(body.get("compressType"),
                boolVal(body.get("compressEnabled"), false) ? "GZIP" : "NONE").toUpperCase(Locale.ROOT);
        int retentionDays = parseRetentionDays(body.get("retentionDays"), 180);
        String storageStrategy = normalizeStorageStrategy(
                str(body.get("storageStrategy"), null), action);
        RcStoragePolicy p = new RcStoragePolicy();
        p.setPolicyCode(str(body.get("policyCode"), "POL_" + System.currentTimeMillis()));
        p.setPolicyName(required(body.get("policyName"), "policyName").toString());
        p.setActionType(action);
        p.setRetentionDays(retentionDays);
        p.setThemeId(longVal(body.get("themeId")) != null ? longVal(body.get("themeId"))
                : (mt == null ? null : mt.getThemeId()));
        p.setManagedTableId(managedId);
        p.setStorageStrategy(storageStrategy);
        p.setBackupLibraryId(longVal(body.get("backupLibraryId")));
        p.setTableRule(tableRule);
        p.setCompressEnabled(boolVal(body.get("compressEnabled"), "GZIP".equals(compressType)) ? 1 : 0);
        p.setCompressType(compressType);
        p.setDestroyRule("DESTROY".equals(action)
                ? str(body.get("destroyRule"),
                "仅销毁已备份且已归档、且保存满 " + retentionDays + " 天的日快照与归档文件，源业务表不改")
                : str(body.get("destroyRule"), null));
        p.setScheduleEnabled(boolVal(body.get("scheduleEnabled"), false) ? 1 : 0);
        p.setScheduleCron(cron);
        p.setNextRunAt(p.getScheduleEnabled() != null && p.getScheduleEnabled() == 1
                ? computeNextRun(cron, LocalDateTime.now()) : null);
        p.setDsPublishStatus("LOCAL");
        p.setStatus("ACTIVE");
        policyMapper.insert(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_POLICY_CREATE", "rc_storage_policy", String.valueOf(p.getId()), action);
        return p.getId();
    }

    private List<String> extractLifecycleTableNames(Map<String, Object> body) {
        List<String> names = new ArrayList<>();
        Object raw = body.get("tableNames");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    names.add(String.valueOf(o).trim());
                }
            }
        }
        if (names.isEmpty()) {
            String one = str(body.get("tableName"), null);
            if (one != null) {
                names.add(one);
            }
        }
        if (names.isEmpty()) {
            String existing = str(body.get("tableRule"), null);
            if (existing != null && existing.trim().startsWith("{")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = JSON.readValue(existing, Map.class);
                    names.addAll(StorageLifecycleService.resolveTableNames(parsed));
                } catch (Exception ignored) {
                    // keep empty
                }
            }
        }
        return names;
    }

    private String mergeLifecycleRule(Map<String, Object> body, String sourceDb, List<String> tableNames) {
        Map<String, Object> rule = new LinkedHashMap<>();
        String existing = str(body.get("tableRule"), null);
        if (existing != null && existing.trim().startsWith("{")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = JSON.readValue(existing, Map.class);
                if (parsed != null) {
                    rule.putAll(parsed);
                }
            } catch (Exception ignored) {
                rule.put("note", existing);
            }
        }
        rule.put("v", 3);
        rule.put("sourceDb", sourceDb);
        rule.put("tableNames", tableNames);
        rule.put("tableName", tableNames.isEmpty() ? null : tableNames.get(0));
        String expectedBak = com.chengde.smartcity.masterdata.support.DataLayerSupport.backupDatabaseFor(sourceDb);
        String bakFromBody = str(body.get("backupDatabase"), null);
        if (bakFromBody == null || bakFromBody.isBlank()) {
            bakFromBody = str(rule.get("backupDatabase"), expectedBak);
        }
        String bak = bakFromBody == null || bakFromBody.isBlank() ? expectedBak : bakFromBody.trim();
        if (!com.chengde.smartcity.masterdata.support.DataLayerSupport.isBackupDatabase(bak)) {
            throw new BusinessException(400, "备份库须为分层 *_bak 库");
        }
        if (!sourceDb.equalsIgnoreCase(
                com.chengde.smartcity.masterdata.support.DataLayerSupport.sourceDatabaseOf(bak))) {
            throw new BusinessException(400, "备份库须与源库对应，期望 " + expectedBak);
        }
        rule.put("backupDatabase", bak.toLowerCase(Locale.ROOT));
        String scope = body.get("backupScope") != null
                ? String.valueOf(body.get("backupScope")).toUpperCase(Locale.ROOT)
                : str(rule.get("backupScope"), "TABLE").toUpperCase(Locale.ROOT);
        if ("BY_PARTITION".equals(scope) || "PARTITION".equals(scope) || "BY_BOTH".equals(scope)) {
            rule.put("backupScope", "PARTITION");
        } else {
            rule.put("backupScope", "TABLE");
        }
        rule.remove("timeColumn");
        rule.remove("timeBeforeDays");
        rule.remove("partitionName");
        try {
            return JSON.writeValueAsString(rule);
        } catch (Exception e) {
            throw new BusinessException(400, "策略规则无法序列化");
        }
    }

    private String inferTableName(RcStoragePolicy p) {
        if (p.getManagedTableId() == null) return null;
        RcManagedTable mt = managedTableMapper.selectById(p.getManagedTableId());
        return mt == null ? null : mt.getPhysicalTable();
    }

    @Transactional
    public Map<String, Object> updatePolicySchedule(UserPrincipal operator, Long policyId, Map<String, Object> body) {
        RcStoragePolicy p = policyMapper.selectById(policyId);
        if (p == null) throw new BusinessException(404, "策略不存在");
        boolean scheduleOn = boolVal(body.get("scheduleEnabled"), true);
        String cron = str(body.get("scheduleCron"), p.getScheduleCron());
        validateCron(cron);
        if (!scheduleOn) {
            p.setScheduleEnabled(0);
            p.setScheduleCron(cron);
            p.setNextRunAt(null);
        } else {
            p.setScheduleEnabled(1);
            p.setScheduleCron(cron);
            p.setNextRunAt(computeNextRun(cron, LocalDateTime.now()));
        }
        policyMapper.updateById(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_POLICY_SCHEDULE", "rc_storage_policy", String.valueOf(policyId),
                scheduleOn ? cron : "DISABLED");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyId", policyId);
        out.put("scheduleEnabled", p.getScheduleEnabled());
        out.put("scheduleCron", p.getScheduleCron());
        out.put("nextRunAt", p.getNextRunAt());
        return out;
    }

    @Transactional
    public void updatePolicy(UserPrincipal operator, Long policyId, Map<String, Object> body) {
        RcStoragePolicy p = policyMapper.selectById(policyId);
        if (p == null) throw new BusinessException(404, "策略不存在");
        if (body.containsKey("policyName")) {
            p.setPolicyName(required(body.get("policyName"), "policyName").toString());
        }
        if (body.containsKey("retentionDays")) {
            p.setRetentionDays(parseRetentionDays(body.get("retentionDays"),
                    p.getRetentionDays() == null ? 180 : p.getRetentionDays()));
        }
        if (body.containsKey("managedTableId")) {
            Long managedId = longVal(body.get("managedTableId"));
            if (managedId == null) throw new BusinessException(400, "请选择纳管表");
            RcManagedTable mt = managedTableMapper.selectById(managedId);
            if (mt == null) throw new BusinessException(404, "纳管表不存在");
            p.setManagedTableId(managedId);
            p.setThemeId(mt.getThemeId());
        }
        if (body.containsKey("storageStrategy")) {
            p.setStorageStrategy(normalizeStorageStrategy(
                    str(body.get("storageStrategy"), p.getStorageStrategy()), p.getActionType()));
        }
        if (body.containsKey("backupLibraryId")) {
            p.setBackupLibraryId(longVal(body.get("backupLibraryId")));
        }
        if (body.containsKey("sourceDb") || body.containsKey("tableName") || body.containsKey("tableNames")
                || body.containsKey("tableRule") || body.containsKey("backupScope")) {
            Map<String, Object> existing = storageLifecycleService.parseRule(p);
            String sourceDb = str(body.get("sourceDb"), str(existing.get("sourceDb"), null));
            List<String> tableNames = extractLifecycleTableNames(body);
            if (tableNames.isEmpty()) {
                tableNames = StorageLifecycleService.resolveTableNames(existing);
            }
            if (tableNames.isEmpty()) {
                String inferred = inferTableName(p);
                if (inferred != null) {
                    tableNames = List.of(inferred);
                }
            }
            if (sourceDb == null || tableNames.isEmpty()) {
                throw new BusinessException(400, "请选择源库和表");
            }
            StorageLifecycleService.requireSourceDb(sourceDb);
            for (String tn : tableNames) {
                StorageLifecycleService.requireIdent(tn, "tableName");
            }
            p.setTableRule(mergeLifecycleRule(body, sourceDb, tableNames));
        }
        if (body.containsKey("compressEnabled") || body.containsKey("compressType")) {
            String compressType = str(body.get("compressType"),
                    boolVal(body.get("compressEnabled"), false) ? "GZIP" : "NONE").toUpperCase(Locale.ROOT);
            p.setCompressEnabled(boolVal(body.get("compressEnabled"), "GZIP".equals(compressType)) ? 1 : 0);
            p.setCompressType(compressType);
        }
        if (body.containsKey("destroyRule")) {
            p.setDestroyRule(str(body.get("destroyRule"), null));
        }
        if (body.containsKey("scheduleEnabled") || body.containsKey("scheduleCron")) {
            String cron = str(body.get("scheduleCron"), p.getScheduleCron());
            validateCron(cron);
            p.setScheduleCron(cron);
            if (body.containsKey("scheduleEnabled")) {
                boolean on = boolVal(body.get("scheduleEnabled"), false);
                p.setScheduleEnabled(on ? 1 : 0);
                p.setNextRunAt(on ? computeNextRun(cron, LocalDateTime.now()) : null);
            } else if (p.getScheduleEnabled() != null && p.getScheduleEnabled() == 1) {
                p.setNextRunAt(computeNextRun(cron, LocalDateTime.now()));
            }
        }
        if (body.containsKey("status")) {
            p.setStatus(str(body.get("status"), p.getStatus()));
        }
        policyMapper.updateById(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_POLICY_UPDATE", "rc_storage_policy", String.valueOf(policyId), p.getPolicyName());
    }

    @Transactional
    public void deletePolicy(UserPrincipal operator, Long policyId) {
        RcStoragePolicy p = policyMapper.selectById(policyId);
        if (p == null) throw new BusinessException(404, "策略不存在");
        policyMapper.deleteById(policyId);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_POLICY_DELETE", "rc_storage_policy", String.valueOf(policyId), p.getPolicyName());
    }

    public RcStoragePolicy getPolicy(Long policyId) {
        RcStoragePolicy p = policyMapper.selectById(policyId);
        if (p == null) throw new BusinessException(404, "策略不存在");
        return p;
    }

    public Map<String, Object> executePolicy(UserPrincipal operator, Long policyId) {
        Map<String, Object> executed = storageLifecycleService.execute(operator, policyId);
        refreshNextRunAfterExecute(policyId);
        return executed;
    }

    public void refreshNextRunAfterExecute(Long policyId) {
        RcStoragePolicy p = policyMapper.selectById(policyId);
        if (p == null || p.getScheduleEnabled() == null || p.getScheduleEnabled() != 1) return;
        LocalDateTime next = computeNextRun(p.getScheduleCron(), LocalDateTime.now().plusSeconds(1));
        // 只改 next_run_at，避免整行 update 覆盖刚写入的 last_run_* 
        policyMapper.update(null, new LambdaUpdateWrapper<RcStoragePolicy>()
                .eq(RcStoragePolicy::getId, policyId)
                .set(RcStoragePolicy::getNextRunAt, next));
    }

    public List<RcStoragePolicy> listDuePolicies(LocalDateTime now) {
        return policyMapper.selectList(new LambdaQueryWrapper<RcStoragePolicy>()
                .eq(RcStoragePolicy::getScheduleEnabled, 1)
                .eq(RcStoragePolicy::getStatus, "ACTIVE")
                .isNotNull(RcStoragePolicy::getNextRunAt)
                .le(RcStoragePolicy::getNextRunAt, now));
    }

    public List<RcPolicyRunLog> listPolicyRuns(Long policyId) {
        LambdaQueryWrapper<RcPolicyRunLog> q = new LambdaQueryWrapper<RcPolicyRunLog>()
                .orderByDesc(RcPolicyRunLog::getId);
        if (policyId != null) q.eq(RcPolicyRunLog::getPolicyId, policyId);
        return policyRunLogMapper.selectList(q.last("LIMIT 200"));
    }

    public Map<String, Object> restoreArtifact(UserPrincipal operator, Long artifactId) {
        return storageLifecycleService.restore(operator, artifactId);
    }

    @Transactional
    public Map<String, Object> runLogicalBackup(UserPrincipal operator, Long managedTableId, Integer retentionDays) {
        return runLogicalBackup(operator, managedTableId, retentionDays, "LOCAL", null, null);
    }

    @Transactional
    public Map<String, Object> runLogicalBackup(UserPrincipal operator, Long managedTableId, Integer retentionDays,
                                                String storageStrategy, String tableRule, Long backupLibraryId) {
        RcManagedTable mt = managedTableMapper.selectById(managedTableId);
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(404, "纳管表不存在");
        }
        String table = requireIdent(mt.getPhysicalTable(), "physicalTable");
        String actor = operator != null ? operator.getUsername() : "scheduler";
        String strategy = storageStrategy == null || storageStrategy.isBlank()
                ? "LOCAL" : storageStrategy.toUpperCase(Locale.ROOT);
        Path root = resolveStorageRoot(strategy);
        RcBackupJob job = new RcBackupJob();
        job.setJobName("BACKUP_" + table + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        job.setThemeId(mt.getThemeId());
        job.setStatus("RUNNING");
        job.setCreatedBy(actor);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        backupJobMapper.insert(job);

        try {
            Files.createDirectories(root);
            String fileName = table + "_" + System.currentTimeMillis() + ".cdbak";
            Path tmp = root.resolve(fileName + ".tmp");
            Path finalPath = root.resolve(fileName);
            long rows = 0;
            String sql = "SELECT * FROM `" + table + "`";
            String ruleNote = tableRule == null ? "" : tableRule.trim();
            try (Connection conn = platformDataSource.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql);
                 BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write("# Chengde logical backup\n");
                writer.write("# table=" + table + "\n");
                writer.write("# storageStrategy=" + strategy + "\n");
                writer.write("# tableRule=" + ruleNote + "\n");
                if (backupLibraryId != null) {
                    writer.write("# backupLibraryId=" + backupLibraryId + "\n");
                }
                writer.write("# createdAt=" + LocalDateTime.now() + "\n");
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                List<String> headers = new ArrayList<>();
                for (int i = 1; i <= cols; i++) headers.add(meta.getColumnLabel(i));
                writer.write(String.join("\t", headers));
                writer.write("\n");
                while (rs.next()) {
                    List<String> values = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) {
                        Object v = rs.getObject(i);
                        values.add(v == null ? "\\N" : String.valueOf(v).replace("\t", " ").replace("\n", " "));
                    }
                    writer.write(String.join("\t", values));
                    writer.write("\n");
                    rows++;
                }
            }
            try {
                Files.move(tmp, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
                Files.move(tmp, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            String sha = sha256(finalPath);
            long size = Files.size(finalPath);
            String location = strategy + ":" + finalPath.toAbsolutePath();

            RcBackupArtifact art = new RcBackupArtifact();
            art.setArtifactType("BACKUP");
            art.setJobId(job.getId());
            art.setManagedTableId(mt.getId());
            art.setPhysicalTable(table);
            art.setFilePath(finalPath.toAbsolutePath().toString());
            art.setStorageLocation(location);
            art.setFileName(fileName);
            art.setRowCount(rows);
            art.setByteSize(size);
            art.setSha256(sha);
            art.setStatus("SUCCESS");
            art.setMessage("logical backup ok"
                    + (ruleNote.isBlank() ? "" : "; rule=" + ruleNote)
                    + (backupLibraryId == null ? "" : "; lib=" + backupLibraryId));
            art.setCreatedBy(actor);
            art.setCreatedAt(LocalDateTime.now());
            backupArtifactMapper.insert(art);

            job.setStatus("SUCCESS");
            job.setLastRunAt(LocalDateTime.now());
            job.setLastMessage("rows=" + rows + " sha256=" + sha);
            job.setUpdatedAt(LocalDateTime.now());
            backupJobMapper.updateById(job);

            cleanupOldBackups(mt.getId(), retentionDays == null ? 30 : retentionDays);
            refreshTableStats(mt);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jobId", job.getId());
            out.put("artifactId", art.getId());
            out.put("fileName", fileName);
            out.put("filePath", art.getFilePath());
            out.put("storageLocation", location);
            out.put("storageStrategy", strategy);
            out.put("tableRule", ruleNote);
            out.put("rowCount", rows);
            out.put("byteSize", size);
            out.put("sha256", sha);
            out.put("message", job.getLastMessage());
            return out;
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setLastRunAt(LocalDateTime.now());
            job.setLastMessage(ex.getMessage());
            job.setUpdatedAt(LocalDateTime.now());
            backupJobMapper.updateById(job);
            throw new BusinessException(500, "逻辑备份失败: " + ex.getMessage());
        }
    }

    /**
     * 解析策略 tableRule 中的备份范围 JSON（含 backupScope）。非 JSON 视为整表备注。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBackupScope(String tableRule) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("backupScope", "FULL");
        if (tableRule == null || tableRule.isBlank()) {
            return out;
        }
        String raw = tableRule.trim();
        if (!raw.startsWith("{")) {
            out.put("note", raw);
            return out;
        }
        try {
            Map<String, Object> parsed = JSON.readValue(raw, Map.class);
            if (parsed != null) {
                out.putAll(parsed);
                if (!out.containsKey("backupScope") || str(out.get("backupScope"), "").isBlank()) {
                    out.put("backupScope", "FULL");
                }
            }
        } catch (Exception ignored) {
            out.put("note", raw);
        }
        return out;
    }

    /**
     * 按时间/按分区备份台账：写入产物记录与说明，不按条件真实 SELECT。
     */
    @Transactional
    public Map<String, Object> runScopedBackupLedger(UserPrincipal operator, RcStoragePolicy policy,
                                                     Map<String, Object> scopeMeta) {
        Long managedId = policy.getManagedTableId();
        if (managedId == null) throw new BusinessException(400, "备份策略未绑定纳管表");
        RcManagedTable mt = managedTableMapper.selectById(managedId);
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(404, "纳管表不存在");
        }
        String table = requireIdent(mt.getPhysicalTable(), "physicalTable");
        String actor = operator != null ? operator.getUsername() : "scheduler";
        String scope = str(scopeMeta.get("backupScope"), "BY_TIME").toUpperCase(Locale.ROOT);
        String timeColumn = str(scopeMeta.get("timeColumn"), "");
        String timeBeforeDays = str(scopeMeta.get("timeBeforeDays"),
                policy.getRetentionDays() == null ? "" : String.valueOf(policy.getRetentionDays()));
        String partitionName = str(scopeMeta.get("partitionName"), "");
        String note = str(scopeMeta.get("note"), "");
        String strategy = policy.getStorageStrategy() == null || policy.getStorageStrategy().isBlank()
                ? "LOCAL" : policy.getStorageStrategy().toUpperCase(Locale.ROOT);
        Path root = resolveStorageRoot(strategy);
        try {
            Files.createDirectories(root);
            String fileName = table + "_ledger_" + System.currentTimeMillis() + ".cdbak.meta";
            Path finalPath = root.resolve(fileName);
            StringBuilder meta = new StringBuilder();
            meta.append("# Chengde scoped backup ledger\n");
            meta.append("# table=").append(table).append('\n');
            meta.append("# backupScope=").append(scope).append('\n');
            meta.append("# timeColumn=").append(timeColumn).append('\n');
            meta.append("# timeBeforeDays=").append(timeBeforeDays).append('\n');
            meta.append("# partitionName=").append(partitionName).append('\n');
            meta.append("# note=").append(note).append('\n');
            meta.append("# createdAt=").append(LocalDateTime.now()).append('\n');
            meta.append("# note2=ledger only, rows not exported by scope filter\n");
            Files.writeString(finalPath, meta.toString(), StandardCharsets.UTF_8);

            String location = strategy + ":" + finalPath.toAbsolutePath();
            String msg = "按" + ("BY_PARTITION".equals(scope) ? "分区" : "BY_BOTH".equals(scope) ? "时间+分区" : "时间")
                    + "备份台账已记录（未按条件真实导出）"
                    + (timeColumn.isBlank() ? "" : "；时间列=" + timeColumn)
                    + (timeBeforeDays.isBlank() ? "" : "；早于" + timeBeforeDays + "天")
                    + (partitionName.isBlank() ? "" : "；分区=" + partitionName);

            RcBackupArtifact art = new RcBackupArtifact();
            art.setArtifactType("BACKUP");
            art.setJobId(0L);
            art.setManagedTableId(mt.getId());
            art.setPhysicalTable(table);
            art.setFilePath(finalPath.toAbsolutePath().toString());
            art.setStorageLocation(location);
            art.setFileName(fileName);
            art.setRowCount(0L);
            art.setByteSize(Files.size(finalPath));
            art.setSha256(sha256(finalPath));
            art.setStatus("LEDGER");
            art.setMessage(msg);
            art.setCreatedBy(actor);
            art.setCreatedAt(LocalDateTime.now());
            backupArtifactMapper.insert(art);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("artifactId", art.getId());
            out.put("fileName", fileName);
            out.put("filePath", art.getFilePath());
            out.put("storageLocation", location);
            out.put("storageStrategy", strategy);
            out.put("rowCount", 0);
            out.put("backupScope", scope);
            out.put("status", "LEDGER");
            out.put("message", msg);
            return out;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(500, "按范围备份台账失败: " + ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> runArchiveLedger(UserPrincipal operator, RcStoragePolicy policy) {
        Long managedId = policy.getManagedTableId();
        if (managedId == null) throw new BusinessException(400, "归档策略未绑定纳管表");
        RcManagedTable mt = managedTableMapper.selectById(managedId);
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(404, "纳管表不存在");
        }
        String table = requireIdent(mt.getPhysicalTable(), "physicalTable");
        String actor = operator != null ? operator.getUsername() : "scheduler";
        boolean compress = policy.getCompressEnabled() != null && policy.getCompressEnabled() == 1;
        String compressType = str(policy.getCompressType(), compress ? "GZIP" : "NONE").toUpperCase(Locale.ROOT);
        Path root = ARCHIVE_ROOT;
        try {
            Files.createDirectories(root);
            String fileName = table + "_" + System.currentTimeMillis()
                    + (compress && "GZIP".equals(compressType) ? ".archive.gz" : ".archive.meta");
            Path finalPath = root.resolve(fileName);
            long approxRows = mt.getRecordCount() == null ? 0 : mt.getRecordCount();
            byte[] meta = ("# Chengde archive ledger\n"
                    + "# table=" + table + "\n"
                    + "# retentionDays=" + policy.getRetentionDays() + "\n"
                    + "# compress=" + compressType + "\n"
                    + "# createdAt=" + LocalDateTime.now() + "\n").getBytes(StandardCharsets.UTF_8);
            if (compress && "GZIP".equals(compressType)) {
                try (OutputStream fos = Files.newOutputStream(finalPath);
                     GZIPOutputStream gzip = new GZIPOutputStream(fos)) {
                    gzip.write(meta);
                    gzip.write(("# note=ledger only, physical rows not moved\n").getBytes(StandardCharsets.UTF_8));
                }
            } else {
                Files.write(finalPath, meta);
            }
            String location = "ARCHIVE:" + finalPath.toAbsolutePath();
            RcBackupArtifact art = new RcBackupArtifact();
            art.setArtifactType("ARCHIVE");
            art.setJobId(0L);
            art.setManagedTableId(mt.getId());
            art.setPhysicalTable(table);
            art.setFilePath(finalPath.toAbsolutePath().toString());
            art.setStorageLocation(location);
            art.setFileName(fileName);
            art.setRowCount(approxRows);
            art.setByteSize(Files.size(finalPath));
            art.setSha256(sha256(finalPath));
            art.setStatus("LEDGER");
            art.setMessage("归档台账已记录；压缩=" + compressType + "；保存天数=" + policy.getRetentionDays());
            art.setCreatedBy(actor);
            art.setCreatedAt(LocalDateTime.now());
            backupArtifactMapper.insert(art);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("artifactId", art.getId());
            out.put("fileName", fileName);
            out.put("filePath", art.getFilePath());
            out.put("storageLocation", location);
            out.put("rowCount", approxRows);
            out.put("compressType", compressType);
            out.put("status", "LEDGER");
            out.put("message", art.getMessage());
            return out;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(500, "归档台账失败: " + ex.getMessage());
        }
    }

    public Map<String, Object> verifyArtifact(Long artifactId) {
        RcBackupArtifact art = backupArtifactMapper.selectById(artifactId);
        if (art == null) throw new BusinessException(404, "备份产物不存在");
        Path path = Path.of(art.getFilePath());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("artifactId", artifactId);
        out.put("exists", Files.exists(path));
        try {
            if (Files.exists(path)) {
                String sha = sha256(path);
                out.put("sha256", sha);
                out.put("match", sha.equalsIgnoreCase(art.getSha256()));
                out.put("byteSize", Files.size(path));
            } else {
                out.put("match", false);
            }
        } catch (Exception e) {
            throw new BusinessException(500, "校验失败: " + e.getMessage());
        }
        return out;
    }

    public List<RcBackupArtifact> listArtifacts(Long managedTableId) {
        LambdaQueryWrapper<RcBackupArtifact> q = new LambdaQueryWrapper<RcBackupArtifact>()
                .orderByDesc(RcBackupArtifact::getId);
        if (managedTableId != null) q.eq(RcBackupArtifact::getManagedTableId, managedTableId);
        return backupArtifactMapper.selectList(q.last("LIMIT 500"));
    }

    public Map<String, Object> syncLifecycleArtifacts() {
        return storageLifecycleService.syncLifecycleArtifacts();
    }

    @Transactional
    public void updateArtifact(UserPrincipal operator, Long artifactId, Map<String, Object> body) {
        RcBackupArtifact art = backupArtifactMapper.selectById(artifactId);
        if (art == null) throw new BusinessException(404, "备份产物不存在");
        if (body.containsKey("message")) {
            art.setMessage(str(body.get("message"), null));
        }
        if (body.containsKey("storageLocation")) {
            art.setStorageLocation(str(body.get("storageLocation"), art.getStorageLocation()));
        }
        if (body.containsKey("status")) {
            String st = str(body.get("status"), art.getStatus());
            if (st != null && !Set.of("SUCCESS", "FAILED", "PARTIAL", "DELETED").contains(st.toUpperCase(Locale.ROOT))) {
                throw new BusinessException(400, "status 无效");
            }
            art.setStatus(st == null ? art.getStatus() : st.toUpperCase(Locale.ROOT));
        }
        backupArtifactMapper.updateById(art);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_ARTIFACT_UPDATE", "rc_backup_artifact", String.valueOf(artifactId), art.getFileName());
    }

    @Transactional
    public void deleteArtifact(UserPrincipal operator, Long artifactId) {
        RcBackupArtifact art = backupArtifactMapper.selectById(artifactId);
        if (art == null) throw new BusinessException(404, "备份产物不存在");
        // 尽量清理本地文件；失败不阻断台账删除
        String path = art.getFilePath();
        if (path != null && !path.isBlank()) {
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(path);
                if (java.nio.file.Files.exists(p)) {
                    java.nio.file.Files.deleteIfExists(p);
                }
            } catch (Exception e) {
                log.warn("delete artifact file skipped id={} path={}: {}", artifactId, path, e.getMessage());
            }
        }
        backupArtifactMapper.deleteById(artifactId);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_ARTIFACT_DELETE", "rc_backup_artifact", String.valueOf(artifactId), art.getFileName());
    }

    @Transactional
    public Map<String, Object> refreshMonitor(UserPrincipal operator) {
        collectMonitorMetrics();
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_MONITOR_REFRESH", "rc_monitor_metric", "all", "四维监控刷新");
        return monitorOverview();
    }

    /** 资源监控总览：可用性/完整性/安全性/性能 + 通道与审计明细 */
    public Map<String, Object> monitorOverview() {
        List<RcMonitorMetric> metrics = monitorMetrics();
        Map<String, List<Map<String, Object>>> byCategory = new LinkedHashMap<>();
        byCategory.put("AVAILABILITY", new ArrayList<>());
        byCategory.put("INTEGRITY", new ArrayList<>());
        byCategory.put("SECURITY", new ArrayList<>());
        byCategory.put("PERFORMANCE", new ArrayList<>());
        int warn = 0;
        int critical = 0;
        LocalDateTime latest = null;
        for (RcMonitorMetric m : metrics) {
            String cat = m.getMetricCategory() == null ? "AVAILABILITY" : m.getMetricCategory().toUpperCase(Locale.ROOT);
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(toMetricView(m));
            String level = m.getAlertLevel() == null ? "OK" : m.getAlertLevel().toUpperCase(Locale.ROOT);
            if ("WARN".equals(level)) warn++;
            if ("CRITICAL".equals(level)) critical++;
            if (m.getCheckedAt() != null && (latest == null || m.getCheckedAt().isAfter(latest))) {
                latest = m.getCheckedAt();
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String cat : List.of("AVAILABILITY", "INTEGRITY", "SECURITY", "PERFORMANCE")) {
            summary.put(cat, categoryHealth(byCategory.getOrDefault(cat, List.of())));
        }
        summary.put("warnCount", warn);
        summary.put("criticalCount", critical);
        summary.put("metricCount", metrics.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("metrics", metrics.stream().map(this::toMetricView).toList());
        out.put("byCategory", byCategory);
        out.put("channels", listMonitorChannels());
        out.put("audits", listMonitorAudits(30));
        out.put("integritySamples", listIntegritySamples(20));
        out.put("checkedAt", latest);
        out.put("hint", "监控覆盖数据库服务、存储设备、传输通道、备份校验、目录加密与访问审计、查询/传输性能；未接入外部监控探针时以平台实测与台账为准");
        return out;
    }

    public List<Map<String, Object>> listCatalogSubsystems() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] s : CATALOG_SUBSYSTEMS) {
            out.add(Map.of("code", s[0], "name", s[1]));
        }
        return out;
    }

    public List<Map<String, Object>> listCatalogEntries(String q, String visibility, String subsystem,
                                                        String publishStatus) {
        LambdaQueryWrapper<RcAssetCatalogEntry> w = new LambdaQueryWrapper<RcAssetCatalogEntry>()
                .orderByDesc(RcAssetCatalogEntry::getId);
        if (q != null && !q.isBlank()) {
            w.and(x -> x.like(RcAssetCatalogEntry::getEntryCode, q)
                    .or().like(RcAssetCatalogEntry::getEntryName, q)
                    .or().like(RcAssetCatalogEntry::getDriveTask, q));
        }
        if (visibility != null && !visibility.isBlank()) {
            w.eq(RcAssetCatalogEntry::getVisibility, visibility.trim().toUpperCase(Locale.ROOT));
        }
        if (subsystem != null && !subsystem.isBlank()) {
            w.eq(RcAssetCatalogEntry::getSubsystemCode, subsystem.trim().toUpperCase(Locale.ROOT));
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            w.eq(RcAssetCatalogEntry::getPublishStatus, publishStatus.trim().toUpperCase(Locale.ROOT));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (RcAssetCatalogEntry e : catalogMapper.selectList(w)) {
            out.add(toCatalogView(e));
        }
        return out;
    }

    /** 兼容旧调用：无筛选返回全量视图 */
    public List<RcAssetCatalogEntry> listCatalogEntries() {
        return catalogMapper.selectList(new LambdaQueryWrapper<RcAssetCatalogEntry>()
                .orderByDesc(RcAssetCatalogEntry::getId));
    }

    @Transactional
    public Long createCatalogEntry(UserPrincipal operator, Map<String, Object> body) {
        Long managedTableId = longVal(body.get("managedTableId"));
        String metaEntryCode = str(body.get("metaEntryCode"), null);
        if (managedTableId == null && (metaEntryCode == null || metaEntryCode.isBlank())) {
            throw new BusinessException(400, "资产目录须关联已纳管表或元数据条目，禁止空手填");
        }
        String entryName = str(body.get("entryName"), null);
        String driveTask = str(body.get("driveTask"), "exchange-task");
        Long libId = longVal(body.get("libId"));
        String physicalTable = null;
        if (managedTableId != null) {
            RcManagedTable mt = managedTableMapper.selectById(managedTableId);
            if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
                throw new BusinessException(400, "纳管表不存在或已解绑");
            }
            physicalTable = mt.getPhysicalTable();
            if (entryName == null || entryName.isBlank()) {
                entryName = mt.getPhysicalTable();
            }
            if (metaEntryCode == null || metaEntryCode.isBlank()) {
                metaEntryCode = mt.getMetaEntryCode();
            }
            if (libId == null && mt.getThemeId() != null) {
                libId = mt.getThemeId();
            }
            driveTask = str(body.get("driveTask"), "managed:" + mt.getPhysicalTable());
        }
        String subsystem = normalizeSubsystem(str(body.get("subsystemCode"), "RESOURCE"));
        String encryptAlgo = normalizeEncryptAlgo(str(body.get("encryptAlgo"), "NONE"));
        boolean encryptEnabled = boolVal(body.get("encryptEnabled"), !"NONE".equals(encryptAlgo));
        if (encryptEnabled && "NONE".equals(encryptAlgo)) {
            encryptAlgo = "AES256";
        }
        if (!encryptEnabled) {
            encryptAlgo = "NONE";
        }

        RcAssetCatalogEntry e = new RcAssetCatalogEntry();
        e.setEntryCode(str(body.get("entryCode"), metaEntryCode != null ? metaEntryCode : ("ACE_" + System.currentTimeMillis())));
        e.setEntryName(required(entryName, "entryName").toString());
        if (libId != null) e.setLibId(libId);
        e.setManagedTableId(managedTableId);
        e.setSubsystemCode(subsystem);
        e.setVisibility("PRIVATE");
        e.setEncryptEnabled(encryptEnabled ? 1 : 0);
        e.setEncryptAlgo(encryptAlgo);
        e.setPublishStatus("DRAFT");
        e.setDescription(str(body.get("description"), null));
        e.setDriveTask(driveTask);
        e.setStatus("ACTIVE");
        e.setCreatedBy(operator.getUsername());
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.insert(e);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_CATALOG_CREATE", "rc_asset_catalog_entry", String.valueOf(e.getId()),
                e.getEntryCode() + (physicalTable == null ? "" : " → " + physicalTable));
        return e.getId();
    }

    @Transactional
    public void updateCatalogEncrypt(UserPrincipal operator, Long id, Map<String, Object> body) {
        RcAssetCatalogEntry e = requireCatalog(id);
        String encryptAlgo = normalizeEncryptAlgo(str(body.get("encryptAlgo"), e.getEncryptAlgo()));
        boolean encryptEnabled = boolVal(body.get("encryptEnabled"),
                e.getEncryptEnabled() != null && e.getEncryptEnabled() == 1);
        if (encryptEnabled && "NONE".equals(encryptAlgo)) {
            encryptAlgo = "AES256";
        }
        if (!encryptEnabled) {
            encryptAlgo = "NONE";
        }
        e.setEncryptEnabled(encryptEnabled ? 1 : 0);
        e.setEncryptAlgo(encryptAlgo);
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_CATALOG_ENCRYPT", "rc_asset_catalog_entry", String.valueOf(id),
                encryptEnabled + "/" + encryptAlgo);
    }

    @Transactional
    public void submitCatalogPublish(UserPrincipal operator, Long id) {
        RcAssetCatalogEntry e = requireCatalog(id);
        String ps = e.getPublishStatus() == null ? "DRAFT" : e.getPublishStatus().toUpperCase(Locale.ROOT);
        if (!"DRAFT".equals(ps) && !"REJECTED".equals(ps)) {
            throw new BusinessException(400, "仅草稿或驳回状态可提交公开审批，当前=" + ps);
        }
        e.setPublishStatus("PENDING_REVIEW");
        e.setRejectReason(null);
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_CATALOG_SUBMIT", "rc_asset_catalog_entry", String.valueOf(id), e.getEntryCode());
    }

    @Transactional
    public void approveCatalogPublish(UserPrincipal operator, Long id) {
        requireSysAdmin(operator);
        RcAssetCatalogEntry e = requireCatalog(id);
        if (!"PENDING_REVIEW".equalsIgnoreCase(nullToEmpty(e.getPublishStatus()))) {
            throw new BusinessException(400, "仅待审核目录可由管理员公开");
        }
        e.setPublishStatus("PUBLISHED");
        e.setVisibility("PUBLIC");
        e.setRejectReason(null);
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_CATALOG_APPROVE", "rc_asset_catalog_entry", String.valueOf(id), e.getEntryCode());
    }

    @Transactional
    public void rejectCatalogPublish(UserPrincipal operator, Long id, Map<String, Object> body) {
        requireSysAdmin(operator);
        RcAssetCatalogEntry e = requireCatalog(id);
        if (!"PENDING_REVIEW".equalsIgnoreCase(nullToEmpty(e.getPublishStatus()))) {
            throw new BusinessException(400, "仅待审核目录可驳回");
        }
        String reason = str(body == null ? null : body.get("reason"), null);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "请填写驳回原因");
        }
        e.setPublishStatus("REJECTED");
        e.setVisibility("PRIVATE");
        e.setRejectReason(reason.trim());
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_CATALOG_REJECT", "rc_asset_catalog_entry", String.valueOf(id), reason);
    }

    @Transactional
    public void unpublishCatalog(UserPrincipal operator, Long id) {
        requireSysAdmin(operator);
        RcAssetCatalogEntry e = requireCatalog(id);
        if (!"PUBLIC".equalsIgnoreCase(nullToEmpty(e.getVisibility()))
                && !"PUBLISHED".equalsIgnoreCase(nullToEmpty(e.getPublishStatus()))) {
            throw new BusinessException(400, "目录未公开，无需下线");
        }
        e.setVisibility("PRIVATE");
        e.setPublishStatus("DRAFT");
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_CATALOG_UNPUBLISH", "rc_asset_catalog_entry", String.valueOf(id), e.getEntryCode());
    }

    @Transactional
    public Map<String, Object> driveCatalogExchange(UserPrincipal operator, Long id) {
        RcAssetCatalogEntry e = requireCatalog(id);
        if (!"PUBLIC".equalsIgnoreCase(nullToEmpty(e.getVisibility()))
                || !"PUBLISHED".equalsIgnoreCase(nullToEmpty(e.getPublishStatus()))) {
            throw new BusinessException(400, "仅已公开目录可驱动数据交换");
        }
        if (!"ACTIVE".equalsIgnoreCase(nullToEmpty(e.getStatus()))) {
            throw new BusinessException(400, "目录未启用，无法驱动交换");
        }

        RcManagedTable mt = e.getManagedTableId() == null ? null : managedTableMapper.selectById(e.getManagedTableId());
        String physicalTable = mt != null ? mt.getPhysicalTable() : null;
        Long rowCount = null;
        String runStatus = "LEDGER";
        String message;
        if (mt != null && physicalTable != null && IDENT.matcher(physicalTable).matches()) {
            try (Connection conn = platformDataSource.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + physicalTable + "`")) {
                if (rs.next()) rowCount = rs.getLong(1);
                runStatus = "SUCCESS";
                message = "已按公开目录生成交换任务并完成台账交换；源表=" + physicalTable
                        + "，行数=" + (rowCount == null ? 0 : rowCount)
                        + (e.getEncryptEnabled() != null && e.getEncryptEnabled() == 1
                        ? "；传输加密=" + e.getEncryptAlgo() : "");
            } catch (Exception ex) {
                log.warn("catalog drive exchange count failed entry={}: {}", id, ex.getMessage());
                runStatus = "LEDGER";
                message = "已生成交换任务台账；未能读取源表行数（" + ex.getMessage() + "）";
            }
        } else {
            message = "已按公开目录生成交换任务台账（未绑定可计数纳管表，driveTask="
                    + nullToEmpty(e.getDriveTask()) + "）";
        }

        String jobCode = "CEX_" + id + "_" + System.currentTimeMillis();
        RcCatalogExchangeJob job = new RcCatalogExchangeJob();
        job.setCatalogEntryId(id);
        job.setJobCode(jobCode);
        job.setJobName("交换-" + e.getEntryName());
        job.setManagedTableId(e.getManagedTableId());
        job.setPhysicalTable(physicalTable);
        job.setRowCount(rowCount);
        job.setRunStatus(runStatus);
        job.setMessage(message);
        job.setCreatedBy(operator.getUsername());
        job.setCreatedAt(LocalDateTime.now());
        catalogExchangeJobMapper.insert(job);

        e.setDriveTask(str(e.getDriveTask(), "exchange:" + e.getEntryCode()));
        e.setExchangeTaskRef(jobCode);
        e.setLastExchangeAt(LocalDateTime.now());
        e.setLastExchangeMessage(message);
        e.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(e);

        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_CATALOG_DRIVE_EXCHANGE", "rc_catalog_exchange_job", String.valueOf(job.getId()), jobCode);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobId", job.getId());
        out.put("jobCode", jobCode);
        out.put("runStatus", runStatus);
        out.put("rowCount", rowCount == null ? 0 : rowCount);
        out.put("message", message);
        out.put("engineMode", "LEDGER");
        return out;
    }

    @Transactional
    public Map<String, Object> driveAllPublicCatalogExchange(UserPrincipal operator) {
        Map<String, Object> sync = syncFromPublishedGovCatalogs(operator);
        List<RcAssetCatalogEntry> pubs = catalogMapper.selectList(new LambdaQueryWrapper<RcAssetCatalogEntry>()
                .eq(RcAssetCatalogEntry::getVisibility, "PUBLIC")
                .eq(RcAssetCatalogEntry::getPublishStatus, "PUBLISHED")
                .eq(RcAssetCatalogEntry::getStatus, "ACTIVE"));
        int ok = 0;
        int fail = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (RcAssetCatalogEntry e : pubs) {
            try {
                results.add(driveCatalogExchange(operator, e.getId()));
                ok++;
            } catch (BusinessException ex) {
                fail++;
                results.add(Map.of("entryId", e.getId(), "error", ex.getMessage()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", pubs.size());
        out.put("success", ok);
        out.put("failed", fail);
        out.put("results", results);
        out.put("syncedFromGov", sync);
        return out;
    }

    /**
     * 读取目录管理流程中已公开/已审核的资源，同步为资源中心公开目录，供各子系统共享并驱动交换。
     */
    @Transactional
    public Map<String, Object> syncFromPublishedGovCatalogs(UserPrincipal operator) {
        List<GovCatalogResource> pubs = catalogResourceMapper.selectList(new LambdaQueryWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getPublishStatus, "PUBLISHED")
                .orderByDesc(GovCatalogResource::getId)
                .last("LIMIT 500"));
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (GovCatalogResource r : pubs) {
            if (r.getResourceCode() == null || r.getResourceCode().isBlank()) {
                skipped++;
                continue;
            }
            RcAssetCatalogEntry exist = catalogMapper.selectOne(new LambdaQueryWrapper<RcAssetCatalogEntry>()
                    .eq(RcAssetCatalogEntry::getEntryCode, r.getResourceCode())
                    .last("LIMIT 1"));
            Long managedTableId = null;
            String physical = r.getPhysicalTableName();
            if (physical != null && !physical.isBlank()) {
                RcManagedTable mt = managedTableMapper.selectOne(new LambdaQueryWrapper<RcManagedTable>()
                        .eq(RcManagedTable::getPhysicalTable, physical.trim())
                        .last("LIMIT 1"));
                if (mt != null) {
                    managedTableId = mt.getId();
                }
            }
            if (exist == null && r.getMetadataEntryCode() != null && !r.getMetadataEntryCode().isBlank()) {
                exist = catalogMapper.selectOne(new LambdaQueryWrapper<RcAssetCatalogEntry>()
                        .eq(RcAssetCatalogEntry::getEntryCode, r.getMetadataEntryCode())
                        .last("LIMIT 1"));
            }
            if (exist == null) {
                RcAssetCatalogEntry e = new RcAssetCatalogEntry();
                e.setEntryCode(r.getResourceCode());
                e.setEntryName(r.getResourceName() == null ? r.getResourceCode() : r.getResourceName());
                e.setManagedTableId(managedTableId);
                e.setSubsystemCode("SHARED");
                e.setVisibility("PUBLIC");
                e.setEncryptEnabled(r.getSecretFlag() != null && r.getSecretFlag() == 1 ? 1 : 0);
                e.setEncryptAlgo(e.getEncryptEnabled() == 1 ? "AES256" : "NONE");
                e.setPublishStatus("PUBLISHED");
                e.setDescription("由公开资源目录同步：" + nullToEmpty(r.getProviderOrg()));
                e.setDriveTask("gov-catalog:" + r.getResourceCode());
                e.setStatus("ACTIVE");
                e.setCreatedBy(operator.getUsername());
                e.setCreatedAt(LocalDateTime.now());
                e.setUpdatedAt(LocalDateTime.now());
                catalogMapper.insert(e);
                created++;
            } else {
                boolean changed = false;
                if (!"PUBLIC".equalsIgnoreCase(nullToEmpty(exist.getVisibility()))
                        || !"PUBLISHED".equalsIgnoreCase(nullToEmpty(exist.getPublishStatus()))) {
                    exist.setVisibility("PUBLIC");
                    exist.setPublishStatus("PUBLISHED");
                    exist.setSubsystemCode("SHARED");
                    changed = true;
                }
                if (managedTableId != null && !managedTableId.equals(exist.getManagedTableId())) {
                    exist.setManagedTableId(managedTableId);
                    changed = true;
                }
                if (changed) {
                    exist.setUpdatedAt(LocalDateTime.now());
                    catalogMapper.updateById(exist);
                    updated++;
                } else {
                    skipped++;
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("govPublished", pubs.size());
        out.put("created", created);
        out.put("updated", updated);
        out.put("skipped", skipped);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_CATALOG_SYNC_GOV", "rc_asset_catalog_entry", "batch",
                "created=" + created + ",updated=" + updated);
        return out;
    }

    public List<Map<String, Object>> listCatalogExchangeJobs(Long entryId) {
        LambdaQueryWrapper<RcCatalogExchangeJob> w = new LambdaQueryWrapper<RcCatalogExchangeJob>()
                .orderByDesc(RcCatalogExchangeJob::getId)
                .last("LIMIT 100");
        if (entryId != null) {
            w.eq(RcCatalogExchangeJob::getCatalogEntryId, entryId);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (RcCatalogExchangeJob j : catalogExchangeJobMapper.selectList(w)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.getId());
            m.put("catalogEntryId", j.getCatalogEntryId());
            m.put("jobCode", j.getJobCode());
            m.put("jobName", j.getJobName());
            m.put("managedTableId", j.getManagedTableId());
            m.put("physicalTable", j.getPhysicalTable());
            m.put("rowCount", j.getRowCount());
            m.put("runStatus", j.getRunStatus());
            m.put("message", j.getMessage());
            m.put("createdBy", j.getCreatedBy());
            m.put("createdAt", j.getCreatedAt() == null ? null : j.getCreatedAt().toString());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> toCatalogView(RcAssetCatalogEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("entryCode", e.getEntryCode());
        m.put("entryName", e.getEntryName());
        m.put("libId", e.getLibId());
        m.put("managedTableId", e.getManagedTableId());
        m.put("subsystemCode", e.getSubsystemCode());
        m.put("subsystemName", subsystemName(e.getSubsystemCode()));
        m.put("visibility", e.getVisibility() == null ? "PRIVATE" : e.getVisibility());
        m.put("encryptEnabled", e.getEncryptEnabled() != null && e.getEncryptEnabled() == 1);
        m.put("encryptAlgo", e.getEncryptAlgo() == null ? "NONE" : e.getEncryptAlgo());
        m.put("publishStatus", e.getPublishStatus() == null ? "DRAFT" : e.getPublishStatus());
        m.put("rejectReason", e.getRejectReason());
        m.put("description", e.getDescription());
        m.put("driveTask", e.getDriveTask());
        m.put("exchangeTaskRef", e.getExchangeTaskRef());
        m.put("lastExchangeAt", e.getLastExchangeAt() == null ? null : e.getLastExchangeAt().toString());
        m.put("lastExchangeMessage", e.getLastExchangeMessage());
        m.put("status", e.getStatus());
        m.put("createdBy", e.getCreatedBy());
        m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        m.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
        if (e.getManagedTableId() != null) {
            RcManagedTable mt = managedTableMapper.selectById(e.getManagedTableId());
            if (mt != null) {
                m.put("physicalTable", mt.getPhysicalTable());
                m.put("metaEntryCode", mt.getMetaEntryCode());
            }
        }
        return m;
    }

    private RcAssetCatalogEntry requireCatalog(Long id) {
        if (id == null) throw new BusinessException(400, "目录 id 必填");
        RcAssetCatalogEntry e = catalogMapper.selectById(id);
        if (e == null) throw new BusinessException(404, "资产目录不存在");
        return e;
    }

    private void requireSysAdmin(UserPrincipal operator) {
        if (operator == null || !operator.isSystemAdmin()) {
            throw new BusinessException(403, "目录公开及审批流程由系统管理员控制");
        }
    }

    private String normalizeSubsystem(String code) {
        String c = code == null ? "RESOURCE" : code.trim().toUpperCase(Locale.ROOT);
        for (String[] s : CATALOG_SUBSYSTEMS) {
            if (s[0].equals(c)) return c;
        }
        throw new BusinessException(400, "未知子系统: " + code);
    }

    private String normalizeEncryptAlgo(String algo) {
        String a = algo == null || algo.isBlank() ? "NONE" : algo.trim().toUpperCase(Locale.ROOT);
        if (!ENCRYPT_ALGOS.contains(a)) {
            throw new BusinessException(400, "不支持的加密算法: " + algo);
        }
        return a;
    }

    private String subsystemName(String code) {
        if (code == null) return "-";
        for (String[] s : CATALOG_SUBSYSTEMS) {
            if (s[0].equalsIgnoreCase(code)) return s[1];
        }
        return code;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean boolVal(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        if ("1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s)) return true;
        if ("0".equals(s) || "false".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s)) return false;
        return def;
    }

    private void validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new BusinessException(400, "启用调度时须填写 Cron 表达式");
        }
        try {
            CronExpression.parse(cron.trim());
        } catch (Exception ex) {
            throw new BusinessException(400, "Cron 表达式非法: " + ex.getMessage());
        }
    }

    private static int parseRetentionDays(Object raw, int defaultDays) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return Math.max(defaultDays, 1);
        }
        try {
            int d = Integer.parseInt(String.valueOf(raw).trim());
            if (d < 1 || d > 3650) {
                throw new BusinessException(400, "保存天数须在 1～3650 之间");
            }
            return d;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "保存天数须为整数");
        }
    }

    /** 生命周期：BACKUP/DESTROY 默认 DB（分层备份库）；ARCHIVE 默认 LOCAL（归档目录）。 */
    private static String normalizeStorageStrategy(String raw, String actionType) {
        String action = actionType == null ? "" : actionType.toUpperCase(Locale.ROOT);
        String def = "ARCHIVE".equals(action) ? "LOCAL" : "DB";
        String s = raw == null || raw.isBlank() ? def : raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DB", "LOCAL", "NAS", "OBJECT").contains(s)) {
            throw new BusinessException(400, "存储策略须为 DB / LOCAL / NAS / OBJECT");
        }
        if ("BACKUP".equals(action) || "DESTROY".equals(action)) {
            if (!"DB".equals(s)) {
                throw new BusinessException(400, "备份/销毁请使用存储策略「DB」（分层备份库快照）");
            }
        }
        return s;
    }

    private static LocalDateTime computeNextRun(String cron, LocalDateTime base) {
        if (cron == null || cron.isBlank()) return null;
        CronExpression expr = CronExpression.parse(cron.trim());
        return expr.next(base == null ? LocalDateTime.now() : base);
    }

    private Path resolveStorageRoot(String strategy) {
        String s = strategy == null ? "LOCAL" : strategy.toUpperCase(Locale.ROOT);
        if ("NAS".equals(s)) return BACKUP_ROOT;
        if ("OBJECT".equals(s)) return OBJECT_ROOT;
        return BACKUP_ROOT;
    }

    private void recordPolicyRun(RcStoragePolicy policy, String runStatus, Long rowCount,
                                 Long artifactId, String storageLocation, String message, String actor) {
        RcPolicyRunLog logRow = new RcPolicyRunLog();
        logRow.setPolicyId(policy.getId());
        logRow.setActionType(policy.getActionType());
        logRow.setRunStatus(runStatus);
        logRow.setRowCount(rowCount);
        logRow.setArtifactId(artifactId);
        logRow.setStorageLocation(storageLocation);
        logRow.setMessage(message);
        logRow.setCreatedBy(actor);
        logRow.setCreatedAt(LocalDateTime.now());
        policyRunLogMapper.insert(logRow);
    }

    private void markPolicyRun(RcStoragePolicy policy, String runStatus, String message) {
        policy.setLastRunAt(LocalDateTime.now());
        policy.setLastRunStatus(runStatus);
        policy.setLastRunMessage(message == null ? null
                : (message.length() > 500 ? message.substring(0, 500) : message));
        policyMapper.updateById(policy);
    }

    /**
     * 兼容旧入口：库名 / 纳管表 / 元数据粗搜。
     * 新页面请用 {@link #searchFullText} / {@link #searchMetadata}。
     */
    public Map<String, Object> searchLibraries(String q) {
        List<Map<String, Object>> hits = new ArrayList<>();
        LambdaQueryWrapper<RcBaseLibrary> query = new LambdaQueryWrapper<RcBaseLibrary>().orderByDesc(RcBaseLibrary::getRecordCount);
        if (q != null && !q.isBlank()) query.like(RcBaseLibrary::getLibName, q);
        for (RcBaseLibrary lib : libraryMapper.selectList(query.last("LIMIT 20"))) {
            hits.add(Map.of("libCode", lib.getLibCode(), "libName", lib.getLibName(),
                    "libType", lib.getLibType(), "recordCount", lib.getRecordCount()));
        }
        for (RcManagedTable mt : managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getStatus, "ACTIVE")
                .like(q != null && !q.isBlank(), RcManagedTable::getPhysicalTable, q)
                .last("LIMIT 20"))) {
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("libCode", mt.getPhysicalTable());
            hit.put("libName", mt.getPhysicalTable());
            hit.put("libType", "MANAGED");
            hit.put("recordCount", mt.getRecordCount() == null ? 0 : mt.getRecordCount());
            hit.put("managedTableId", mt.getId());
            hit.put("metaEntryCode", mt.getMetaEntryCode() == null ? "" : mt.getMetaEntryCode());
            hits.add(hit);
        }
        if (q != null && !q.isBlank()) {
            for (GovMetadataRegistry reg : registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                    .and(w -> w.like(GovMetadataRegistry::getEntryCode, q)
                            .or().like(GovMetadataRegistry::getEntryName, q)
                            .or().like(GovMetadataRegistry::getPhysicalTableName, q))
                    .last("LIMIT 20"))) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("libCode", reg.getEntryCode() == null ? "" : reg.getEntryCode());
                hit.put("libName", reg.getEntryName() == null ? "" : reg.getEntryName());
                hit.put("libType", "METADATA");
                hit.put("recordCount", 0);
                hit.put("physicalTable", reg.getPhysicalTableName() == null ? "" : reg.getPhysicalTableName());
                hits.add(hit);
            }
        }
        return Map.of("query", q == null ? "" : q, "hits", hits);
    }

    /**
     * 数据全文检索：在已纳管物理表业务数据中按关键词检索（姓名/身份证/手机号等）。
     */
    public Map<String, Object> searchFullText(String q, Integer perTableLimit, Integer maxTables) {
        if (q == null || q.isBlank()) {
            throw new BusinessException(400, "请输入关键词（如姓名、身份证号码、手机号）");
        }
        String keyword = q.trim();
        if (keyword.length() > 64) {
            throw new BusinessException(400, "关键词过长（最多 64 字）");
        }
        int per = perTableLimit == null ? 10 : Math.min(Math.max(perTableLimit, 1), 50);
        int tableCap = maxTables == null ? 30 : Math.min(Math.max(maxTables, 1), 80);
        List<RcManagedTable> tables = managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getStatus, "ACTIVE")
                .orderByDesc(RcManagedTable::getId)
                .last("LIMIT " + tableCap));
        List<Map<String, Object>> hits = new ArrayList<>();
        List<String> scanned = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        String like = "%" + escapeLike(keyword) + "%";
        try (Connection conn = platformDataSource.getConnection()) {
            for (RcManagedTable mt : tables) {
                String physical;
                try {
                    physical = requireIdent(mt.getPhysicalTable(), "physicalTable");
                } catch (BusinessException e) {
                    skipped.add(mt.getPhysicalTable() + "(非法表名)");
                    continue;
                }
                if (!tableExists(physical)) {
                    skipped.add(physical + "(表不存在)");
                    continue;
                }
                List<String> searchable = listSearchableColumns(conn, physical);
                if (searchable.isEmpty()) {
                    skipped.add(physical + "(无可检索列)");
                    continue;
                }
                scanned.add(physical);
                StringBuilder sql = new StringBuilder("SELECT * FROM `").append(physical).append("` WHERE ");
                for (int i = 0; i < searchable.size(); i++) {
                    if (i > 0) sql.append(" OR ");
                    sql.append("`").append(searchable.get(i)).append("` LIKE ? ESCAPE '\\\\'");
                }
                sql.append(" LIMIT ").append(per);
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < searchable.size(); i++) {
                        ps.setString(i + 1, like);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int cc = meta.getColumnCount();
                        List<String> columns = new ArrayList<>();
                        for (int i = 1; i <= cc; i++) columns.add(meta.getColumnLabel(i));
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            List<String> matched = new ArrayList<>();
                            for (String col : columns) {
                                Object v = rs.getObject(col);
                                String sv = v == null ? null : String.valueOf(v);
                                row.put(col, sv);
                                if (sv != null && sv.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                                    matched.add(col);
                                }
                            }
                            Map<String, Object> hit = new LinkedHashMap<>();
                            hit.put("hitType", "DATA");
                            hit.put("managedTableId", mt.getId());
                            hit.put("physicalTable", physical);
                            hit.put("metaEntryCode", mt.getMetaEntryCode() == null ? "" : mt.getMetaEntryCode());
                            hit.put("matchedColumns", matched);
                            hit.put("summary", buildRowSummary(row, matched, keyword));
                            hit.put("row", row);
                            hits.add(hit);
                        }
                    }
                } catch (Exception e) {
                    log.warn("全文检索跳过表 {}: {}", physical, e.getMessage());
                    skipped.add(physical + "(" + e.getMessage() + ")");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "全文检索失败: " + e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "FULLTEXT");
        out.put("query", keyword);
        out.put("hitCount", hits.size());
        out.put("scannedTables", scanned);
        out.put("skippedTables", skipped);
        out.put("hits", hits);
        out.put("hint", "在已纳管物理表业务数据中检索；可点开详情，或锁定物理表后做条件查询/下载");
        return out;
    }

    /**
     * 元数据检索：按分类/标签/数据项/关键词从元数据库定位物理表。
     */
    public Map<String, Object> searchMetadata(String q, String tag, String domain, String dataItem) {
        boolean hasCond = (q != null && !q.isBlank())
                || (tag != null && !tag.isBlank())
                || (domain != null && !domain.isBlank())
                || (dataItem != null && !dataItem.isBlank());
        if (!hasCond) {
            throw new BusinessException(400, "请至少填写关键词、标签、业务分类或数据项之一");
        }
        String kw = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String tg = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
        String dm = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        String di = dataItem == null ? "" : dataItem.trim().toLowerCase(Locale.ROOT);

        List<GovMetadataRegistry> all = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .ne(GovMetadataRegistry::getStatus, "OFFLINE")
                .orderByDesc(GovMetadataRegistry::getUpdatedAt)
                .last("LIMIT 500"));

        Map<String, RcManagedTable> managedByTable = new LinkedHashMap<>();
        Map<String, RcManagedTable> managedByEntry = new LinkedHashMap<>();
        for (RcManagedTable mt : managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getStatus, "ACTIVE"))) {
            if (mt.getPhysicalTable() != null) {
                managedByTable.put(mt.getPhysicalTable().toLowerCase(Locale.ROOT), mt);
            }
            if (mt.getMetaEntryCode() != null && !mt.getMetaEntryCode().isBlank()) {
                managedByEntry.put(mt.getMetaEntryCode().toLowerCase(Locale.ROOT), mt);
            }
        }

        Set<String> keepTableCodes = new HashSet<>();
        Map<String, List<String>> matchedItemsByParent = new LinkedHashMap<>();
        for (GovMetadataRegistry e : all) {
            if ("COLUMN".equalsIgnoreCase(e.getEntryType()) && !di.isEmpty()) {
                String name = nvl(e.getEntryName()).toLowerCase(Locale.ROOT);
                String code = nvl(e.getEntryCode()).toLowerCase(Locale.ROOT);
                String kws = nvl(e.getKeywords()).toLowerCase(Locale.ROOT);
                if (name.contains(di) || code.contains(di) || kws.contains(di)) {
                    if (e.getParentCode() != null) {
                        keepTableCodes.add(e.getParentCode());
                        matchedItemsByParent
                                .computeIfAbsent(e.getParentCode(), k -> new ArrayList<>())
                                .add(e.getEntryName() == null ? e.getEntryCode() : e.getEntryName());
                    }
                }
            }
        }

        List<Map<String, Object>> hits = new ArrayList<>();
        for (GovMetadataRegistry e : all) {
            boolean isTable = "TABLE".equalsIgnoreCase(e.getEntryType())
                    || (e.getPhysicalTableName() != null && !e.getPhysicalTableName().isBlank()
                    && !"COLUMN".equalsIgnoreCase(e.getEntryType())
                    && !"SOURCE".equalsIgnoreCase(e.getEntryType()));
            if (!isTable && !keepTableCodes.contains(e.getEntryCode())) {
                continue;
            }
            if ("COLUMN".equalsIgnoreCase(e.getEntryType()) || "SOURCE".equalsIgnoreCase(e.getEntryType())) {
                continue;
            }
            boolean kwOk = kw.isEmpty()
                    || containsIgnore(e.getEntryCode(), kw)
                    || containsIgnore(e.getEntryName(), kw)
                    || containsIgnore(e.getPhysicalTableName(), kw)
                    || containsIgnore(e.getDescription(), kw)
                    || containsIgnore(e.getKeywords(), kw)
                    || containsIgnore(e.getTags(), kw);
            boolean tagOk = tg.isEmpty() || containsIgnore(e.getTags(), tg);
            boolean domainOk = dm.isEmpty()
                    || containsIgnore(e.getBusinessDomain(), dm)
                    || containsIgnore(e.getEntryType(), dm)
                    || containsIgnore(e.getDataLayer(), dm);
            boolean dataItemOk = di.isEmpty() || keepTableCodes.contains(e.getEntryCode());
            if (!(kwOk && tagOk && domainOk && dataItemOk)) {
                continue;
            }

            RcManagedTable mt = null;
            if (e.getEntryCode() != null) {
                mt = managedByEntry.get(e.getEntryCode().toLowerCase(Locale.ROOT));
            }
            if (mt == null && e.getPhysicalTableName() != null) {
                mt = managedByTable.get(e.getPhysicalTableName().toLowerCase(Locale.ROOT));
            }
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("hitType", "METADATA");
            hit.put("entryCode", e.getEntryCode() == null ? "" : e.getEntryCode());
            hit.put("entryName", e.getEntryName() == null ? "" : e.getEntryName());
            hit.put("entryType", e.getEntryType() == null ? "" : e.getEntryType());
            hit.put("physicalTable", e.getPhysicalTableName() == null ? "" : e.getPhysicalTableName());
            hit.put("dataLayer", e.getDataLayer() == null ? "" : e.getDataLayer());
            hit.put("businessDomain", e.getBusinessDomain() == null ? "" : e.getBusinessDomain());
            hit.put("tags", e.getTags() == null ? "" : e.getTags());
            hit.put("keywords", e.getKeywords() == null ? "" : e.getKeywords());
            hit.put("description", e.getDescription() == null ? "" : e.getDescription());
            hit.put("matchedDataItems", matchedItemsByParent.getOrDefault(e.getEntryCode(), List.of()));
            hit.put("managed", mt != null);
            hit.put("managedTableId", mt == null ? null : mt.getId());
            hit.put("recordCount", mt == null || mt.getRecordCount() == null ? 0 : mt.getRecordCount());
            hits.add(hit);
            if (hits.size() >= 100) break;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "METADATA");
        out.put("query", q == null ? "" : q.trim());
        out.put("tag", tag == null ? "" : tag.trim());
        out.put("domain", domain == null ? "" : domain.trim());
        out.put("dataItem", dataItem == null ? "" : dataItem.trim());
        out.put("hitCount", hits.size());
        out.put("hits", hits);
        out.put("hint", "找到元数据后可锁定已纳管物理表，再按条件浏览或下载业务数据");
        return out;
    }

    public Map<String, Object> queryManagedTable(Long managedTableId, Integer limit) {
        return queryManagedTable(managedTableId, limit, null, null);
    }

    /**
     * 数据查询：可选关键词/指定列条件过滤后预览，供下载前锁定。
     */
    public Map<String, Object> queryManagedTable(Long managedTableId, Integer limit, String keyword, String column) {
        if (managedTableId == null) throw new BusinessException(400, "managedTableId required");
        RcManagedTable mt = managedTableMapper.selectById(managedTableId);
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(404, "纳管表不存在或已解绑");
        }
        String table = requireIdent(mt.getPhysicalTable(), "physicalTable");
        int lim = limit == null ? 100 : Math.min(Math.max(limit, 1), 500);
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        String appliedFilter = "";
        try (Connection conn = platformDataSource.getConnection()) {
            String sql;
            List<String> params = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim();
                if (kw.length() > 64) throw new BusinessException(400, "查询关键词过长");
                String like = "%" + escapeLike(kw) + "%";
                List<String> cols;
                if (column != null && !column.isBlank()) {
                    String col = requireIdent(column.trim(), "column");
                    if (!columnExists(table, col)) {
                        throw new BusinessException(400, "列不存在: " + col);
                    }
                    cols = List.of(col);
                    appliedFilter = col + " LIKE '%" + kw + "%'";
                } else {
                    cols = listSearchableColumns(conn, table);
                    if (cols.isEmpty()) {
                        throw new BusinessException(400, "该表无可检索字符列，请去掉关键词后查询");
                    }
                    appliedFilter = "多列 LIKE '%" + kw + "%'";
                }
                StringBuilder sb = new StringBuilder("SELECT * FROM `").append(table).append("` WHERE ");
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) sb.append(" OR ");
                    sb.append("`").append(cols.get(i)).append("` LIKE ? ESCAPE '\\\\'");
                    params.add(like);
                }
                sb.append(" LIMIT ").append(lim);
                sql = sb.toString();
            } else {
                sql = "SELECT * FROM `" + table + "` LIMIT " + lim;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setString(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cc = meta.getColumnCount();
                    for (int i = 1; i <= cc; i++) columns.add(meta.getColumnLabel(i));
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (String col : columns) {
                            Object v = rs.getObject(col);
                            row.put(col, v == null ? null : String.valueOf(v));
                        }
                        rows.add(row);
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "查询失败: " + e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("managedTableId", managedTableId);
        out.put("physicalTable", table);
        out.put("metaEntryCode", mt.getMetaEntryCode());
        out.put("limit", lim);
        out.put("filter", appliedFilter);
        out.put("keyword", keyword == null ? "" : keyword.trim());
        out.put("column", column == null ? "" : column.trim());
        out.put("columns", columns);
        out.put("rows", rows);
        out.put("rowCount", rows.size());
        return out;
    }

    private List<String> listSearchableColumns(Connection conn, String table) throws Exception {
        List<String> cols = new ArrayList<>();
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(conn.getCatalog(), null, table, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                if (name == null || !IDENT.matcher(name).matches()) continue;
                if (type == null) continue;
                String t = type.toUpperCase(Locale.ROOT);
                if (t.contains("CHAR") || t.contains("TEXT") || t.contains("JSON") || t.contains("ENUM")) {
                    cols.add(name);
                }
            }
        }
        // 优先常见关键业务列
        cols.sort((a, b) -> Integer.compare(personKeyScore(b), personKeyScore(a)));
        if (cols.size() > 24) {
            return new ArrayList<>(cols.subList(0, 24));
        }
        return cols;
    }

    private static int personKeyScore(String col) {
        String c = col.toLowerCase(Locale.ROOT);
        if (c.contains("id_card") || c.contains("idcard") || c.equals("sfzh") || c.contains("身份证")) return 100;
        if (c.contains("mobile") || c.contains("phone") || c.equals("sjh") || c.contains("手机")) return 90;
        if (c.equals("name") || c.equals("xm") || c.contains("姓名") || c.endsWith("_name")) return 80;
        if (c.contains("person") || c.contains("entity")) return 50;
        return 0;
    }

    private static String buildRowSummary(Map<String, Object> row, List<String> matched, String keyword) {
        if (matched != null && !matched.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (String col : matched) {
                if (n++ >= 3) break;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(col).append("=").append(row.get(col));
            }
            return sb.toString();
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object v = e.getValue();
            if (v != null && String.valueOf(v).contains(keyword)) {
                return e.getKey() + "=" + v;
            }
        }
        return keyword;
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static boolean containsIgnore(String hay, String needleLower) {
        return hay != null && hay.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    public List<RcStoragePolicy> listPolicies(String actionType) {
        LambdaQueryWrapper<RcStoragePolicy> q = new LambdaQueryWrapper<RcStoragePolicy>().orderByDesc(RcStoragePolicy::getId);
        if (actionType != null && !actionType.isBlank()) {
            q.eq(RcStoragePolicy::getActionType, actionType.toUpperCase(Locale.ROOT));
        }
        return policyMapper.selectList(q);
    }

    public Map<String, Object> statistics() {
        long managedRows = managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                        .eq(RcManagedTable::getStatus, "ACTIVE")).stream()
                .mapToLong(m -> m.getRecordCount() == null ? 0 : m.getRecordCount()).sum();
        long totalRecords = libraryMapper.selectList(null).stream()
                .mapToLong(l -> l.getRecordCount() == null ? 0 : l.getRecordCount()).sum() + managedRows;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalRecords", totalRecords);
        out.put("libraryCount", libraryMapper.selectCount(null));
        out.put("themeCount", themeMapper.selectCount(null));
        out.put("managedTableCount", managedTableMapper.selectCount(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getStatus, "ACTIVE")));
        out.put("topLibraries", libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>()
                .orderByDesc(RcBaseLibrary::getRecordCount).last("LIMIT 5")));
        return out;
    }

    public List<RcMonitorMetric> monitorMetrics() {
        return monitorMapper.selectList(new LambdaQueryWrapper<RcMonitorMetric>().orderByAsc(RcMonitorMetric::getId));
    }

    // ---------- helpers ----------

    private void collectMonitorMetrics() {
        List<RcManagedTable> tables = managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getStatus, "ACTIVE"));
        long totalRows = 0;
        long totalBytes = 0;
        int missingPhysical = 0;
        for (RcManagedTable mt : tables) {
            refreshTableStats(mt);
            totalRows += mt.getRecordCount() == null ? 0 : mt.getRecordCount();
            totalBytes += (mt.getDataBytes() == null ? 0 : mt.getDataBytes())
                    + (mt.getIndexBytes() == null ? 0 : mt.getIndexBytes());
            if (mt.getPhysicalTable() == null || mt.getPhysicalTable().isBlank() || !tableExists(mt.getPhysicalTable())) {
                missingPhysical++;
            }
        }

        // —— 可用性：库服务 / 存储 / 传输通道 ——
        long dbMs = pingDbMs();
        boolean dbUp = dbMs >= 0;
        upsertMetric("db_service", "AVAILABILITY", "数据库服务", dbUp ? "UP" : "DOWN",
                null, "DB", dbUp ? "OK" : "CRITICAL");
        upsertMetric("db_ping_ms", "AVAILABILITY", "数据库探活耗时", dbUp ? String.valueOf(dbMs) : "-",
                "ms", "DB", !dbUp ? "CRITICAL" : (dbMs > 500 ? "WARN" : "OK"));

        boolean backupWritable = pathWritable(BACKUP_ROOT);
        boolean archiveWritable = pathWritable(ARCHIVE_ROOT);
        upsertMetric("storage_backup", "AVAILABILITY", "备份存储目录",
                backupWritable ? "UP" : "DOWN", null, "STORAGE", backupWritable ? "OK" : "CRITICAL");
        upsertMetric("storage_archive", "AVAILABILITY", "归档存储目录",
                archiveWritable ? "UP" : "DOWN", null, "STORAGE", archiveWritable ? "OK" : "WARN");

        boolean seaweed = false;
        boolean es = false;
        try {
            seaweed = storageIntegrationClient.isSeaweedHealthy();
            es = storageIntegrationClient.isElasticsearchHealthy();
        } catch (Exception e) {
            log.debug("storage probe skipped: {}", e.getMessage());
        }
        boolean objectFallback = pathWritable(OBJECT_ROOT);
        String objectVal;
        String objectLevel;
        if (seaweed) {
            objectVal = "UP";
            objectLevel = "OK";
        } else if (objectFallback) {
            objectVal = "LOCAL_FALLBACK";
            objectLevel = "WARN";
        } else {
            objectVal = "DOWN";
            objectLevel = "CRITICAL";
        }
        upsertMetric("storage_object", "AVAILABILITY", "对象存储服务", objectVal, null, "STORAGE", objectLevel);
        upsertMetric("storage_index", "AVAILABILITY", "检索索引服务",
                es ? "UP" : "LEDGER", null, "STORAGE", es ? "OK" : "WARN");

        LocalDateTime since = LocalDateTime.now().minusDays(1);
        List<RcCatalogExchangeJob> recentJobs = catalogExchangeJobMapper.selectList(
                new LambdaQueryWrapper<RcCatalogExchangeJob>()
                        .ge(RcCatalogExchangeJob::getCreatedAt, since)
                        .orderByDesc(RcCatalogExchangeJob::getId));
        long chFail = recentJobs.stream()
                .filter(j -> "FAILED".equalsIgnoreCase(nullToEmpty(j.getRunStatus()))).count();
        long chOk = recentJobs.stream()
                .filter(j -> !"FAILED".equalsIgnoreCase(nullToEmpty(j.getRunStatus()))).count();
        upsertMetric("channel_exchange_24h", "AVAILABILITY", "近24h目录交换通道",
                recentJobs.isEmpty() ? "IDLE" : (chOk + "成功/" + chFail + "失败"),
                null, "CHANNEL",
                chFail > 0 ? "CRITICAL" : (recentJobs.isEmpty() ? "WARN" : "OK"));
        upsertMetric("managed_tables", "AVAILABILITY", "纳管表数量",
                String.valueOf(tables.size()), "张", "DB", tables.isEmpty() ? "WARN" : "OK");

        // —— 完整性：物理表存在 / 备份校验 / 交换行数 ——
        upsertMetric("physical_missing", "INTEGRITY", "纳管表物理缺失",
                String.valueOf(missingPhysical), "张", "DB",
                missingPhysical > 0 ? "CRITICAL" : "OK");
        List<RcBackupArtifact> artifacts = backupArtifactMapper.selectList(
                new LambdaQueryWrapper<RcBackupArtifact>().orderByDesc(RcBackupArtifact::getId).last("LIMIT 200"));
        long withSha = artifacts.stream()
                .filter(a -> a.getSha256() != null && !a.getSha256().isBlank()).count();
        long shaPct = artifacts.isEmpty() ? 100 : Math.round(withSha * 100.0 / artifacts.size());
        upsertMetric("backup_sha_coverage", "INTEGRITY", "备份SHA覆盖率",
                String.valueOf(shaPct), "%", "BACKUP",
                shaPct < 80 ? "WARN" : "OK");
        int verifyOk = 0;
        int verifyFail = 0;
        int sampled = 0;
        for (RcBackupArtifact art : artifacts) {
            if (sampled >= 3) break;
            if (art.getFilePath() == null || art.getSha256() == null || art.getSha256().isBlank()) continue;
            sampled++;
            try {
                Path p = Path.of(art.getFilePath());
                if (!Files.exists(p)) {
                    verifyFail++;
                    continue;
                }
                if (art.getSha256().equalsIgnoreCase(sha256(p))) verifyOk++;
                else verifyFail++;
            } catch (Exception e) {
                verifyFail++;
            }
        }
        upsertMetric("backup_verify_sample", "INTEGRITY", "备份抽样校验",
                sampled == 0 ? "无样本" : (verifyOk + "通过/" + verifyFail + "失败"),
                null, "BACKUP",
                verifyFail > 0 ? "CRITICAL" : (sampled == 0 ? "WARN" : "OK"));
        long exchangeRows = recentJobs.stream()
                .mapToLong(j -> j.getRowCount() == null ? 0 : j.getRowCount()).sum();
        upsertMetric("exchange_rows_24h", "INTEGRITY", "近24h交换行数",
                String.valueOf(exchangeRows), "行", "CHANNEL", "OK");
        upsertMetric("total_rows", "INTEGRITY", "纳管表总行数",
                String.valueOf(totalRows), "行", "DB", totalRows > 0 ? "OK" : "WARN");

        // —— 安全性：加密 / 权限可见性 / 访问审计 ——
        List<RcAssetCatalogEntry> catalogs = catalogMapper.selectList(null);
        long encrypted = catalogs.stream()
                .filter(c -> c.getEncryptEnabled() != null && c.getEncryptEnabled() == 1).count();
        long published = catalogs.stream()
                .filter(c -> "PUBLISHED".equalsIgnoreCase(nullToEmpty(c.getPublishStatus()))).count();
        long privates = catalogs.stream()
                .filter(c -> !"PUBLIC".equalsIgnoreCase(nullToEmpty(c.getVisibility()))).count();
        upsertMetric("catalog_encrypt", "SECURITY", "目录加密启用",
                encrypted + "/" + catalogs.size(), null, "CATALOG",
                catalogs.isEmpty() ? "WARN" : "OK");
        upsertMetric("catalog_visibility", "SECURITY", "未公开目录占比",
                catalogs.isEmpty() ? "0" : String.valueOf(Math.round(privates * 100.0 / catalogs.size())),
                "%", "CATALOG", "OK");
        upsertMetric("catalog_published", "SECURITY", "已公开目录数",
                String.valueOf(published), "条", "CATALOG", "OK");

        LocalDateTime auditSince = LocalDateTime.now().minusDays(1);
        List<AuditLog> rcAudits = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .and(w -> w.likeRight(AuditLog::getAction, "RC_")
                        .or().likeRight(AuditLog::getResourceType, "rc_"))
                .ge(AuditLog::getCreatedAt, auditSince)
                .orderByDesc(AuditLog::getId)
                .last("LIMIT 500"));
        upsertMetric("access_audit_24h", "SECURITY", "近24h资源访问审计",
                String.valueOf(rcAudits.size()), "条", "AUDIT",
                rcAudits.isEmpty() ? "WARN" : "OK");
        long distinctIp = rcAudits.stream()
                .map(a -> a.getIpAddress() == null ? "" : a.getIpAddress())
                .filter(s -> !s.isBlank())
                .distinct().count();
        long distinctUser = rcAudits.stream()
                .map(a -> a.getUsername() == null ? "" : a.getUsername())
                .filter(s -> !s.isBlank())
                .distinct().count();
        // 简单异常：单 IP 访问量占比过高
        Map<String, Long> ipCount = new LinkedHashMap<>();
        for (AuditLog a : rcAudits) {
            String ip = a.getIpAddress() == null ? "-" : a.getIpAddress();
            ipCount.merge(ip, 1L, Long::sum);
        }
        long maxIpHits = ipCount.values().stream().mapToLong(Long::longValue).max().orElse(0);
        boolean anomaly = !rcAudits.isEmpty() && rcAudits.size() >= 20 && maxIpHits * 100.0 / rcAudits.size() >= 80;
        upsertMetric("access_anomaly", "SECURITY", "异常访问探测",
                anomaly ? "单IP占比偏高(" + maxIpHits + "/" + rcAudits.size() + ")"
                        : ("IP=" + distinctIp + " 用户=" + distinctUser),
                null, "AUDIT", anomaly ? "WARN" : "OK");

        // —— 性能：响应时间 / 存储规模 / 传输吞吐 ——
        long queryMs = sampleQueryMs(tables);
        upsertMetric("query_latency", "PERFORMANCE", "纳管表抽样查询耗时",
                queryMs < 0 ? "-" : String.valueOf(queryMs), "ms", "PERF",
                queryMs < 0 ? "WARN" : (queryMs > 1000 ? "WARN" : "OK"));
        upsertMetric("db_response", "PERFORMANCE", "数据库响应时间",
                dbUp ? String.valueOf(dbMs) : "-", "ms", "PERF",
                !dbUp ? "CRITICAL" : (dbMs > 300 ? "WARN" : "OK"));
        double gb = totalBytes / (1024.0 * 1024.0 * 1024.0);
        upsertMetric("storage_capacity", "PERFORMANCE", "纳管表存储容量",
                String.format(Locale.ROOT, "%.3f", gb), "GB", "PERF", "OK");
        upsertMetric("total_bytes", "PERFORMANCE", "纳管表存储字节",
                String.valueOf(totalBytes), "B", "PERF", "OK");
        long transferRows = exchangeRows;
        upsertMetric("transfer_throughput_24h", "PERFORMANCE", "近24h传输行量",
                String.valueOf(transferRows), "行", "CHANNEL", "OK");
        List<RcPolicyRunLog> runs = policyRunLogMapper.selectList(new LambdaQueryWrapper<RcPolicyRunLog>()
                .ge(RcPolicyRunLog::getCreatedAt, since)
                .orderByDesc(RcPolicyRunLog::getId)
                .last("LIMIT 100"));
        upsertMetric("policy_runs_24h", "PERFORMANCE", "近24h策略执行次数",
                String.valueOf(runs.size()), "次", "PERF", "OK");
    }

    private Map<String, Object> toMetricView(RcMonitorMetric m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("metricKey", m.getMetricKey());
        row.put("metricCategory", m.getMetricCategory());
        row.put("metricLabel", m.getMetricLabel());
        row.put("metricValue", m.getMetricValue());
        row.put("metricUnit", m.getMetricUnit());
        row.put("resourceType", m.getResourceType());
        row.put("alertLevel", m.getAlertLevel());
        row.put("checkedAt", m.getCheckedAt());
        return row;
    }

    private String categoryHealth(List<Map<String, Object>> rows) {
        boolean critical = rows.stream().anyMatch(r -> "CRITICAL".equalsIgnoreCase(String.valueOf(r.get("alertLevel"))));
        if (critical) return "CRITICAL";
        boolean warn = rows.stream().anyMatch(r -> "WARN".equalsIgnoreCase(String.valueOf(r.get("alertLevel"))));
        if (warn) return "WARN";
        return rows.isEmpty() ? "WARN" : "OK";
    }

    private List<Map<String, Object>> listMonitorChannels() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RcCatalogExchangeJob j : catalogExchangeJobMapper.selectList(
                new LambdaQueryWrapper<RcCatalogExchangeJob>()
                        .orderByDesc(RcCatalogExchangeJob::getId).last("LIMIT 30"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("channelType", "CATALOG_EXCHANGE");
            row.put("jobCode", j.getJobCode());
            row.put("jobName", j.getJobName());
            row.put("physicalTable", j.getPhysicalTable());
            row.put("rowCount", j.getRowCount());
            row.put("runStatus", j.getRunStatus());
            row.put("message", j.getMessage());
            row.put("createdAt", j.getCreatedAt());
            out.add(row);
        }
        for (RcBackupJob job : backupJobMapper.selectList(
                new LambdaQueryWrapper<RcBackupJob>().orderByDesc(RcBackupJob::getId).last("LIMIT 20"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("channelType", "BACKUP_TRANSFER");
            row.put("jobCode", "BJ-" + job.getId());
            row.put("jobName", job.getJobName() != null ? job.getJobName() : "备份传输");
            row.put("physicalTable", null);
            row.put("rowCount", null);
            row.put("runStatus", job.getStatus());
            row.put("message", job.getLastMessage());
            row.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt() : job.getLastRunAt());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> listMonitorAudits(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<AuditLog> logs = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .and(w -> w.likeRight(AuditLog::getAction, "RC_")
                        .or().likeRight(AuditLog::getResourceType, "rc_"))
                .orderByDesc(AuditLog::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        for (AuditLog a : logs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("action", a.getAction());
            row.put("username", a.getUsername());
            row.put("resourceType", a.getResourceType());
            row.put("resourceId", a.getResourceId());
            row.put("detail", a.getDetail());
            row.put("ipAddress", a.getIpAddress());
            row.put("createdAt", a.getCreatedAt());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> listIntegritySamples(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RcBackupArtifact art : backupArtifactMapper.selectList(
                new LambdaQueryWrapper<RcBackupArtifact>().orderByDesc(RcBackupArtifact::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", art.getId());
            row.put("physicalTable", art.getPhysicalTable());
            row.put("fileName", art.getFileName());
            row.put("rowCount", art.getRowCount());
            row.put("byteSize", art.getByteSize());
            row.put("sha256", art.getSha256());
            row.put("status", art.getStatus());
            row.put("createdAt", art.getCreatedAt());
            boolean fileOk = art.getFilePath() != null && Files.exists(Path.of(art.getFilePath()));
            row.put("fileExists", fileOk);
            out.add(row);
        }
        return out;
    }

    private boolean pathWritable(Path root) {
        try {
            Files.createDirectories(root);
            Path probe = root.resolve(".monitor-probe");
            Files.writeString(probe, "ok", StandardCharsets.UTF_8);
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private long pingDbMs() {
        long start = System.nanoTime();
        try (Connection c = platformDataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT 1");
            return Math.max(0, (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            return -1;
        }
    }

    private long sampleQueryMs(List<RcManagedTable> tables) {
        for (RcManagedTable mt : tables) {
            if (mt.getPhysicalTable() == null || !IDENT.matcher(mt.getPhysicalTable()).matches()) continue;
            if (!tableExists(mt.getPhysicalTable())) continue;
            long start = System.nanoTime();
            try (Connection c = platformDataSource.getConnection();
                 Statement s = c.createStatement()) {
                s.execute("SELECT * FROM `" + mt.getPhysicalTable() + "` LIMIT 1");
                return Math.max(0, (System.nanoTime() - start) / 1_000_000);
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    private void refreshTableStats(RcManagedTable mt) {
        try (Connection conn = platformDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH FROM information_schema.TABLES "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            ps.setString(1, mt.getPhysicalTable());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    mt.setRecordCount(rs.getLong(1));
                    mt.setDataBytes(rs.getLong(2));
                    mt.setIndexBytes(rs.getLong(3));
                }
            }
            mt.setUpdatedAt(LocalDateTime.now());
            managedTableMapper.updateById(mt);
        } catch (Exception ignored) {
            // keep previous stats
        }
    }

    private void cleanupOldBackups(Long managedTableId, int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(retentionDays, 1));
        List<RcBackupArtifact> old = backupArtifactMapper.selectList(new LambdaQueryWrapper<RcBackupArtifact>()
                .eq(RcBackupArtifact::getManagedTableId, managedTableId)
                .lt(RcBackupArtifact::getCreatedAt, threshold));
        for (RcBackupArtifact art : old) {
            try { Files.deleteIfExists(Path.of(art.getFilePath())); } catch (Exception ignored) {}
            backupArtifactMapper.deleteById(art.getId());
        }
    }

    private void upsertMetric(String key, String category, String label, String value,
                              String unit, String resourceType, String level) {
        RcMonitorMetric m = monitorMapper.selectOne(new LambdaQueryWrapper<RcMonitorMetric>()
                .eq(RcMonitorMetric::getMetricKey, key).last("LIMIT 1"));
        if (m == null) {
            m = new RcMonitorMetric();
            m.setMetricKey(key);
            m.setMetricCategory(category);
            m.setMetricLabel(label);
            m.setMetricValue(value);
            m.setMetricUnit(unit);
            m.setResourceType(resourceType);
            m.setAlertLevel(level);
            m.setCheckedAt(LocalDateTime.now());
            monitorMapper.insert(m);
        } else {
            m.setMetricCategory(category);
            m.setMetricLabel(label);
            m.setMetricValue(value);
            m.setMetricUnit(unit);
            m.setResourceType(resourceType);
            m.setAlertLevel(level);
            m.setCheckedAt(LocalDateTime.now());
            monitorMapper.updateById(m);
        }
    }

    private boolean pingDb() {
        return pingDbMs() >= 0;
    }

    private boolean tableExists(String table) {
        try (Connection c = platformDataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getTables(c.getCatalog(), null, table, new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean columnExists(String table, String column) {
        try (Connection c = platformDataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(c.getCatalog(), null, table, column)) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasUniqueOrPrimary(String table) {
        try (Connection c = platformDataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet pk = md.getPrimaryKeys(c.getCatalog(), null, table)) {
                if (pk.next()) return true;
            }
            try (ResultSet idx = md.getIndexInfo(c.getCatalog(), null, table, true, false)) {
                return idx.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAlreadyPartitioned(String table) {
        try (Connection c = platformDataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.PARTITIONS "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND PARTITION_NAME IS NOT NULL")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String findMetaEntry(String physical) {
        GovMetadataRegistry e = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getPhysicalTableName, physical)
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .orderByDesc(GovMetadataRegistry::getId).last("LIMIT 1"));
        return e == null ? null : e.getEntryCode();
    }

    private String findCatalogCode(String physical) {
        GovCatalogResource r = catalogResourceMapper.selectOne(new LambdaQueryWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getPhysicalTableName, physical)
                .orderByDesc(GovCatalogResource::getId).last("LIMIT 1"));
        return r == null ? null : r.getResourceCode();
    }

    private Map<String, Object> themeRow(RcThemeLibrary t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("themeCode", t.getThemeCode());
        m.put("themeName", t.getThemeName());
        m.put("libraryKind", t.getLibraryKind());
        m.put("zoneCode", t.getZoneCode());
        m.put("ownerOrg", t.getOwnerOrg());
        m.put("partitionKey", t.getPartitionKey());
        m.put("status", t.getStatus());
        m.put("description", t.getDescription());
        return m;
    }

    private Map<String, Object> managedRow(RcManagedTable mt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mt.getId());
        m.put("themeId", mt.getThemeId());
        m.put("libId", mt.getLibId());
        m.put("assetType", mt.getAssetType());
        m.put("physicalTable", mt.getPhysicalTable());
        m.put("metaEntryCode", mt.getMetaEntryCode());
        m.put("catalogResourceCode", mt.getCatalogResourceCode());
        m.put("recordCount", mt.getRecordCount());
        m.put("dataBytes", mt.getDataBytes());
        m.put("indexBytes", mt.getIndexBytes());
        m.put("status", mt.getStatus());
        RcThemeLibrary theme = themeMapper.selectById(mt.getThemeId());
        if (theme != null) {
            m.put("themeCode", theme.getThemeCode());
            m.put("themeName", theme.getThemeName());
            m.put("libraryKind", theme.getLibraryKind());
            m.put("zoneCode", theme.getZoneCode());
        }
        if (mt.getLibId() != null) {
            RcBaseLibrary lib = libraryMapper.selectById(mt.getLibId());
            if (lib != null) {
                m.put("libCode", lib.getLibCode());
                m.put("libName", lib.getLibName());
                m.put("libType", lib.getLibType());
            }
        }
        return m;
    }

    private List<Map<String, Object>> enrichLibraries(List<RcBaseLibrary> libs, List<Map<String, Object>> managed) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RcBaseLibrary lib : libs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", lib.getId());
            m.put("libCode", lib.getLibCode());
            m.put("libName", lib.getLibName());
            m.put("libType", lib.getLibType());
            m.put("recordCount", lib.getRecordCount());
            m.put("status", lib.getStatus());
            m.put("description", lib.getDescription());
            m.put("ownerOrg", lib.getOwnerOrg());
            m.put("sortOrder", lib.getSortOrder());
            long bound = managed.stream()
                    .filter(t -> lib.getId().equals(t.get("libId"))
                            || (lib.getLibType() != null && lib.getLibType().equals(t.get("assetType"))
                            && t.get("libId") == null))
                    .count();
            m.put("managedCount", bound);
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> buildInventory(List<RcBaseLibrary> base,
                                               List<RcBaseLibrary> semi,
                                               List<RcBaseLibrary> unstruct,
                                               List<Map<String, Object>> managed) {
        Map<String, Object> byType = new LinkedHashMap<>();
        byType.put("BASE", Map.of("libraryCount", base.size(),
                "recordCount", base.stream().mapToLong(l -> l.getRecordCount() == null ? 0 : l.getRecordCount()).sum()));
        byType.put("SEMI", Map.of("libraryCount", semi.size(),
                "recordCount", semi.stream().mapToLong(l -> l.getRecordCount() == null ? 0 : l.getRecordCount()).sum()));
        byType.put("UNSTRUCT", Map.of("libraryCount", unstruct.size(),
                "recordCount", unstruct.stream().mapToLong(l -> l.getRecordCount() == null ? 0 : l.getRecordCount()).sum()));

        Map<String, Long> byAssetType = new LinkedHashMap<>();
        byAssetType.put("BASE", 0L);
        byAssetType.put("SEMI", 0L);
        byAssetType.put("UNSTRUCT", 0L);
        long managedRows = 0;
        for (Map<String, Object> t : managed) {
            String at = String.valueOf(t.getOrDefault("assetType", "BASE")).toUpperCase(Locale.ROOT);
            byAssetType.merge(at, 1L, Long::sum);
            Object rc = t.get("recordCount");
            if (rc instanceof Number) managedRows += ((Number) rc).longValue();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byLibraryType", byType);
        out.put("byAssetTypeTables", byAssetType);
        out.put("managedTableCount", managed.size());
        out.put("managedRecordCount", managedRows);
        out.put("moduleCount", ASSET_MODULES.size());
        out.put("angle", List.of("库类型", "资产类型（表）", "数据中心模块", "文件目录/索引"));
        return out;
    }

    private static int intVal(Object o, int def) {
        if (o == null) return def;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path);
             DigestInputStream din = new DigestInputStream(in, md)) {
            din.transferTo(OutputStream.nullOutputStream());
        }
        byte[] dig = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : dig) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String requireIdent(String value, String field) {
        if (value == null || !IDENT.matcher(value).matches()) {
            throw new BusinessException(400, field + " 非法: " + value);
        }
        return value;
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) throw new BusinessException(400, field + " required");
        return v;
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
