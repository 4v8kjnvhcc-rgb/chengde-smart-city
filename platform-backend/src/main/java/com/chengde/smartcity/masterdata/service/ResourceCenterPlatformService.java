package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.RcAssetCatalogEntry;
import com.chengde.smartcity.masterdata.entity.RcBackupArtifact;
import com.chengde.smartcity.masterdata.entity.RcBackupJob;
import com.chengde.smartcity.masterdata.entity.RcBaseLibrary;
import com.chengde.smartcity.masterdata.entity.RcManagedTable;
import com.chengde.smartcity.masterdata.entity.RcMonitorMetric;
import com.chengde.smartcity.masterdata.entity.RcPartitionDef;
import com.chengde.smartcity.masterdata.entity.RcStoragePolicy;
import com.chengde.smartcity.masterdata.entity.RcThemeLibrary;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.RcAssetCatalogEntryMapper;
import com.chengde.smartcity.masterdata.mapper.RcBackupArtifactMapper;
import com.chengde.smartcity.masterdata.mapper.RcBackupJobMapper;
import com.chengde.smartcity.masterdata.mapper.RcBaseLibraryMapper;
import com.chengde.smartcity.masterdata.mapper.RcManagedTableMapper;
import com.chengde.smartcity.masterdata.mapper.RcMonitorMetricMapper;
import com.chengde.smartcity.masterdata.mapper.RcPartitionDefMapper;
import com.chengde.smartcity.masterdata.mapper.RcStoragePolicyMapper;
import com.chengde.smartcity.masterdata.mapper.RcThemeLibraryMapper;
import com.chengde.smartcity.security.UserPrincipal;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceCenterPlatformService {

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Path BACKUP_ROOT = Path.of("data", "nas-demo", "backups");

    private final RcBaseLibraryMapper libraryMapper;
    private final RcPartitionDefMapper partitionMapper;
    private final RcStoragePolicyMapper policyMapper;
    private final RcAssetCatalogEntryMapper catalogMapper;
    private final RcMonitorMetricMapper monitorMapper;
    private final RcThemeLibraryMapper themeMapper;
    private final RcManagedTableMapper managedTableMapper;
    private final RcBackupJobMapper backupJobMapper;
    private final RcBackupArtifactMapper backupArtifactMapper;
    private final GovMetadataRegistryMapper registryMapper;
    private final GovCatalogResourceMapper catalogResourceMapper;
    private final AuditService auditService;
    private final DataSource platformDataSource;

    public ResourceCenterPlatformService(RcBaseLibraryMapper libraryMapper,
                                         RcPartitionDefMapper partitionMapper,
                                         RcStoragePolicyMapper policyMapper,
                                         RcAssetCatalogEntryMapper catalogMapper,
                                         RcMonitorMetricMapper monitorMapper,
                                         RcThemeLibraryMapper themeMapper,
                                         RcManagedTableMapper managedTableMapper,
                                         RcBackupJobMapper backupJobMapper,
                                         RcBackupArtifactMapper backupArtifactMapper,
                                         GovMetadataRegistryMapper registryMapper,
                                         GovCatalogResourceMapper catalogResourceMapper,
                                         AuditService auditService,
                                         DataSource platformDataSource) {
        this.libraryMapper = libraryMapper;
        this.partitionMapper = partitionMapper;
        this.policyMapper = policyMapper;
        this.catalogMapper = catalogMapper;
        this.monitorMapper = monitorMapper;
        this.themeMapper = themeMapper;
        this.managedTableMapper = managedTableMapper;
        this.backupJobMapper = backupJobMapper;
        this.backupArtifactMapper = backupArtifactMapper;
        this.registryMapper = registryMapper;
        this.catalogResourceMapper = catalogResourceMapper;
        this.auditService = auditService;
        this.platformDataSource = platformDataSource;
    }

    public Map<String, Object> libraryOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseLibraries", libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>().eq(RcBaseLibrary::getLibType, "BASE")));
        out.put("semiLibraries", libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>().eq(RcBaseLibrary::getLibType, "SEMI")));
        out.put("unstructLibraries", libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>().eq(RcBaseLibrary::getLibType, "UNSTRUCT")));
        out.put("themes", listThemes(null));
        out.put("managedTables", listManagedTables(null));
        return out;
    }

    public List<RcBaseLibrary> listLibraries(String libType) {
        LambdaQueryWrapper<RcBaseLibrary> q = new LambdaQueryWrapper<RcBaseLibrary>().orderByAsc(RcBaseLibrary::getId);
        if (libType != null && !libType.isBlank()) q.eq(RcBaseLibrary::getLibType, libType);
        return libraryMapper.selectList(q);
    }

    @Transactional
    public Long createLibrary(UserPrincipal operator, Map<String, Object> body) {
        RcBaseLibrary lib = new RcBaseLibrary();
        lib.setLibCode(str(body.get("libCode"), "LIB_" + System.currentTimeMillis()));
        lib.setLibName(required(body.get("libName"), "libName").toString());
        lib.setLibType(str(body.get("libType"), "BASE"));
        lib.setRecordCount(0);
        lib.setStatus("ACTIVE");
        libraryMapper.insert(lib);
        return lib.getId();
    }

    public List<Map<String, Object>> listThemes(String libraryKind) {
        LambdaQueryWrapper<RcThemeLibrary> q = new LambdaQueryWrapper<RcThemeLibrary>().orderByAsc(RcThemeLibrary::getId);
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
        out.put("partitions", partitionMapper.selectList(new LambdaQueryWrapper<RcPartitionDef>().orderByAsc(RcPartitionDef::getId)));
        out.put("policies", policyMapper.selectList(new LambdaQueryWrapper<RcStoragePolicy>().orderByAsc(RcStoragePolicy::getId)));
        out.put("backups", backupJobMapper.selectList(new LambdaQueryWrapper<RcBackupJob>().orderByDesc(RcBackupJob::getId)));
        out.put("artifacts", backupArtifactMapper.selectList(new LambdaQueryWrapper<RcBackupArtifact>()
                .orderByDesc(RcBackupArtifact::getId).last("LIMIT 50")));
        return out;
    }

    @Transactional
    public Long createPartition(UserPrincipal operator, Map<String, Object> body) {
        String tableName = str(body.get("tableName"), null);
        if (tableName == null || tableName.isBlank()) {
            throw new BusinessException(400, "请选择已纳管的目标表");
        }
        requireIdent(tableName, "tableName");
        long managed = managedTableMapper.selectCount(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getPhysicalTable, tableName)
                .eq(RcManagedTable::getStatus, "ACTIVE"));
        if (managed == 0) {
            throw new BusinessException(400, "目标表须为已纳管物理表：" + tableName);
        }
        RcPartitionDef p = new RcPartitionDef();
        p.setPartitionCode(str(body.get("partitionCode"), "PART_" + System.currentTimeMillis()));
        p.setPartitionName(required(body.get("partitionName"), "partitionName").toString());
        p.setPartitionType(str(body.get("partitionType"), "RANGE").toUpperCase(Locale.ROOT));
        p.setThemeId(longVal(body.get("themeId")));
        p.setTableName(tableName);
        p.setPartitionColumn(str(body.get("partitionColumn"), null));
        p.setExpressionText(str(body.get("expressionText"), null));
        p.setPretestStatus("DRAFT");
        p.setStatus("ACTIVE");
        partitionMapper.insert(p);
        return p.getId();
    }

    @Transactional
    public Map<String, Object> pretestPartition(UserPrincipal operator, Long id) {
        RcPartitionDef p = partitionMapper.selectById(id);
        if (p == null) throw new BusinessException(404, "分区策略不存在");
        String table = p.getTableName();
        String column = p.getPartitionColumn();
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
                risks.add("表已分区，重复执行 ALTER 可能失败");
            }
        }
        String ddl = "ALTER TABLE `" + (table == null ? "?" : table) + "` "
                + "PARTITION BY " + p.getPartitionType() + " (`"
                + (column == null ? "?" : column) + "`) "
                + "/* expression: " + (p.getExpressionText() == null ? "" : p.getExpressionText())
                + " */ -- DRY-RUN ONLY, NOT EXECUTED";
        p.setPreviewDdl(ddl);
        p.setPretestStatus(status);
        p.setPretestMessage(String.join("; ", risks.isEmpty() ? List.of("预检通过，未执行物理DDL") : risks));
        p.setPretestAt(LocalDateTime.now());
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
    public Long createPolicy(UserPrincipal operator, Map<String, Object> body) {
        String action = str(body.get("actionType"), "BACKUP").toUpperCase(Locale.ROOT);
        if (!Set.of("BACKUP", "ARCHIVE", "DESTROY").contains(action)) {
            throw new BusinessException(400, "actionType 须为 BACKUP / ARCHIVE / DESTROY");
        }
        Long managedId = longVal(body.get("managedTableId"));
        if (("BACKUP".equals(action) || "ARCHIVE".equals(action) || "DESTROY".equals(action))
                && managedId != null) {
            RcManagedTable mt = managedTableMapper.selectById(managedId);
            if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
                throw new BusinessException(400, "纳管表不存在或已解绑");
            }
        }
        RcStoragePolicy p = new RcStoragePolicy();
        p.setPolicyCode(str(body.get("policyCode"), "POL_" + System.currentTimeMillis()));
        p.setPolicyName(required(body.get("policyName"), "policyName").toString());
        p.setActionType(action);
        p.setRetentionDays(body.get("retentionDays") == null ? 30
                : Integer.valueOf(String.valueOf(body.get("retentionDays"))));
        p.setThemeId(longVal(body.get("themeId")));
        p.setManagedTableId(managedId);
        p.setStatus("ACTIVE");
        policyMapper.insert(p);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_POLICY_CREATE", "rc_storage_policy", String.valueOf(p.getId()), action);
        return p.getId();
    }

    @Transactional
    public Map<String, Object> executePolicy(UserPrincipal operator, Long policyId) {
        RcStoragePolicy p = policyMapper.selectById(policyId);
        if (p == null) throw new BusinessException(404, "策略不存在");
        String action = p.getActionType() == null ? "" : p.getActionType().toUpperCase(Locale.ROOT);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyId", policyId);
        out.put("actionType", action);
        if ("BACKUP".equals(action)) {
            Long managedId = p.getManagedTableId();
            if (managedId == null) throw new BusinessException(400, "备份策略未绑定纳管表");
            Map<String, Object> backup = runLogicalBackup(operator, managedId, p.getRetentionDays());
            out.putAll(backup);
            out.put("status", "SUCCESS");
        } else if ("ARCHIVE".equals(action)) {
            out.put("status", "LEDGER");
            out.put("message", "归档状态已记录（台账），未移动物理数据");
        } else if ("DESTROY".equals(action)) {
            throw new BusinessException(403, "销毁策略禁止自动执行物理删除");
        } else {
            throw new BusinessException(400, "不支持的策略动作: " + action);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_POLICY_RUN", "rc_storage_policy", String.valueOf(policyId), action);
        out.put("retentionDays", p.getRetentionDays());
        return out;
    }

    @Transactional
    public Map<String, Object> runLogicalBackup(UserPrincipal operator, Long managedTableId, Integer retentionDays) {
        RcManagedTable mt = managedTableMapper.selectById(managedTableId);
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(404, "纳管表不存在");
        }
        String table = requireIdent(mt.getPhysicalTable(), "physicalTable");
        RcBackupJob job = new RcBackupJob();
        job.setJobName("BACKUP_" + table + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        job.setThemeId(mt.getThemeId());
        job.setStatus("RUNNING");
        job.setCreatedBy(operator.getUsername());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        backupJobMapper.insert(job);

        try {
            Files.createDirectories(BACKUP_ROOT);
            String fileName = table + "_" + System.currentTimeMillis() + ".cdbak";
            Path tmp = BACKUP_ROOT.resolve(fileName + ".tmp");
            Path finalPath = BACKUP_ROOT.resolve(fileName);
            long rows = 0;
            try (Connection conn = platformDataSource.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "`");
                 BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write("# Chengde logical backup\n");
                writer.write("# table=" + table + "\n");
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

            RcBackupArtifact art = new RcBackupArtifact();
            art.setJobId(job.getId());
            art.setManagedTableId(mt.getId());
            art.setPhysicalTable(table);
            art.setFilePath(finalPath.toAbsolutePath().toString());
            art.setFileName(fileName);
            art.setRowCount(rows);
            art.setByteSize(size);
            art.setSha256(sha);
            art.setStatus("SUCCESS");
            art.setMessage("logical backup ok");
            art.setCreatedBy(operator.getUsername());
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
        return backupArtifactMapper.selectList(q.last("LIMIT 100"));
    }

    @Transactional
    public Map<String, Object> refreshMonitor(UserPrincipal operator) {
        List<RcManagedTable> tables = managedTableMapper.selectList(new LambdaQueryWrapper<RcManagedTable>()
                .eq(RcManagedTable::getStatus, "ACTIVE"));
        long totalRows = 0;
        long totalBytes = 0;
        for (RcManagedTable mt : tables) {
            refreshTableStats(mt);
            totalRows += mt.getRecordCount() == null ? 0 : mt.getRecordCount();
            totalBytes += (mt.getDataBytes() == null ? 0 : mt.getDataBytes())
                    + (mt.getIndexBytes() == null ? 0 : mt.getIndexBytes());
        }
        upsertMetric("managed_tables", "纳管表数量", String.valueOf(tables.size()), "OK");
        upsertMetric("total_rows", "纳管表总行数", String.valueOf(totalRows), totalRows > 0 ? "OK" : "WARN");
        upsertMetric("total_bytes", "纳管表存储字节", String.valueOf(totalBytes), "OK");
        upsertMetric("db_ping", "库连通性", pingDb() ? "UP" : "DOWN", pingDb() ? "OK" : "CRITICAL");
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_MONITOR_REFRESH", "rc_monitor_metric", "all", "tables=" + tables.size());
        return Map.of("managedTables", tables.size(), "totalRows", totalRows, "totalBytes", totalBytes,
                "metrics", monitorMetrics());
    }

    public List<RcAssetCatalogEntry> listCatalogEntries() {
        return catalogMapper.selectList(new LambdaQueryWrapper<RcAssetCatalogEntry>().orderByDesc(RcAssetCatalogEntry::getId));
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
        if (managedTableId != null) {
            RcManagedTable mt = managedTableMapper.selectById(managedTableId);
            if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
                throw new BusinessException(400, "纳管表不存在或已解绑");
            }
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
        RcAssetCatalogEntry e = new RcAssetCatalogEntry();
        e.setEntryCode(str(body.get("entryCode"), metaEntryCode != null ? metaEntryCode : ("ACE_" + System.currentTimeMillis())));
        e.setEntryName(required(entryName, "entryName").toString());
        if (libId != null) e.setLibId(libId);
        e.setDriveTask(driveTask);
        e.setStatus("ACTIVE");
        catalogMapper.insert(e);
        return e.getId();
    }

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
        // 元数据登记条目（供「数据搜索与元数据检索」）
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

    public Map<String, Object> queryManagedTable(Long managedTableId, Integer limit) {
        if (managedTableId == null) throw new BusinessException(400, "managedTableId required");
        RcManagedTable mt = managedTableMapper.selectById(managedTableId);
        if (mt == null || !"ACTIVE".equalsIgnoreCase(mt.getStatus())) {
            throw new BusinessException(404, "纳管表不存在或已解绑");
        }
        String table = requireIdent(mt.getPhysicalTable(), "physicalTable");
        int lim = limit == null ? 100 : Math.min(Math.max(limit, 1), 500);
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = platformDataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "` LIMIT " + lim)) {
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
        out.put("columns", columns);
        out.put("rows", rows);
        out.put("rowCount", rows.size());
        return out;
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

    private void upsertMetric(String key, String label, String value, String level) {
        RcMonitorMetric m = monitorMapper.selectOne(new LambdaQueryWrapper<RcMonitorMetric>()
                .eq(RcMonitorMetric::getMetricKey, key).last("LIMIT 1"));
        if (m == null) {
            m = new RcMonitorMetric();
            m.setMetricKey(key);
            m.setMetricLabel(label);
            m.setMetricValue(value);
            m.setAlertLevel(level);
            m.setCheckedAt(LocalDateTime.now());
            monitorMapper.insert(m);
        } else {
            m.setMetricLabel(label);
            m.setMetricValue(value);
            m.setAlertLevel(level);
            m.setCheckedAt(LocalDateTime.now());
            monitorMapper.updateById(m);
        }
    }

    private boolean pingDb() {
        try (Connection c = platformDataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            return false;
        }
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
        }
        return m;
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
