package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.OrgCreateRequest;
import com.chengde.smartcity.system.dto.OrgUpdateRequest;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgService {

    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;
    private final AuditService auditService;

    public OrgService(SysOrgMapper orgMapper, SysUserMapper userMapper, AuditService auditService) {
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    public List<SysOrg> list() {
        return orgMapper.selectList(new LambdaQueryWrapper<SysOrg>().orderByAsc(SysOrg::getSortOrder).orderByAsc(SysOrg::getId));
    }

    @Transactional
    public Long create(UserPrincipal operator, OrgCreateRequest req) {
        if (orgMapper.selectCount(new LambdaQueryWrapper<SysOrg>().eq(SysOrg::getOrgCode, req.orgCode())) > 0) {
            throw new BusinessException(400, "机构编码已存在");
        }
        SysOrg org = new SysOrg();
        org.setOrgCode(req.orgCode());
        org.setOrgName(req.orgName());
        org.setParentId(req.parentId() == null ? 0L : req.parentId());
        org.setOrgType(req.orgType() == null ? 1 : req.orgType());
        org.setSortOrder(0);
        org.setStatus(1);
        orgMapper.insert(org);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ORG_CREATE", "sys_org", String.valueOf(org.getId()), req.orgCode());
        return org.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, OrgUpdateRequest req) {
        SysOrg org = orgMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(404, "机构不存在");
        }
        if (req.orgName() != null) {
            org.setOrgName(req.orgName());
        }
        if (req.parentId() != null) {
            org.setParentId(req.parentId());
        }
        if (req.status() != null) {
            org.setStatus(req.status());
        }
        if (req.sortOrder() != null) {
            org.setSortOrder(req.sortOrder());
        }
        orgMapper.updateById(org);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ORG_UPDATE", "sys_org", String.valueOf(id), org.getOrgCode());
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        SysOrg org = orgMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(404, "机构不存在");
        }
        long children = orgMapper.selectCount(new LambdaQueryWrapper<SysOrg>().eq(SysOrg::getParentId, id));
        if (children > 0) {
            throw new BusinessException(400, "请先删除下级机构");
        }
        long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getOrgId, id));
        if (users > 0) {
            throw new BusinessException(400, "机构下仍有用户，无法删除");
        }
        orgMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ORG_DELETE", "sys_org", String.valueOf(id), org.getOrgCode());
    }
}
