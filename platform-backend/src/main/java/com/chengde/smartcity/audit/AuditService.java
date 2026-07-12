package com.chengde.smartcity.audit;

import com.chengde.smartcity.system.entity.AuditLog;
import com.chengde.smartcity.system.mapper.AuditLogMapper;
import com.chengde.smartcity.system.service.SecurityConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final SecurityConfigService securityConfigService;

    public AuditService(AuditLogMapper auditLogMapper, SecurityConfigService securityConfigService) {
        this.auditLogMapper = auditLogMapper;
        this.securityConfigService = securityConfigService;
    }

    public void log(Long userId, String username, Long orgId, String action, String resourceType,
                    String resourceId, String detail) {
        if (!securityConfigService.isAuditEnabled()) {
            return;
        }
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setOrgId(orgId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetail(detail);
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            log.setIpAddress(request.getRemoteAddr());
            log.setUserAgent(request.getHeader("User-Agent"));
        }
        auditLogMapper.insert(log);
    }
}
