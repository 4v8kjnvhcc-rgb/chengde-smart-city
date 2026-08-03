package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngAssetCatalogReg;
import com.chengde.smartcity.exchange.entity.IngAssetTag;
import com.chengde.smartcity.exchange.entity.IngBizSystem;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDict;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.entity.IngRegisterAuditLog;
import com.chengde.smartcity.exchange.mapper.IngAssetCatalogRegMapper;
import com.chengde.smartcity.exchange.mapper.IngAssetTagMapper;
import com.chengde.smartcity.exchange.mapper.IngBizSystemMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDictMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.exchange.mapper.IngRegisterAuditLogMapper;
import com.chengde.smartcity.exchange.support.RegisterStatuses;
import com.chengde.smartcity.security.UserPrincipal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RegisterWorkflowService {

    public static final String TYPE_PROJECT = "PROJECT";
    public static final String TYPE_SYSTEM = "SYSTEM";
    public static final String TYPE_DATA_SOURCE = "DATA_SOURCE";
    public static final String TYPE_DICT = "DICT";
    public static final String TYPE_TAG = "TAG";
    public static final String TYPE_CATALOG_REG = "CATALOG_REG";

    private static final Set<String> TYPES = Set.of(
            TYPE_PROJECT, TYPE_SYSTEM, TYPE_DATA_SOURCE, TYPE_DICT, TYPE_TAG, TYPE_CATALOG_REG);

    private final IngProjectMapper projectMapper;
    private final IngBizSystemMapper bizSystemMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final IngDictMapper dictMapper;
    private final IngAssetTagMapper tagMapper;
    private final IngAssetCatalogRegMapper catalogRegMapper;
    private final IngRegisterAuditLogMapper auditLogMapper;

    public RegisterWorkflowService(IngProjectMapper projectMapper,
                                   IngBizSystemMapper bizSystemMapper,
                                   IngDataSourceMapper dataSourceMapper,
                                   IngDictMapper dictMapper,
                                   IngAssetTagMapper tagMapper,
                                   IngAssetCatalogRegMapper catalogRegMapper,
                                   IngRegisterAuditLogMapper auditLogMapper) {
        this.projectMapper = projectMapper;
        this.bizSystemMapper = bizSystemMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.dictMapper = dictMapper;
        this.tagMapper = tagMapper;
        this.catalogRegMapper = catalogRegMapper;
        this.auditLogMapper = auditLogMapper;
    }

    /** 角色只控菜单；能调用登记流程接口即可提交/审核（状态机另行校验） */
    public boolean canSubmit(UserPrincipal operator) {
        return operator != null;
    }

    public boolean canAudit(UserPrincipal operator) {
        return operator != null;
    }

    public void assertEditable(UserPrincipal operator, String objectType, Long objectId) {
        String status = getStatus(objectType, objectId);
        if (!RegisterStatuses.canEdit(status)) {
            throw new BusinessException(400, "当前状态为「" + RegisterStatuses.zh(status) + "」，不可编辑");
        }
        if (RegisterStatuses.PENDING_REVIEW.equals(norm(status))) {
            throw new BusinessException(400, "待审核状态不可编辑");
        }
    }

    public List<IngRegisterAuditLog> listAuditLogs(String objectType, Long objectId) {
        requireType(objectType);
        return auditLogMapper.selectList(new LambdaQueryWrapper<IngRegisterAuditLog>()
                .eq(IngRegisterAuditLog::getObjectType, objectType.trim().toUpperCase(Locale.ROOT))
                .eq(IngRegisterAuditLog::getObjectId, objectId)
                .orderByDesc(IngRegisterAuditLog::getId));
    }

    @Transactional
    public void submit(UserPrincipal operator, String objectType, Long objectId) {
        if (!canSubmit(operator)) {
            throw new BusinessException(403, "无提交权限");
        }
        String from = getStatus(objectType, objectId);
        if (!RegisterStatuses.canSubmit(from)) {
            throw new BusinessException(400, "仅草稿或驳回待提交可提交审核");
        }
        setStatus(objectType, objectId, RegisterStatuses.PENDING_REVIEW, null);
        writeLog(operator, objectType, objectId, "SUBMIT", from, RegisterStatuses.PENDING_REVIEW, null);
    }

    @Transactional
    public void approve(UserPrincipal operator, String objectType, Long objectId, String comment) {
        if (!canAudit(operator)) {
            throw new BusinessException(403, "无审核权限");
        }
        String from = getStatus(objectType, objectId);
        if (!RegisterStatuses.canAudit(from)) {
            throw new BusinessException(400, "仅待审核状态可审核通过");
        }
        setStatus(objectType, objectId, RegisterStatuses.APPROVED, null);
        writeLog(operator, objectType, objectId, "APPROVE", from, RegisterStatuses.APPROVED, comment);
    }

    @Transactional
    public void reject(UserPrincipal operator, String objectType, Long objectId, String reason) {
        if (!canAudit(operator)) {
            throw new BusinessException(403, "无审核权限");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(400, "驳回须填写原因");
        }
        String from = getStatus(objectType, objectId);
        if (!RegisterStatuses.canAudit(from)) {
            throw new BusinessException(400, "仅待审核状态可驳回");
        }
        setStatus(objectType, objectId, RegisterStatuses.REJECTED, reason.trim());
        writeLog(operator, objectType, objectId, "REJECT", from, RegisterStatuses.REJECTED, reason.trim());
    }

    public void writeCreateLog(UserPrincipal operator, String objectType, Long objectId) {
        writeLog(operator, objectType, objectId, "CREATE", null, RegisterStatuses.DRAFT, null);
    }

    private String getStatus(String objectType, Long objectId) {
        String type = requireType(objectType);
        return switch (type) {
            case TYPE_PROJECT -> {
                IngProject p = projectMapper.selectById(objectId);
                if (p == null) throw new BusinessException(404, "项目不存在");
                yield p.getRegisterStatus();
            }
            case TYPE_SYSTEM -> {
                IngBizSystem s = bizSystemMapper.selectById(objectId);
                if (s == null) throw new BusinessException(404, "系统不存在");
                yield s.getRegisterStatus();
            }
            case TYPE_DATA_SOURCE -> {
                IngDataSource d = dataSourceMapper.selectById(objectId);
                if (d == null) throw new BusinessException(404, "数据库不存在");
                yield d.getRegisterStatus();
            }
            case TYPE_DICT -> {
                IngDict d = dictMapper.selectById(objectId);
                if (d == null) throw new BusinessException(404, "字典不存在");
                yield d.getRegisterStatus();
            }
            case TYPE_TAG -> {
                IngAssetTag t = tagMapper.selectById(objectId);
                if (t == null) throw new BusinessException(404, "标签不存在");
                yield t.getRegisterStatus();
            }
            case TYPE_CATALOG_REG -> {
                IngAssetCatalogReg c = catalogRegMapper.selectById(objectId);
                if (c == null) throw new BusinessException(404, "资产目录登记不存在");
                yield c.getStatus();
            }
            default -> throw new BusinessException(400, "不支持的对象类型");
        };
    }

    private void setStatus(String objectType, Long objectId, String toStatus, String rejectReason) {
        String type = requireType(objectType);
        switch (type) {
            case TYPE_PROJECT -> {
                IngProject p = projectMapper.selectById(objectId);
                if (p == null) throw new BusinessException(404, "项目不存在");
                p.setRegisterStatus(toStatus);
                p.setRejectReason(rejectReason);
                projectMapper.updateById(p);
            }
            case TYPE_SYSTEM -> {
                IngBizSystem s = bizSystemMapper.selectById(objectId);
                if (s == null) throw new BusinessException(404, "系统不存在");
                s.setRegisterStatus(toStatus);
                s.setRejectReason(rejectReason);
                bizSystemMapper.updateById(s);
            }
            case TYPE_DATA_SOURCE -> {
                IngDataSource d = dataSourceMapper.selectById(objectId);
                if (d == null) throw new BusinessException(404, "数据库不存在");
                d.setRegisterStatus(toStatus);
                d.setRejectReason(rejectReason);
                dataSourceMapper.updateById(d);
            }
            case TYPE_DICT -> {
                IngDict d = dictMapper.selectById(objectId);
                if (d == null) throw new BusinessException(404, "字典不存在");
                d.setRegisterStatus(toStatus);
                d.setRejectReason(rejectReason);
                dictMapper.updateById(d);
            }
            case TYPE_TAG -> {
                IngAssetTag t = tagMapper.selectById(objectId);
                if (t == null) throw new BusinessException(404, "标签不存在");
                t.setRegisterStatus(toStatus);
                t.setRejectReason(rejectReason);
                tagMapper.updateById(t);
            }
            case TYPE_CATALOG_REG -> {
                IngAssetCatalogReg c = catalogRegMapper.selectById(objectId);
                if (c == null) throw new BusinessException(404, "资产目录登记不存在");
                c.setStatus(toStatus);
                c.setRejectReason(rejectReason);
                catalogRegMapper.updateById(c);
            }
            default -> throw new BusinessException(400, "不支持的对象类型");
        }
    }

    private void writeLog(UserPrincipal operator, String objectType, Long objectId,
                          String action, String from, String to, String comment) {
        IngRegisterAuditLog log = new IngRegisterAuditLog();
        log.setObjectType(requireType(objectType));
        log.setObjectId(objectId);
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setCommentText(comment);
        log.setOperatorId(operator.getUserId());
        log.setOperatorName(operator.getUsername());
        auditLogMapper.insert(log);
    }

    private static String requireType(String objectType) {
        if (!StringUtils.hasText(objectType)) {
            throw new BusinessException(400, "objectType 必填");
        }
        String t = objectType.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new BusinessException(400, "不支持的 objectType: " + objectType);
        }
        return t;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    /** 解析前端 body 中的 objectType/id */
    public static Map.Entry<String, Long> parseTarget(Map<String, Object> body) {
        if (body == null || body.get("objectType") == null || body.get("objectId") == null) {
            throw new BusinessException(400, "objectType / objectId 必填");
        }
        Long id = Long.valueOf(String.valueOf(body.get("objectId")));
        return Map.entry(String.valueOf(body.get("objectType")), id);
    }
}
