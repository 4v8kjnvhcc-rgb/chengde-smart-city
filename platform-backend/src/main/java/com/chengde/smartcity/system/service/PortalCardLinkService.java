package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.PortalCardLinkRequest;
import com.chengde.smartcity.system.entity.PortalCardLink;
import com.chengde.smartcity.system.mapper.PortalCardLinkMapper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PortalCardLinkService {

    private static final Set<String> PLATFORM_PATHS = Set.of(
            "/exchange", "/master-data", "/analytics", "/business", "/system");
    private static final Set<String> OPEN_MODES = Set.of("new_tab", "same_tab");
    private static final Set<String> SSO_MODES = Set.of("none", "token_query");

    private final PortalCardLinkMapper linkMapper;
    private final AuditService auditService;

    public PortalCardLinkService(PortalCardLinkMapper linkMapper, AuditService auditService) {
        this.linkMapper = linkMapper;
        this.auditService = auditService;
    }

    public List<PortalCardLink> listAll() {
        return linkMapper.selectList(new LambdaQueryWrapper<PortalCardLink>()
                .orderByAsc(PortalCardLink::getPlatformPath)
                .orderByAsc(PortalCardLink::getSortOrder)
                .orderByAsc(PortalCardLink::getId));
    }

    /** 门户 Hub：仅返回启用中的外链 */
    public List<PortalCardLink> listEnabled(String platformPath) {
        LambdaQueryWrapper<PortalCardLink> q = new LambdaQueryWrapper<PortalCardLink>()
                .eq(PortalCardLink::getStatus, 1)
                .orderByAsc(PortalCardLink::getSortOrder)
                .orderByAsc(PortalCardLink::getId);
        if (StringUtils.hasText(platformPath)) {
            q.eq(PortalCardLink::getPlatformPath, platformPath);
        }
        return linkMapper.selectList(q);
    }

    @Transactional
    public Long create(UserPrincipal operator, PortalCardLinkRequest req) {
        PortalCardLink link = new PortalCardLink();
        apply(link, req, true);
        linkMapper.insert(link);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_LINK_CREATE", "portal_card_link", String.valueOf(link.getId()), link.getTitle());
        return link.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, PortalCardLinkRequest req) {
        PortalCardLink link = require(id);
        apply(link, req, false);
        linkMapper.updateById(link);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_LINK_UPDATE", "portal_card_link", String.valueOf(id), link.getTitle());
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        PortalCardLink link = require(id);
        linkMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_LINK_DELETE", "portal_card_link", String.valueOf(id), link.getTitle());
    }

    private PortalCardLink require(Long id) {
        PortalCardLink link = linkMapper.selectById(id);
        if (link == null) {
            throw new BusinessException(404, "外链不存在");
        }
        return link;
    }

    private void apply(PortalCardLink link, PortalCardLinkRequest req, boolean creating) {
        String platformPath = req.platformPath().trim();
        if (!PLATFORM_PATHS.contains(platformPath)) {
            throw new BusinessException(400, "所属卡片无效");
        }
        String openMode = StringUtils.hasText(req.openMode()) ? req.openMode().trim() : "new_tab";
        String ssoMode = StringUtils.hasText(req.ssoMode()) ? req.ssoMode().trim() : "token_query";
        if (!OPEN_MODES.contains(openMode)) {
            throw new BusinessException(400, "打开方式无效");
        }
        if (!SSO_MODES.contains(ssoMode)) {
            throw new BusinessException(400, "SSO 模式无效");
        }
        String url = req.url().trim();
        if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/"))) {
            throw new BusinessException(400, "链接须以 http(s):// 或 / 开头");
        }

        link.setPlatformPath(platformPath);
        link.setTitle(req.title().trim());
        link.setUrl(url);
        link.setDescription(req.description());
        link.setOpenMode(openMode);
        link.setSsoMode(ssoMode);
        link.setSsoParam(StringUtils.hasText(req.ssoParam()) ? req.ssoParam().trim() : "access_token");
        link.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        if (req.status() != null) {
            link.setStatus(req.status());
        } else if (creating) {
            link.setStatus(1);
        }
    }
}
