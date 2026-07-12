package com.chengde.smartcity.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.AnaAnalysisModel;
import com.chengde.smartcity.analysis.entity.AnaDomainModule;
import com.chengde.smartcity.analysis.entity.AnaModelSample;
import com.chengde.smartcity.analysis.mapper.AnaAnalysisModelMapper;
import com.chengde.smartcity.analysis.mapper.AnaDomainModuleMapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.dataease.DataEaseClient;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsDomainService {

    private final AnaDomainModuleMapper moduleMapper;
    private final AnaAnalysisModelMapper modelMapper;
    private final AnalysisDemoService analysisDemoService;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final DataEaseClient dataEaseClient;

    public AnalyticsDomainService(AnaDomainModuleMapper moduleMapper, AnaAnalysisModelMapper modelMapper,
                                  AnalysisDemoService analysisDemoService, AuditService auditService,
                                  IntegrationProperties integrationProperties, DataEaseClient dataEaseClient) {
        this.moduleMapper = moduleMapper;
        this.modelMapper = modelMapper;
        this.analysisDemoService = analysisDemoService;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.dataEaseClient = dataEaseClient;
    }

    public Map<String, Object> domainOverview(String domain) {
        List<AnaDomainModule> modules = moduleMapper.selectList(new LambdaQueryWrapper<AnaDomainModule>()
                .eq(AnaDomainModule::getDomainCode, domain)
                .orderByAsc(AnaDomainModule::getSortOrder));
        long dataOps = modules.stream().filter(m -> "DATA_OPS".equals(m.getModuleType())).count();
        long analysis = modules.stream().filter(m -> "ANALYSIS".equals(m.getModuleType())).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("domain", domain);
        out.put("modules", modules);
        out.put("dataOpsCount", dataOps);
        out.put("analysisCount", analysis);
        out.put("totalModules", modules.size());
        out.put("dataEaseHealthy", integrationProperties.isEnabled() && dataEaseClient.isHealthy());
        return out;
    }

    public Map<String, Object> moduleDetail(String mCode) {
        AnaDomainModule mod = getModule(mCode);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("module", mod);
        if ("ANALYSIS".equals(mod.getModuleType())) {
            AnaAnalysisModel model = modelMapper.selectOne(new LambdaQueryWrapper<AnaAnalysisModel>()
                    .eq(AnaAnalysisModel::getMCode, mCode)
                    .last("LIMIT 1"));
            out.put("model", model);
            if (model != null) {
                List<AnaModelSample> samples = analysisDemoService.samples(model.getId());
                out.put("sampleCount", samples.size());
                out.put("samplesPreview", samples.size() > 20 ? samples.subList(0, 20) : samples);
            }
        }
        return out;
    }

    @Transactional
    public Map<String, Object> runDataOps(UserPrincipal operator, String mCode) {
        AnaDomainModule mod = getModule(mCode);
        if (!"DATA_OPS".equals(mod.getModuleType())) {
            throw new BusinessException(400, "not a data ops module");
        }
        mod.setLastRunAt(LocalDateTime.now());
        mod.setLastMessage(mod.getCapGroup() + " job completed");
        mod.setStatus("SUCCESS");
        moduleMapper.updateById(mod);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_DOMAIN_RUN", mod.getDomainCode(), mCode, mod.getModuleName());
        return Map.of("mCode", mCode, "status", "SUCCESS", "message", mod.getLastMessage());
    }

    public Map<String, Object> issueModuleEmbed(UserPrincipal operator, String mCode) {
        AnaDomainModule mod = getModule(mCode);
        if (!"ANALYSIS".equals(mod.getModuleType())) {
            throw new BusinessException(400, "not an analysis module");
        }
        String targetId = mod.getDeDashboardId() != null ? mod.getDeDashboardId() : "demo";
        Map<String, Object> token = new HashMap<>(analysisDemoService.issueEmbedToken(operator, "model", targetId));
        token.put("module", mod);
        return token;
    }

    private AnaDomainModule getModule(String mCode) {
        String code = mCode.toUpperCase();
        if (!code.startsWith("M")) {
            code = "M" + code;
        }
        AnaDomainModule mod = moduleMapper.selectOne(new LambdaQueryWrapper<AnaDomainModule>()
                .eq(AnaDomainModule::getMCode, code));
        if (mod == null) {
            throw new BusinessException(404, "domain module not found");
        }
        return mod;
    }
}
