package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.ClusterAccountRequest;
import com.chengde.smartcity.system.entity.SysClusterAccount;
import com.chengde.smartcity.system.mapper.SysClusterAccountMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClusterAccountService {

    private static final Logger log = LoggerFactory.getLogger(ClusterAccountService.class);

    private final SysClusterAccountMapper mapper;
    private final IngProjectMapper projectMapper;
    private final AuditService auditService;

    public ClusterAccountService(SysClusterAccountMapper mapper,
                                 IngProjectMapper projectMapper,
                                 AuditService auditService) {
        this.mapper = mapper;
        this.projectMapper = projectMapper;
        this.auditService = auditService;
    }

    public List<SysClusterAccount> list(String keyword) {
        LambdaQueryWrapper<SysClusterAccount> q = new LambdaQueryWrapper<SysClusterAccount>()
                .orderByDesc(SysClusterAccount::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(SysClusterAccount::getClusterCode, kw)
                    .or().like(SysClusterAccount::getClusterName, kw)
                    .or().like(SysClusterAccount::getAccountName, kw)
                    .or().like(SysClusterAccount::getEndpoint, kw));
        }
        return mapper.selectList(q);
    }

    public SysClusterAccount get(Long id) {
        return require(id);
    }

    @Transactional
    public Long create(UserPrincipal operator, ClusterAccountRequest req) {
        String code = req.getClusterCode().trim();
        Long exists = mapper.selectCount(new LambdaQueryWrapper<SysClusterAccount>()
                .eq(SysClusterAccount::getClusterCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "集群编码已存在");
        }
        SysClusterAccount row = new SysClusterAccount();
        apply(row, req, true);
        mapper.insert(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_CLUSTER_CREATE", "sys_cluster_account", String.valueOf(row.getId()), row.getClusterCode());
        log.info("cluster account created id={} code={}", row.getId(), row.getClusterCode());
        return row.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, ClusterAccountRequest req) {
        SysClusterAccount row = require(id);
        String code = req.getClusterCode().trim();
        Long dup = mapper.selectCount(new LambdaQueryWrapper<SysClusterAccount>()
                .eq(SysClusterAccount::getClusterCode, code)
                .ne(SysClusterAccount::getId, id));
        if (dup != null && dup > 0) {
            throw new BusinessException(400, "集群编码已存在");
        }
        apply(row, req, false);
        mapper.updateById(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_CLUSTER_UPDATE", "sys_cluster_account", String.valueOf(id), row.getClusterCode());
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        SysClusterAccount row = require(id);
        Long used = projectMapper.selectCount(new LambdaQueryWrapper<IngProject>()
                .eq(IngProject::getClusterAccountId, id));
        if (used != null && used > 0) {
            throw new BusinessException(400, "该集群账号已被 " + used + " 个项目绑定，无法删除");
        }
        mapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_CLUSTER_DELETE", "sys_cluster_account", String.valueOf(id), row.getClusterCode());
    }

    private void apply(SysClusterAccount row, ClusterAccountRequest req, boolean creating) {
        row.setClusterCode(req.getClusterCode().trim());
        row.setClusterName(req.getClusterName().trim());
        // 账号/密码已从前端表单移除；保留字段兼容旧数据，未传时置空且不覆盖已有密码
        if (req.getAccountName() != null && StringUtils.hasText(req.getAccountName())) {
            row.setAccountName(req.getAccountName().trim());
        } else if (creating) {
            row.setAccountName("");
        }
        if (creating) {
            row.setAccountPassword(blankToNull(req.getAccountPassword()));
        } else if (req.getAccountPassword() != null && !req.getAccountPassword().isBlank()) {
            row.setAccountPassword(req.getAccountPassword().trim());
        }
        row.setEndpoint(blankToNull(req.getEndpoint()));
        row.setRemark(blankToNull(req.getRemark()));
        row.setStatus(req.getStatus() != null && req.getStatus() == 0 ? 0 : 1);
    }

    private SysClusterAccount require(Long id) {
        if (id == null) {
            throw new BusinessException(400, "id required");
        }
        SysClusterAccount row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "集群账号不存在");
        }
        return row;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
