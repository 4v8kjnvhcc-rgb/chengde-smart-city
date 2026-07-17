package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovCatalogAuthorization;
import com.chengde.smartcity.masterdata.entity.GovCatalogResource;
import com.chengde.smartcity.masterdata.entity.GovCatalogSubscription;
import com.chengde.smartcity.masterdata.entity.GovMetaRelation;
import com.chengde.smartcity.masterdata.entity.GovMetadataRegistry;
import com.chengde.smartcity.masterdata.entity.GovQualityIssue;
import com.chengde.smartcity.masterdata.entity.GovQualityTask;
import com.chengde.smartcity.masterdata.entity.GovQualityTaskRun;
import com.chengde.smartcity.masterdata.mapper.GovCatalogAuthorizationMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogResourceMapper;
import com.chengde.smartcity.masterdata.mapper.GovCatalogSubscriptionMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetaRelationMapper;
import com.chengde.smartcity.masterdata.mapper.GovMetadataRegistryMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityIssueMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityTaskRunMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 资产 360：以 entry_code 为枢纽，把元数据、血缘、质量、目录、订阅授权聚合到一屏，实现跨域产物联动。
 */
@Service
public class AssetProfileService {

    private final GovMetadataRegistryMapper registryMapper;
    private final GovMetaRelationMapper relationMapper;
    private final GovQualityTaskMapper qualityTaskMapper;
    private final GovQualityTaskRunMapper qualityRunMapper;
    private final GovQualityIssueMapper qualityIssueMapper;
    private final GovCatalogResourceMapper catalogResourceMapper;
    private final GovCatalogSubscriptionMapper catalogSubscriptionMapper;
    private final GovCatalogAuthorizationMapper authorizationMapper;

    public AssetProfileService(GovMetadataRegistryMapper registryMapper,
                               GovMetaRelationMapper relationMapper,
                               GovQualityTaskMapper qualityTaskMapper,
                               GovQualityTaskRunMapper qualityRunMapper,
                               GovQualityIssueMapper qualityIssueMapper,
                               GovCatalogResourceMapper catalogResourceMapper,
                               GovCatalogSubscriptionMapper catalogSubscriptionMapper,
                               GovCatalogAuthorizationMapper authorizationMapper) {
        this.registryMapper = registryMapper;
        this.relationMapper = relationMapper;
        this.qualityTaskMapper = qualityTaskMapper;
        this.qualityRunMapper = qualityRunMapper;
        this.qualityIssueMapper = qualityIssueMapper;
        this.catalogResourceMapper = catalogResourceMapper;
        this.catalogSubscriptionMapper = catalogSubscriptionMapper;
        this.authorizationMapper = authorizationMapper;
    }

