package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.RcAssetCatalogEntry;
import com.chengde.smartcity.masterdata.entity.RcBaseLibrary;
import com.chengde.smartcity.masterdata.entity.RcMonitorMetric;
import com.chengde.smartcity.masterdata.entity.RcPartitionDef;
import com.chengde.smartcity.masterdata.entity.RcStoragePolicy;
import com.chengde.smartcity.masterdata.entity.RcThemeLibrary;
import com.chengde.smartcity.masterdata.mapper.RcAssetCatalogEntryMapper;
import com.chengde.smartcity.masterdata.mapper.RcBaseLibraryMapper;
import com.chengde.smartcity.masterdata.mapper.RcMonitorMetricMapper;
import com.chengde.smartcity.masterdata.mapper.RcPartitionDefMapper;
import com.chengde.smartcity.masterdata.mapper.RcStoragePolicyMapper;
import com.chengde.smartcity.masterdata.mapper.RcThemeLibraryMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceCenterPlatformService {

    private final MasterDataDemoService demoService;
    private final RcBaseLibraryMapper libraryMapper;
    private final RcPartitionDefMapper partitionMapper;
    private final RcStoragePolicyMapper policyMapper;
    private final RcAssetCatalogEntryMapper catalogMapper;
    private final RcMonitorMetricMapper monitorMapper;
    private final RcThemeLibraryMapper themeMapper;
    private final AuditService auditService;

    public ResourceCenterPlatformService(MasterDataDemoService demoService, RcBaseLibraryMapper libraryMapper,
                                         RcPartitionDefMapper partitionMapper, RcStoragePolicyMapper policyMapper,
                                         RcAssetCatalogEntryMapper catalogMapper, RcMonitorMetricMapper monitorMapper,
                                         RcThemeLibraryMapper themeMapper, AuditService auditService) {
        this.demoService = demoService;
        this.libraryMapper = libraryMapper;
        this.partitionMapper = partitionMapper;
        this.policyMapper = policyMapper;
        this.catalogMapper = catalogMapper;
        this.monitorMapper = monitorMapper;
        this.themeMapper = themeMapper;
        this.auditService = auditService;
    }

    public Map<String, Object> libraryOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseLibraries", libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>().eq(RcBaseLibrary::getLibType, "BASE")));
        out.put("semiLibraries", libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>().eq(RcBaseLibrary::getLibType, "SEMI")));
        out.put("unstructLibraries", libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>().eq(RcBaseLibrary::getLibType, "UNSTRUCT")));
        out.put("themes", demoService.listThemes());
        return out;
    }

    public List<RcBaseLibrary> listLibraries(String libType) {
        LambdaQueryWrapper<RcBaseLibrary> q = new LambdaQueryWrapper<RcBaseLibrary>().orderByAsc(RcBaseLibrary::getId);
        if (libType != null && !libType.isBlank()) {
            q.eq(RcBaseLibrary::getLibType, libType);
        }
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

    public Map<String, Object> partitionOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("partitions", partitionMapper.selectList(new LambdaQueryWrapper<RcPartitionDef>().orderByAsc(RcPartitionDef::getId)));
        out.put("policies", policyMapper.selectList(new LambdaQueryWrapper<RcStoragePolicy>().orderByAsc(RcStoragePolicy::getId)));
        out.put("backups", demoService.listBackupJobs());
        return out;
    }

    @Transactional
    public Map<String, Object> executePolicy(UserPrincipal operator, Long policyId) {
        RcStoragePolicy p = policyMapper.selectById(policyId);
        if (p == null) {
            throw new BusinessException(404, "策略不存在");
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "RC_POLICY_RUN", "rc_storage_policy", String.valueOf(policyId), p.getActionType());
        return Map.of("policyId", policyId, "actionType", p.getActionType(), "status", "SUCCESS",
                "retentionDays", p.getRetentionDays());
    }

    public List<RcAssetCatalogEntry> listCatalogEntries() {
        return catalogMapper.selectList(new LambdaQueryWrapper<RcAssetCatalogEntry>().orderByDesc(RcAssetCatalogEntry::getId));
    }

    @Transactional
    public Long createCatalogEntry(UserPrincipal operator, Map<String, Object> body) {
        RcAssetCatalogEntry e = new RcAssetCatalogEntry();
        e.setEntryCode(str(body.get("entryCode"), "ACE_" + System.currentTimeMillis()));
        e.setEntryName(required(body.get("entryName"), "entryName").toString());
        Object libId = body.get("libId");
        if (libId != null) {
            e.setLibId(Long.valueOf(String.valueOf(libId)));
        }
        e.setDriveTask(str(body.get("driveTask"), "collect-task"));
        e.setStatus("ACTIVE");
        catalogMapper.insert(e);
        return e.getId();
    }

    public Map<String, Object> searchLibraries(String q) {
        List<Map<String, Object>> hits = new ArrayList<>();
        LambdaQueryWrapper<RcBaseLibrary> query = new LambdaQueryWrapper<RcBaseLibrary>().orderByDesc(RcBaseLibrary::getRecordCount);
        if (q != null && !q.isBlank()) {
            query.like(RcBaseLibrary::getLibName, q);
        }
        for (RcBaseLibrary lib : libraryMapper.selectList(query.last("LIMIT 20"))) {
            hits.add(Map.of("libCode", lib.getLibCode(), "libName", lib.getLibName(), "libType", lib.getLibType(), "recordCount", lib.getRecordCount()));
        }
        return Map.of("query", q == null ? "" : q, "hits", hits);
    }

    public Map<String, Object> statistics() {
        long totalRecords = libraryMapper.selectList(null).stream().mapToLong(l -> l.getRecordCount() == null ? 0 : l.getRecordCount()).sum();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalRecords", totalRecords);
        out.put("libraryCount", libraryMapper.selectCount(null));
        out.put("themeCount", themeMapper.selectCount(null));
        out.put("topLibraries", libraryMapper.selectList(new LambdaQueryWrapper<RcBaseLibrary>().orderByDesc(RcBaseLibrary::getRecordCount).last("LIMIT 5")));
        return out;
    }

    public List<RcMonitorMetric> monitorMetrics() {
        return monitorMapper.selectList(new LambdaQueryWrapper<RcMonitorMetric>().orderByAsc(RcMonitorMetric::getId));
    }

    private String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }
}
