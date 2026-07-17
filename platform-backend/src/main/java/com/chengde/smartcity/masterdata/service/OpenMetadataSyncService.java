package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.openmetadata.OpenMetadataClient;
import com.chengde.smartcity.masterdata.entity.GovMetaRelation;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.mapper.GovMetaRelationMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * OpenMetadata 真实同步与对账：把平台登记条目（entryCode）双写为 OM service/database/schema/table，
 * 建立 entryCode ↔ OM FQN/entityId 映射，并写 Source→ODS→DWS 表级血缘。
 * OM 不可用时本地登记保留草稿（omSyncStatus=FAILED），绝不标记"OM 同步成功"。
 */
@Service
public class OpenMetadataSyncService {

    private static final Logger log = LoggerFactory.getLogger(OpenMetadataSyncService.class);
    private static final String PLATFORM_SERVICE = "chengde_platform_mysql";
    private static final String SOURCE_SERVICE = "chengde_source_mysql";

    private final OpenMetadataClient omClient;
    private final IntegrationProperties props;
    private final GovMetadataRegistryMapper registryMapper;
    private final GovMetaRelationMapper relationMapper;

    public OpenMetadataSyncService(OpenMetadataClient omClient, IntegrationProperties props,
                                   GovMetadataRegistryMapper registryMapper, GovMetaRelationMapper relationMapper) {
        this.omClient = omClient;
        this.props = props;
        this.registryMapper = registryMapper;
        this.relationMapper = relationMapper;
    }

    public boolean available() {
        return props.isEnabled() && omClient.isHealthy();
    }

    /**
     * 同步一个平台物理表到 OM（service/database/schema/table + columns），
     * 并把 OM FQN/entityId 回写到对应登记条目（entryCode）。
     * @param entryCode 平台登记条目编码（TABLE 级）
     * @param physicalTable 平台库物理表名
     * @param columns 列信息（columnName/dataType/columnSize）
     * @param source 是否源库表（决定 OM service 命名）
     * @return {fqn,id,syncStatus}
     */
    public Map<String, Object> syncTable(String entryCode, String physicalTable,
                                         List<Map<String, Object>> columns, boolean source) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entryCode", entryCode);
        out.put("physicalTable", physicalTable);
        GovMetadataRegistry entry = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, entryCode).last("LIMIT 1"));
        if (!available()) {
            markEntry(entry, null, null, "FAILED");
            out.put("syncStatus", "FAILED");
            out.put("message", "OpenMetadata 不可用，已保留本地草稿");
            return out;
        }
        try {
            String serviceName = source ? SOURCE_SERVICE : PLATFORM_SERVICE;
            String dbName = props.getKettle().getTargetDatabase();
            Map<String, Object> svc = omClient.ensureDatabaseService(serviceName,
                    props.getKettle().getTargetHost(), props.getKettle().getTargetPort(),
                    dbName, props.getKettle().getTargetUser(), props.getKettle().getTargetPassword());
            Map<String, Object> db = omClient.ensureDatabase(serviceName, dbName);
            String dbFqn = String.valueOf(db.get("fqn"));
            Map<String, Object> schema = omClient.ensureSchema(dbFqn, dbName);
            String schemaFqn = String.valueOf(schema.get("fqn"));
            Map<String, Object> table = omClient.upsertTable(schemaFqn, physicalTable, columns);
            String fqn = String.valueOf(table.get("fqn"));
            String id = String.valueOf(table.get("id"));
            markEntry(entry, fqn, id, "SYNCED");
            out.put("service", svc.get("fqn"));
            out.put("fqn", fqn);
            out.put("entityId", id);
            out.put("syncStatus", "SYNCED");
            return out;
        } catch (Exception e) {
            log.warn("OM 同步表失败 entry={} table={}: {}", entryCode, physicalTable, e.getMessage());
            markEntry(entry, null, null, "FAILED");
            out.put("syncStatus", "FAILED");
            out.put("message", e.getMessage());
            return out;
        }
    }

    /** 写 Source→ODS→DWS 表级血缘并写本地对账台账。 */
    public Map<String, Object> writeLineage(String fromEntryCode, String toEntryCode, String label) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromCode", fromEntryCode);
        out.put("toCode", toEntryCode);
        GovMetadataRegistry from = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, fromEntryCode).last("LIMIT 1"));
        GovMetadataRegistry to = registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, toEntryCode).last("LIMIT 1"));
        GovMetaRelation rel = upsertRelation(fromEntryCode, toEntryCode, label,
                from == null ? null : from.getOmRef(), to == null ? null : to.getOmRef());
        if (!available() || from == null || to == null
                || from.getOmEntityId() == null || to.getOmEntityId() == null) {
            rel.setOmSyncStatus("FAILED");
            relationMapper.updateById(rel);
            out.put("syncStatus", "FAILED");
            out.put("message", "OM 不可用或两端未同步，血缘仅记本地台账");
            return out;
        }
        try {
            omClient.addLineage(from.getOmEntityId(), to.getOmEntityId());
            rel.setOmSyncStatus("SYNCED");
            relationMapper.updateById(rel);
            out.put("syncStatus", "SYNCED");
            return out;
        } catch (Exception e) {
            log.warn("OM 写血缘失败 {} -> {}: {}", fromEntryCode, toEntryCode, e.getMessage());
            rel.setOmSyncStatus("FAILED");
            relationMapper.updateById(rel);
            out.put("syncStatus", "FAILED");
            out.put("message", e.getMessage());
            return out;
        }
    }

    /** 对账：返回本地登记与 OM 同步状态差异。 */
    public Map<String, Object> reconcile() {
        long total = registryMapper.selectCount(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryType, "TABLE"));
        long synced = registryMapper.selectCount(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryType, "TABLE")
                .eq(GovMetadataRegistry::getOmSyncStatus, "SYNCED"));
        long relTotal = relationMapper.selectCount(null);
        long relSynced = relationMapper.selectCount(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getOmSyncStatus, "SYNCED"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("omAvailable", available());
        out.put("tableEntries", total);
        out.put("tableSynced", synced);
        out.put("tablePending", total - synced);
        out.put("lineageEntries", relTotal);
        out.put("lineageSynced", relSynced);
        return out;
    }

    private void markEntry(GovMetadataRegistry entry, String fqn, String id, String status) {
        if (entry == null) {
            return;
        }
        if (fqn != null) {
            entry.setOmRef(fqn);
        }
        if (id != null) {
            entry.setOmEntityId(id);
        }
        entry.setOmSyncStatus(status);
        if ("SYNCED".equals(status)) {
            entry.setOmSyncedAt(LocalDateTime.now());
        }
        registryMapper.updateById(entry);
    }

    private GovMetaRelation upsertRelation(String fromCode, String toCode, String label,
                                           String fromFqn, String toFqn) {
        GovMetaRelation rel = relationMapper.selectOne(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getFromCode, fromCode)
                .eq(GovMetaRelation::getToCode, toCode)
                .eq(GovMetaRelation::getRelationType, "LINEAGE")
                .last("LIMIT 1"));
        boolean creating = rel == null;
        if (creating) {
            rel = new GovMetaRelation();
            rel.setFromCode(fromCode);
            rel.setToCode(toCode);
            rel.setRelationType("LINEAGE");
            rel.setStatus("ACTIVE");
            rel.setCreatedAt(LocalDateTime.now());
        }
        rel.setLabel(label);
        rel.setOmFromFqn(fromFqn);
        rel.setOmToFqn(toFqn);
        if (creating) {
            relationMapper.insert(rel);
        } else {
            relationMapper.updateById(rel);
        }
        return rel;
    }
}
