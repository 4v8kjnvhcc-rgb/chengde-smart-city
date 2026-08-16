package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.RcBackupArtifact;
import com.chengde.smartcity.masterdata.entity.RcManagedTable;
import com.chengde.smartcity.masterdata.entity.RcPolicyRunLog;
import com.chengde.smartcity.masterdata.entity.RcStoragePolicy;
import com.chengde.smartcity.masterdata.mapper.RcBackupArtifactMapper;
import com.chengde.smartcity.masterdata.mapper.RcManagedTableMapper;
import com.chengde.smartcity.masterdata.mapper.RcPolicyRunLogMapper;
import com.chengde.smartcity.masterdata.mapper.RcStoragePolicyMapper;
import com.chengde.smartcity.masterdata.support.DataLayerSupport;
import com.chengde.smartcity.masterdata.support.LayerJdbcSupport;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 多表按日快照：备份库生成 {表}{yyyyMMdd}（跑到哪天打哪天），源表不改；归档同名 tsv.gz；满 6 个月才销毁。
 * 分区备份/分区销毁仅 LEDGER。
 */
@Service
public class StorageLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(StorageLifecycleService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern DAY_SUFFIX = Pattern.compile("^(\\d{8})$");
    private static final Pattern LEGACY_MONTH_SUFFIX = Pattern.compile("^(\\d{6})$");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int RETAIN_MONTHS = 6;

    private final LayerJdbcSupport layerJdbc;
    private final RcStoragePolicyMapper policyMapper;
    private final RcManagedTableMapper managedTableMapper;
    private final RcBackupArtifactMapper artifactMapper;
    private final RcPolicyRunLogMapper runLogMapper;
    private final AuditService auditService;
    private final Path archiveDir;
    private final String archiveHostDir;
    private final ConcurrentHashMap<String, Object> tableLocks = new ConcurrentHashMap<>();

    public StorageLifecycleService(LayerJdbcSupport layerJdbc,
                                   RcStoragePolicyMapper policyMapper,
                                   RcManagedTableMapper managedTableMapper,
                                   RcBackupArtifactMapper artifactMapper,
                                   RcPolicyRunLogMapper runLogMapper,
                                   AuditService auditService,
                                   @Value("${app.lifecycle.archive-dir:data/archives}") String archiveDir,
                                   @Value("${app.lifecycle.archive-host-dir:}") String archiveHostDir) {
        this.layerJdbc = layerJdbc;
        this.policyMapper = policyMapper;
        this.managedTableMapper = managedTableMapper;
        this.artifactMapper = artifactMapper;
        this.runLogMapper = runLogMapper;
        this.auditService = auditService;
        this.archiveDir = Path.of(archiveDir);
        this.archiveHostDir = archiveHostDir == null ? "" : archiveHostDir.trim();
    }

    public List<Map<String, String>> listSourceDatabases() {
        List<Map<String, String>> out = new ArrayList<>();
        for (String db : DataLayerSupport.platformSourceDatabases()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("database", db);
            row.put("backupDatabase", DataLayerSupport.backupDatabaseFor(db));
            row.put("layer", DataLayerSupport.layerForDatabase(db));
            out.add(row);
        }
        return out;
    }

    public List<String> listTables(String database) {
        String db = requireSourceDb(database);
        return layerJdbc.listBaseTables(db);
    }

    public Map<String, Object> execute(UserPrincipal operator, Long policyId) {
        RcStoragePolicy p = policyMapper.selectById(policyId);
        if (p == null) {
            throw new BusinessException(404, "策略不存在");
        }
        Map<String, Object> rule = parseRule(p);
        String sourceDb = str(rule.get("sourceDb"), inferSourceDb(p));
        List<String> tables = resolveTableNames(rule);
        if (tables.isEmpty()) {
            String inferred = inferTable(p);
            if (inferred != null && !inferred.isBlank()) {
                tables = List.of(inferred);
            }
        }
        if (sourceDb == null || tables.isEmpty()) {
            throw new BusinessException(400, "策略未指定源库和表");
        }
        sourceDb = requireSourceDb(sourceDb);
        List<String> idents = new ArrayList<>();
        for (String t : tables) {
            idents.add(requireIdent(t, "tableName"));
        }
        Object lock = tableLocks.computeIfAbsent(sourceDb, k -> new Object());
        synchronized (lock) {
            return doExecute(operator, p, rule, sourceDb, idents);
        }
    }

    private Map<String, Object> doExecute(UserPrincipal operator, RcStoragePolicy p,
                                          Map<String, Object> rule, String sourceDb, List<String> tables) {
        String action = p.getActionType() == null ? "" : p.getActionType().toUpperCase(Locale.ROOT);
        String actor = operator != null ? operator.getUsername() : "scheduler";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyId", p.getId());
        out.put("actionType", action);
        try {
            Map<String, Object> result;
            if (isPartitionScope(rule) && ("BACKUP".equals(action) || "DESTROY".equals(action))) {
                result = runPartitionLedger(p, action, sourceDb, tables, actor);
            } else if ("BACKUP".equals(action)) {
                result = runBackup(p, sourceDb, tables, actor);
            } else if ("ARCHIVE".equals(action)) {
                result = runArchive(p, sourceDb, tables, actor);
            } else if ("DESTROY".equals(action)) {
                result = runDestroy(p, sourceDb, tables, actor);
            } else {
                throw new BusinessException(400, "不支持的策略动作: " + action);
            }
            out.putAll(result);
            String status = str(result.get("status"), "SUCCESS");
            String message = str(result.get("message"), "完成");
            Long artifactId = result.get("artifactId") == null ? null
                    : Long.valueOf(String.valueOf(result.get("artifactId")));
            Long rowCount = result.get("rowCount") == null ? null
                    : Long.valueOf(String.valueOf(result.get("rowCount")));
            String location = str(result.get("storageLocation"), null);
            recordRun(p, status, rowCount, artifactId, location, message, actor);
            markPolicy(p, status, message);
            auditService.log(operator != null ? operator.getUserId() : null, actor,
                    operator != null ? operator.getOrgId() : null,
                    "RC_POLICY_RUN", "rc_storage_policy", String.valueOf(p.getId()), action);
            return out;
        } catch (BusinessException ex) {
            recordRun(p, "FAILED", null, null, null, ex.getMessage(), actor);
            markPolicy(p, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private Map<String, Object> runPartitionLedger(RcStoragePolicy p, String action,
                                                   String sourceDb, List<String> tables, String actor) {
        String tablesLabel = String.join(",", tables);
        String msg;
        if ("BACKUP".equals(action)) {
            msg = "分区备份仅登记台账，未导出分区、未写入备份库；表=" + tablesLabel;
        } else {
            msg = "分区销毁仅登记台账，未 DROP 分区、未删源表；表=" + tablesLabel;
        }
        RcBackupArtifact art = new RcBackupArtifact();
        art.setArtifactType(action);
        art.setJobId(0L);
        art.setManagedTableId(p.getManagedTableId());
        art.setPhysicalTable(tables.get(0));
        art.setFilePath("");
        art.setStorageLocation("LEDGER");
        art.setFileName("partition-" + action.toLowerCase(Locale.ROOT) + "-" + dayStamp());
        art.setRowCount(0L);
        art.setByteSize(0L);
        art.setStatus("LEDGER");
        art.setMessage(msg);
        art.setCreatedBy(actor);
        art.setCreatedAt(LocalDateTime.now());
        artifactMapper.insert(art);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "LEDGER");
        out.put("artifactId", art.getId());
        out.put("rowCount", 0);
        out.put("storageLocation", "LEDGER");
        out.put("message", msg);
        out.put("sourceDb", sourceDb);
        return out;
    }

    private Map<String, Object> runBackup(RcStoragePolicy p, String sourceDb, List<String> tables, String actor) {
        String bakDb = DataLayerSupport.backupDatabaseFor(sourceDb);
        String stamp = dayStamp();
        long copiedTotal = 0;
        Long lastArtId = null;
        String lastLoc = null;
        List<String> done = new ArrayList<>();
        for (String table : tables) {
            if (!layerJdbc.tableExists(sourceDb, table)) {
                throw new BusinessException(404, "源表不存在: " + DataLayerSupport.qualify(sourceDb, table));
            }
            String bakTable = snapshotName(table, stamp);
            requireIdent(bakTable, "backupTable");
            try {
                if (layerJdbc.tableExists(bakDb, bakTable)) {
                    try (Connection conn = layerJdbc.open(bakDb); Statement st = conn.createStatement()) {
                        st.execute("DROP TABLE IF EXISTS " + DataLayerSupport.qualify(bakDb, bakTable));
                    }
                }
                layerJdbc.createTableLike(sourceDb, table, bakDb, bakTable);
            } catch (Exception e) {
                throw new BusinessException(500, "准备备份快照表失败（请确认已在源库所在实例创建 "
                        + bakDb + "）: " + e.getMessage());
            }
            long copied;
            String insertSql = "INSERT INTO " + DataLayerSupport.qualify(bakDb, bakTable)
                    + " SELECT * FROM " + DataLayerSupport.qualify(sourceDb, table);
            try (Connection conn = layerJdbc.open(bakDb); Statement st = conn.createStatement()) {
                st.setQueryTimeout(0);
                copied = st.executeUpdate(insertSql);
            } catch (Exception e) {
                throw new BusinessException(500, "写入备份快照表失败 " + bakDb + "." + bakTable + " — " + e.getMessage());
            }
            copiedTotal += copied;
            String location = bakDb + "." + bakTable;
            RcBackupArtifact art = new RcBackupArtifact();
            art.setArtifactType("BACKUP");
            art.setJobId(0L);
            art.setManagedTableId(p.getManagedTableId());
            art.setPhysicalTable(table);
            art.setFilePath(location);
            art.setStorageLocation(location);
            art.setFileName(bakTable);
            art.setRowCount(copied);
            art.setByteSize(0L);
            art.setStatus("SUCCESS");
            art.setMessage("已生成日快照 " + location + " 共 " + copied + " 行（源表未改）");
            art.setCreatedBy(actor);
            art.setCreatedAt(LocalDateTime.now());
            artifactMapper.insert(art);
            lastArtId = art.getId();
            lastLoc = location;
            done.add(bakTable + "(" + copied + ")");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "SUCCESS");
        out.put("artifactId", lastArtId);
        out.put("rowCount", copiedTotal);
        out.put("backupDatabase", bakDb);
        out.put("storageLocation", lastLoc);
        out.put("message", "已备份 " + done.size() + " 张表至 " + bakDb + "：" + String.join("、", done));
        return out;
    }

    private Map<String, Object> runArchive(RcStoragePolicy p, String sourceDb, List<String> tables, String actor) {
        String bakDb = DataLayerSupport.backupDatabaseFor(sourceDb);
        String stamp = dayStamp();
        boolean compress = p.getCompressEnabled() == null || p.getCompressEnabled() == 1;
        long rowsTotal = 0;
        Long lastArtId = null;
        String lastHost = null;
        List<String> done = new ArrayList<>();
        Path dir = archiveDir.resolve(sourceDb);
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new BusinessException(500, "无法创建归档目录 " + dir.toAbsolutePath() + " : " + e.getMessage());
        }
        for (String table : tables) {
            String bakTable = snapshotName(table, stamp);
            if (!layerJdbc.tableExists(bakDb, bakTable)) {
                throw new BusinessException(404, "备份库尚无当天快照表，请先跑备份策略: "
                        + DataLayerSupport.qualify(bakDb, bakTable));
            }
            String fileName = bakTable + (compress ? ".tsv.gz" : ".tsv");
            Path file = dir.resolve(fileName);
            long rows = 0;
            try (Connection conn = layerJdbc.open(bakDb);
                 Statement st = conn.createStatement()) {
                st.setFetchSize(Integer.MIN_VALUE);
                String sql = "SELECT * FROM " + DataLayerSupport.qualify(bakDb, bakTable);
                try (ResultSet rs = st.executeQuery(sql);
                     var fos = Files.newOutputStream(file);
                     GZIPOutputStream gzip = compress ? new GZIPOutputStream(fos) : null;
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                             gzip != null ? gzip : fos, StandardCharsets.UTF_8))) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    List<String> headers = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) {
                        headers.add(meta.getColumnLabel(i));
                    }
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
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(500, "归档导出失败: " + e.getMessage());
            }
            rowsTotal += rows;
            String containerPath = file.toAbsolutePath().toString();
            String hostPath = hostPathFor(sourceDb, fileName);
            RcBackupArtifact art = new RcBackupArtifact();
            art.setArtifactType("ARCHIVE");
            art.setJobId(0L);
            art.setManagedTableId(p.getManagedTableId());
            art.setPhysicalTable(table);
            art.setFilePath(containerPath);
            art.setStorageLocation(hostPath);
            art.setFileName(fileName);
            art.setRowCount(rows);
            try {
                art.setByteSize(Files.size(file));
                art.setSha256(sha256(file));
            } catch (Exception e) {
                art.setByteSize(0L);
            }
            art.setStatus("SUCCESS");
            art.setMessage("已归档 " + rows + " 行至 " + hostPath);
            art.setCreatedBy(actor);
            art.setCreatedAt(LocalDateTime.now());
            artifactMapper.insert(art);
            lastArtId = art.getId();
            lastHost = hostPath;
            done.add(fileName);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "SUCCESS");
        out.put("artifactId", lastArtId);
        out.put("rowCount", rowsTotal);
        out.put("storageLocation", lastHost);
        out.put("message", "已归档 " + done.size() + " 个文件：" + String.join("、", done));
        return out;
    }

    private Map<String, Object> runDestroy(RcStoragePolicy p, String sourceDb, List<String> tables, String actor) {
        String bakDb = DataLayerSupport.backupDatabaseFor(sourceDb);
        int cutoff = cutoffDay();
        int droppedTables = 0;
        int deletedFiles = 0;
        long bakRowsHint = 0;
        for (String table : tables) {
            for (String bakTable : listSnapshotTables(bakDb, table)) {
                Integer stamp = parseStampSuffix(table, bakTable);
                if (stamp == null || stamp > cutoff) {
                    continue;
                }
                try (Connection conn = layerJdbc.open(bakDb); Statement st = conn.createStatement()) {
                    st.execute("DROP TABLE IF EXISTS " + DataLayerSupport.qualify(bakDb, bakTable));
                    droppedTables++;
                } catch (Exception e) {
                    throw new BusinessException(500, "删除备份快照表失败 " + bakDb + "." + bakTable + " — " + e.getMessage());
                }
            }
            deletedFiles += deleteArchiveFiles(sourceDb, table, cutoff);
            List<RcBackupArtifact> arts = artifactMapper.selectList(new LambdaQueryWrapper<RcBackupArtifact>()
                    .eq(RcBackupArtifact::getPhysicalTable, table)
                    .ne(RcBackupArtifact::getStatus, "DESTROYED"));
            for (RcBackupArtifact art : arts) {
                Integer stamp = stampFromFileName(table, art.getFileName());
                if (stamp == null) {
                    stamp = stampFromLocation(table, art.getStorageLocation());
                }
                if (stamp == null || stamp > cutoff) {
                    continue;
                }
                try {
                    if (art.getFilePath() != null && !art.getFilePath().isBlank()) {
                        Path fp = Path.of(art.getFilePath());
                        if (Files.isRegularFile(fp)) {
                            Files.deleteIfExists(fp);
                        }
                    }
                } catch (Exception e) {
                    log.warn("delete archive file {}: {}", art.getFilePath(), e.getMessage());
                }
                art.setStatus("DESTROYED");
                art.setMessage("已按满 " + RETAIN_MONTHS + " 个月规则销毁；原路径=" + art.getStorageLocation());
                artifactMapper.updateById(art);
                bakRowsHint++;
            }
        }
        String msg = "已销毁备份快照表 " + droppedTables + " 张、归档文件 " + deletedFiles
                + " 个（门槛：yyyyMMdd≤" + cutoff + "）；源业务表未改动";
        if (droppedTables == 0 && deletedFiles == 0) {
            msg = "没有满 " + RETAIN_MONTHS + " 个月可销毁的快照表或归档文件（门槛 yyyyMMdd≤" + cutoff + "）；源表未改动";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "SUCCESS");
        out.put("rowCount", bakRowsHint);
        out.put("storageLocation", bakDb);
        out.put("message", msg);
        out.put("policyIdHint", p.getId());
        out.put("actor", actor);
        return out;
    }

    public Map<String, Object> restore(UserPrincipal operator, Long artifactId) {
        RcBackupArtifact art = artifactMapper.selectById(artifactId);
        if (art == null) {
            throw new BusinessException(404, "产物不存在");
        }
        String type = art.getArtifactType() == null ? "BACKUP" : art.getArtifactType().toUpperCase(Locale.ROOT);
        if ("DESTROYED".equalsIgnoreCase(art.getStatus()) || "LEDGER".equalsIgnoreCase(art.getStatus())) {
            throw new BusinessException(400, "该产物不可恢复");
        }
        if ("ARCHIVE".equals(type)) {
            throw new BusinessException(400, "归档文件请由运维按宿主机路径拷回；页面恢复仅支持备份快照表");
        }
        String loc = art.getStorageLocation() == null ? art.getFilePath() : art.getStorageLocation();
        if (loc == null || !loc.contains(".")) {
            throw new BusinessException(400, "备份产物位置无法解析，无法恢复");
        }
        int hash = loc.lastIndexOf('#');
        if (hash > 0) {
            loc = loc.substring(0, hash);
        }
        int dot = loc.lastIndexOf('.');
        String bakDb = loc.substring(0, dot);
        String bakTable = requireIdent(loc.substring(dot + 1), "table");
        String restoreTable = "rc_restore_" + artifactId;
        try {
            layerJdbc.createTableLike(bakDb, bakTable, bakDb, restoreTable);
            try (Connection conn = layerJdbc.open(bakDb); Statement st = conn.createStatement()) {
                st.execute("TRUNCATE TABLE " + DataLayerSupport.qualify(bakDb, restoreTable));
                int n = st.executeUpdate("INSERT INTO " + DataLayerSupport.qualify(bakDb, restoreTable)
                        + " SELECT * FROM " + DataLayerSupport.qualify(bakDb, bakTable));
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("artifactId", artifactId);
                out.put("restoreTable", bakDb + "." + restoreTable);
                out.put("rowCount", n);
                out.put("status", "SUCCESS");
                out.put("message", "已恢复至独立表 " + bakDb + "." + restoreTable + "（不覆盖源表）");
                auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                        "RC_BACKUP_RESTORE", "rc_backup_artifact", String.valueOf(artifactId),
                        String.valueOf(out.get("message")));
                return out;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "恢复失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseRule(RcStoragePolicy p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("backupScope", "TABLE");
        String raw = p.getTableRule();
        if (raw != null && raw.trim().startsWith("{")) {
            try {
                Map<String, Object> parsed = JSON.readValue(raw, Map.class);
                if (parsed != null) {
                    out.putAll(parsed);
                }
            } catch (Exception ignored) {
                out.put("note", raw);
            }
        }
        return out;
    }

    public static List<String> resolveTableNames(Map<String, Object> rule) {
        List<String> out = new ArrayList<>();
        if (rule == null) {
            return out;
        }
        Object raw = rule.get("tableNames");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o).trim());
                }
            }
        }
        if (out.isEmpty()) {
            String one = str(rule.get("tableName"), null);
            if (one != null) {
                out.add(one);
            }
        }
        return out;
    }

    private String inferSourceDb(RcStoragePolicy p) {
        if (p.getManagedTableId() == null) return null;
        RcManagedTable mt = managedTableMapper.selectById(p.getManagedTableId());
        if (mt == null || mt.getPhysicalTable() == null) return null;
        return DataLayerSupport.databaseForLayer(DataLayerSupport.layerForTableName(mt.getPhysicalTable()));
    }

    private String inferTable(RcStoragePolicy p) {
        if (p.getManagedTableId() == null) return null;
        RcManagedTable mt = managedTableMapper.selectById(p.getManagedTableId());
        return mt == null ? null : mt.getPhysicalTable();
    }

    private String hostPathFor(String sourceDb, String fileName) {
        String rel = sourceDb + "/" + fileName;
        if (!archiveHostDir.isBlank()) {
            String base = archiveHostDir.replace('\\', '/');
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base + "/" + rel;
        }
        return archiveDir.resolve(rel).toAbsolutePath().toString();
    }

    private void recordRun(RcStoragePolicy policy, String runStatus, Long rowCount,
                           Long artifactId, String storageLocation, String message, String actor) {
        RcPolicyRunLog logRow = new RcPolicyRunLog();
        logRow.setPolicyId(policy.getId());
        logRow.setActionType(policy.getActionType());
        logRow.setRunStatus(runStatus);
        logRow.setRowCount(rowCount);
        logRow.setArtifactId(artifactId);
        logRow.setStorageLocation(storageLocation);
        logRow.setMessage(message == null ? null : (message.length() > 500 ? message.substring(0, 500) : message));
        logRow.setCreatedBy(actor);
        logRow.setCreatedAt(LocalDateTime.now());
        runLogMapper.insert(logRow);
    }

    private void markPolicy(RcStoragePolicy policy, String runStatus, String message) {
        policy.setLastRunAt(LocalDateTime.now());
        policy.setLastRunStatus(runStatus);
        policy.setLastRunMessage(message == null ? null
                : (message.length() > 500 ? message.substring(0, 500) : message));
        policyMapper.updateById(policy);
    }

    private List<String> listSnapshotTables(String bakDb, String baseTable) {
        List<String> out = new ArrayList<>();
        try {
            for (String name : layerJdbc.listBaseTables(bakDb)) {
                if (parseStampSuffix(baseTable, name) != null) {
                    out.add(name);
                }
            }
        } catch (Exception e) {
            log.warn("list bak tables {}: {}", bakDb, e.getMessage());
        }
        return out;
    }

    private int deleteArchiveFiles(String sourceDb, String table, int cutoff) {
        int n = 0;
        Path dir = archiveDir.resolve(sourceDb);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path f : stream) {
                if (!Files.isRegularFile(f)) continue;
                Integer stamp = stampFromFileName(table, f.getFileName().toString());
                if (stamp == null || stamp > cutoff) continue;
                try {
                    Files.deleteIfExists(f);
                    n++;
                } catch (Exception e) {
                    log.warn("delete archive {}: {}", f, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("scan archive dir {}: {}", dir, e.getMessage());
        }
        return n;
    }

    private static boolean isPartitionScope(Map<String, Object> rule) {
        String scope = str(rule.get("backupScope"), "TABLE").toUpperCase(Locale.ROOT);
        return "PARTITION".equals(scope) || "BY_PARTITION".equals(scope) || "BY_BOTH".equals(scope);
    }

    private static String snapshotName(String table, String stamp) {
        return table + stamp;
    }

    private static String dayStamp() {
        return LocalDate.now().format(YMD);
    }

    /** 满 6 个月门槛，与快照名比较用的 yyyyMMdd。例如 2026-08-16 → 20260216。 */
    private static int cutoffDay() {
        LocalDate c = LocalDate.now().minusMonths(RETAIN_MONTHS);
        return c.getYear() * 10000 + c.getMonthValue() * 100 + c.getDayOfMonth();
    }

    /**
     * 从快照表名解析可比较的 yyyyMMdd。优先 {表}{yyyyMMdd}；兼容旧 {表}{yyyyMM}（按该月 1 日）。
     */
    private static Integer parseStampSuffix(String baseTable, String snapshot) {
        if (baseTable == null || snapshot == null || !snapshot.startsWith(baseTable)) {
            return null;
        }
        String suffix = snapshot.substring(baseTable.length());
        Matcher day = DAY_SUFFIX.matcher(suffix);
        if (day.matches()) {
            return parseYmd(suffix);
        }
        Matcher month = LEGACY_MONTH_SUFFIX.matcher(suffix);
        if (month.matches()) {
            Integer ym = parseYm(suffix);
            return ym == null ? null : ym * 100 + 1;
        }
        return null;
    }

    private static Integer stampFromFileName(String baseTable, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String n = fileName.trim();
        if (n.endsWith(".tsv.gz")) {
            n = n.substring(0, n.length() - 7);
        } else if (n.endsWith(".tsv")) {
            n = n.substring(0, n.length() - 4);
        }
        return parseStampSuffix(baseTable, n);
    }

    private static Integer stampFromLocation(String baseTable, String loc) {
        if (loc == null || loc.isBlank()) {
            return null;
        }
        int dot = loc.lastIndexOf('.');
        String name = dot >= 0 ? loc.substring(dot + 1) : loc;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return stampFromFileName(baseTable, name);
    }

    private static Integer parseYmd(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(yyyymmdd, YMD);
            if (d.getYear() < 2000) {
                return null;
            }
            return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseYm(String yyyymm) {
        if (yyyymm == null || yyyymm.length() != 6) {
            return null;
        }
        try {
            int y = Integer.parseInt(yyyymm.substring(0, 4));
            int mo = Integer.parseInt(yyyymm.substring(4, 6));
            if (y < 2000 || mo < 1 || mo > 12) {
                return null;
            }
            return y * 100 + mo;
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String requireSourceDb(String database) {
        String db = database == null ? "" : database.trim().toLowerCase(Locale.ROOT);
        if (!DataLayerSupport.isPlatformLayerDb(db)) {
            throw new BusinessException(400, "源库须为 smart_city / smart_city_ods / smart_city_dwd / smart_city_dws / smart_city_ads");
        }
        return db;
    }

    public static String requireIdent(String name, String field) {
        if (name == null || !IDENT.matcher(name).matches()) {
            throw new BusinessException(400, field + " 非法");
        }
        return name;
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }
}