    public Map<String, Object> asset360(String entryCode) {
        if (entryCode == null || entryCode.isBlank()) {
            throw new BusinessException(400, "entryCode 必填");
        }
        String code = entryCode.trim();
        GovMetadataRegistry entry = findEntry(code);
        if (entry == null) {
            throw new BusinessException(404, "元数据条目不存在: " + code);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entryCode", code);
        out.put("entry", entryMap(entry));
        out.put("columns", columns(code));
        out.put("lineage", lineage(code));
        out.put("quality", quality(code));
        out.put("catalog", catalog(code));
        out.put("subscriptions", subscriptions(code));
        return out;
    }

    private Map<String, Object> entryMap(GovMetadataRegistry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("entryCode", e.getEntryCode());
        m.put("entryName", e.getEntryName());
        m.put("entryType", e.getEntryType());
        m.put("parentCode", e.getParentCode());
        m.put("physicalTableName", e.getPhysicalTableName());
        m.put("dataSourceId", e.getDataSourceId());
        m.put("tags", e.getTags());
        m.put("keywords", e.getKeywords());
        m.put("securityLevel", e.getSecurityLevel());
        m.put("status", e.getStatus());
        m.put("description", e.getDescription());
        m.put("updatedAt", e.getUpdatedAt());
        return m;
    }

    private List<Map<String, Object>> columns(String parentCode) {
        List<GovMetadataRegistry> cols = registryMapper.selectList(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getParentCode, parentCode)
                .eq(GovMetadataRegistry::getEntryType, "COLUMN")
                .orderByAsc(GovMetadataRegistry::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetadataRegistry c : cols) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entryCode", c.getEntryCode());
            m.put("name", c.getEntryName());
            m.put("dataType", c.getDescription());
            m.put("status", c.getStatus());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> lineage(String code) {
        List<GovMetaRelation> upstream = relationMapper.selectList(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getToCode, code)
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .orderByDesc(GovMetaRelation::getId));
        List<GovMetaRelation> downstream = relationMapper.selectList(new LambdaQueryWrapper<GovMetaRelation>()
                .eq(GovMetaRelation::getFromCode, code)
                .eq(GovMetaRelation::getStatus, "ACTIVE")
                .orderByDesc(GovMetaRelation::getId));

        Set<String> codes = new LinkedHashSet<>();
        codes.add(code);
        for (GovMetaRelation r : upstream) codes.add(r.getFromCode());
        for (GovMetaRelation r : downstream) codes.add(r.getToCode());

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String c : codes) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", c);
            n.put("label", resolveName(c));
            n.put("current", c.equals(code));
            nodes.add(n);
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GovMetaRelation r : upstream) edges.add(edge(r));
        for (GovMetaRelation r : downstream) edges.add(edge(r));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("upstream", relList(upstream, true));
        out.put("downstream", relList(downstream, false));
        out.put("nodes", nodes);
        out.put("edges", edges);
        return out;
    }

    private List<Map<String, Object>> relList(List<GovMetaRelation> relations, boolean upstream) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovMetaRelation r : relations) {
            String peer = upstream ? r.getFromCode() : r.getToCode();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", peer);
            m.put("name", resolveName(peer));
            m.put("relationType", r.getRelationType());
            m.put("label", r.getLabel());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> edge(GovMetaRelation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", r.getFromCode());
        m.put("to", r.getToCode());
        m.put("type", r.getRelationType());
        m.put("label", r.getLabel());
        return m;
    }

    private Map<String, Object> quality(String entryCode) {
        Map<String, Object> out = new LinkedHashMap<>();
        GovQualityTask task = qualityTaskMapper.selectOne(new LambdaQueryWrapper<GovQualityTask>()
                .eq(GovQualityTask::getMetadataEntryCode, entryCode)
                .orderByDesc(GovQualityTask::getId)
                .last("LIMIT 1"));
        if (task == null) {
            out.put("status", "NONE");
            return out;
        }
        out.put("taskId", task.getId());
        out.put("taskName", task.getTaskName());
        out.put("lastScore", task.getLastScore());
        GovQualityTaskRun run = qualityRunMapper.selectOne(new LambdaQueryWrapper<GovQualityTaskRun>()
                .eq(GovQualityTaskRun::getTaskId, task.getId())
                .orderByDesc(GovQualityTaskRun::getId)
                .last("LIMIT 1"));
        out.put("status", run == null ? "PENDING" : run.getStatus());
        if (run != null) {
            out.put("runId", run.getId());
            out.put("score", run.getScore());
            out.put("issueCount", run.getIssueCount());
            out.put("endedAt", run.getEndedAt());
            out.put("issues", topIssues(run.getId()));
        }
        return out;
    }

    private List<Map<String, Object>> topIssues(Long runId) {
        List<GovQualityIssue> issues = qualityIssueMapper.selectList(new LambdaQueryWrapper<GovQualityIssue>()
                .eq(GovQualityIssue::getRunId, runId)
                .orderByDesc(GovQualityIssue::getId)
                .last("LIMIT 10"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityIssue i : issues) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("checkType", i.getCheckType());
            m.put("targetColumn", i.getTargetColumn());
            m.put("issueType", i.getIssueType());
            m.put("issueValue", i.getIssueValue());
            m.put("severity", i.getSeverity());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> catalog(String entryCode) {
        Map<String, Object> out = new LinkedHashMap<>();
        GovCatalogResource resource = catalogResourceMapper.selectOne(new LambdaQueryWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getMetadataEntryCode, entryCode)
                .orderByDesc(GovCatalogResource::getId)
                .last("LIMIT 1"));
        if (resource == null) {
            out.put("status", "NONE");
            return out;
        }
        out.put("status", resource.getPublishStatus());
        out.put("resourceId", resource.getId());
        out.put("resourceCode", resource.getResourceCode());
        out.put("resourceName", resource.getResourceName());
        out.put("sourcePathType", resource.getSourcePathType());
        out.put("qualityScore", resource.getQualityScore());
        out.put("publishStatus", resource.getPublishStatus());
        out.put("approvalStatus", resource.getApprovalStatus());
        out.put("categoryPath", resource.getCategoryPath());
        return out;
    }

    private List<Map<String, Object>> subscriptions(String entryCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        GovCatalogResource resource = catalogResourceMapper.selectOne(new LambdaQueryWrapper<GovCatalogResource>()
                .eq(GovCatalogResource::getMetadataEntryCode, entryCode)
                .orderByDesc(GovCatalogResource::getId)
                .last("LIMIT 1"));
        if (resource == null) {
            return out;
        }
        List<GovCatalogSubscription> subs = catalogSubscriptionMapper.selectList(
                new LambdaQueryWrapper<GovCatalogSubscription>()
                        .eq(GovCatalogSubscription::getResourceId, resource.getId())
                        .orderByDesc(GovCatalogSubscription::getId));
        for (GovCatalogSubscription s : subs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subscriptionId", s.getId());
            m.put("status", s.getStatus());
            m.put("shareMode", s.getShareMode());
            m.put("applicantOrg", s.getApplicantOrg());
            m.put("applicantUser", s.getApplicantUser());
            GovCatalogAuthorization auth = authorizationMapper.selectOne(
                    new LambdaQueryWrapper<GovCatalogAuthorization>()
                            .eq(GovCatalogAuthorization::getSubscriptionId, s.getId())
                            .last("LIMIT 1"));
            if (auth != null) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("authorizationCode", auth.getAuthorizationCode());
                a.put("status", auth.getStatus());
                a.put("credentialRef", auth.getCredentialRef());
                a.put("validFrom", auth.getValidFrom());
                m.put("authorization", a);
            }
            out.add(m);
        }
        return out;
    }

    private String resolveName(String code) {
        GovMetadataRegistry e = findEntry(code);
        return e == null ? code : e.getEntryName();
    }

    private GovMetadataRegistry findEntry(String code) {
        return registryMapper.selectOne(new LambdaQueryWrapper<GovMetadataRegistry>()
                .eq(GovMetadataRegistry::getEntryCode, code)
                .last("LIMIT 1"));
    }
}
