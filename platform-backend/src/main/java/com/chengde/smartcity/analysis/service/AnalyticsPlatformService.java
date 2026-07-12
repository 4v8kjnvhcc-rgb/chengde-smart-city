package com.chengde.smartcity.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.analysis.entity.AnaBiDashboard;
import com.chengde.smartcity.analysis.entity.AnaBiWidget;
import com.chengde.smartcity.analysis.entity.AnaPlatformApp;
import com.chengde.smartcity.analysis.entity.AnaPlatformConfig;
import com.chengde.smartcity.analysis.entity.AnaPlatformIntegration;
import com.chengde.smartcity.analysis.entity.AnaPlatformService;
import com.chengde.smartcity.analysis.mapper.AnaBiDashboardMapper;
import com.chengde.smartcity.analysis.mapper.AnaBiWidgetMapper;
import com.chengde.smartcity.analysis.mapper.AnaPlatformAppMapper;
import com.chengde.smartcity.analysis.mapper.AnaPlatformConfigMapper;
import com.chengde.smartcity.analysis.mapper.AnaPlatformIntegrationMapper;
import com.chengde.smartcity.analysis.mapper.AnaPlatformServiceMapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.dataease.DataEaseClient;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsPlatformService {

    private final AnaPlatformAppMapper appMapper;
    private final AnaPlatformServiceMapper serviceMapper;
    private final AnaPlatformConfigMapper configMapper;
    private final AnaPlatformIntegrationMapper integrationMapper;
    private final AnaBiWidgetMapper widgetMapper;
    private final AnaBiDashboardMapper dashboardMapper;
    private final AuditService auditService;
    private final IntegrationProperties integrationProperties;
    private final DataEaseClient dataEaseClient;
    private final AnalysisDemoService analysisDemoService;

    public AnalyticsPlatformService(AnaPlatformAppMapper appMapper, AnaPlatformServiceMapper serviceMapper,
                                    AnaPlatformConfigMapper configMapper, AnaPlatformIntegrationMapper integrationMapper,
                                    AnaBiWidgetMapper widgetMapper, AnaBiDashboardMapper dashboardMapper,
                                    AuditService auditService, IntegrationProperties integrationProperties,
                                    DataEaseClient dataEaseClient, AnalysisDemoService analysisDemoService) {
        this.appMapper = appMapper;
        this.serviceMapper = serviceMapper;
        this.configMapper = configMapper;
        this.integrationMapper = integrationMapper;
        this.widgetMapper = widgetMapper;
        this.dashboardMapper = dashboardMapper;
        this.auditService = auditService;
        this.integrationProperties = integrationProperties;
        this.dataEaseClient = dataEaseClient;
        this.analysisDemoService = analysisDemoService;
    }

    public Map<String, Object> supportOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("apps", appMapper.selectList(new LambdaQueryWrapper<AnaPlatformApp>().orderByAsc(AnaPlatformApp::getId)));
        out.put("services", serviceMapper.selectList(new LambdaQueryWrapper<AnaPlatformService>().orderByAsc(AnaPlatformService::getId)));
        out.put("configs", configMapper.selectList(new LambdaQueryWrapper<AnaPlatformConfig>().orderByAsc(AnaPlatformConfig::getId)));
        out.put("integrations", integrationMapper.selectList(new LambdaQueryWrapper<AnaPlatformIntegration>().orderByAsc(AnaPlatformIntegration::getId)));
        out.put("systemLinks", List.of(
                Map.of("mCode", "M139", "label", "用户中心", "route", "/system/users"),
                Map.of("mCode", "M144", "label", "日志审计", "route", "/system/audit"),
                Map.of("mCode", "M143", "label", "等保配置", "route", "/system/security")
        ));
        return out;
    }

    @Transactional
    public Long createApp(UserPrincipal operator, Map<String, Object> body) {
        AnaPlatformApp app = new AnaPlatformApp();
        app.setAppCode(str(body.get("appCode"), "APP_" + System.currentTimeMillis()));
        app.setAppName(required(body.get("appName"), "appName").toString());
        app.setAppType(str(body.get("appType"), "WEB"));
        app.setEndpointUrl(str(body.get("endpointUrl"), "/"));
        app.setStatus("ACTIVE");
        app.setMCode("M140");
        appMapper.insert(app);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_APP_CREATE", "ana_platform_app", String.valueOf(app.getId()), app.getAppName());
        return app.getId();
    }

    @Transactional
    public Long createService(UserPrincipal operator, Map<String, Object> body) {
        AnaPlatformService svc = new AnaPlatformService();
        svc.setServiceCode(str(body.get("serviceCode"), "SVC_" + System.currentTimeMillis()));
        svc.setServiceName(required(body.get("serviceName"), "serviceName").toString());
        svc.setServicePath(str(body.get("servicePath"), "/api/v1/"));
        svc.setProtocol(str(body.get("protocol"), "REST"));
        svc.setStatus("PUBLISHED");
        svc.setMCode("M142");
        serviceMapper.insert(svc);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_SVC_CREATE", "ana_platform_service", String.valueOf(svc.getId()), svc.getServiceName());
        return svc.getId();
    }

    @Transactional
    public void updateConfig(UserPrincipal operator, Long id, Map<String, Object> body) {
        AnaPlatformConfig cfg = configMapper.selectById(id);
        if (cfg == null) {
            throw new BusinessException(404, "config not found");
        }
        if (body.containsKey("configValue")) {
            cfg.setConfigValue(String.valueOf(body.get("configValue")));
        }
        configMapper.updateById(cfg);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_CFG_UPDATE", "ana_platform_config", String.valueOf(id), cfg.getConfigKey());
    }

    @Transactional
    public Map<String, Object> testIntegration(UserPrincipal operator, Long id) {
        AnaPlatformIntegration row = integrationMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "integration not found");
        }
        boolean ok = false;
        String msg = "offline";
        if ("DataEase".equals(row.getTargetSystem()) && integrationProperties.isEnabled()) {
            ok = dataEaseClient.isHealthy();
            msg = ok ? "DataEase reachable" : "DataEase unreachable";
        } else if (integrationProperties.isEnabled()) {
            ok = true;
            msg = "endpoint configured";
        } else {
            msg = "integration disabled (demo mode)";
            ok = true;
        }
        row.setLastMessage(msg);
        row.setStatus(ok ? "ACTIVE" : "ERROR");
        integrationMapper.updateById(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ANA_INT_TEST", "ana_platform_integration", String.valueOf(id), msg);
        return Map.of("integrationCode", row.getIntegrationCode(), "status", row.getStatus(), "message", msg);
    }

    public Map<String, Object> biOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("widgets", widgetMapper.selectList(new LambdaQueryWrapper<AnaBiWidget>()
                .eq(AnaBiWidget::getStatus, "ACTIVE")
                .orderByAsc(AnaBiWidget::getSortOrder)));
        out.put("dashboards", dashboardMapper.selectList(new LambdaQueryWrapper<AnaBiDashboard>().orderByAsc(AnaBiDashboard::getId)));
        out.put("dataEaseHealthy", integrationProperties.isEnabled() && dataEaseClient.isHealthy());
        out.put("dataEaseUrl", integrationProperties.getDe().getUrl());
        return out;
    }

    public AnaBiWidget getWidgetByMCode(String mCode) {
        AnaBiWidget w = widgetMapper.selectOne(new LambdaQueryWrapper<AnaBiWidget>().eq(AnaBiWidget::getMCode, mCode));
        if (w == null) {
            throw new BusinessException(404, "widget not found");
        }
        return w;
    }

    public Map<String, Object> issueWidgetEmbed(UserPrincipal operator, String mCode) {
        AnaBiWidget w = getWidgetByMCode(mCode);
        Map<String, Object> token = new HashMap<>(analysisDemoService.issueEmbedToken(operator, "widget", w.getDeDashboardId()));
        token.put("widget", w);
        return token;
    }

    private static String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private static Object required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return v;
    }
}
