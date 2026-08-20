package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovQualityFixSla;
import com.chengde.smartcity.masterdata.mapper.GovQualityFixSlaMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityFixSlaService {

    private final GovQualityFixSlaMapper mapper;

    public QualityFixSlaService(GovQualityFixSlaMapper mapper) {
        this.mapper = mapper;
    }

    public List<GovQualityFixSla> list(String severity, String status) {
        LambdaQueryWrapper<GovQualityFixSla> q = new LambdaQueryWrapper<GovQualityFixSla>()
                .orderByAsc(GovQualityFixSla::getSortNo)
                .orderByDesc(GovQualityFixSla::getId);
        if (severity != null && !severity.isBlank()) {
            q.eq(GovQualityFixSla::getSeverity, severity);
        }
        if (status != null && !status.isBlank()) {
            q.eq(GovQualityFixSla::getStatus, status);
        }
        return mapper.selectList(q);
    }

    @Transactional
    public Long create(UserPrincipal principal, Map<String, Object> body) {
        GovQualityFixSla row = new GovQualityFixSla();
        apply(row, body, true);
        row.setCreatedBy(principal == null ? null : principal.getUsername());
        row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
        return row.getId();
    }

    @Transactional
    public void update(UserPrincipal principal, Long id, Map<String, Object> body) {
        GovQualityFixSla row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "整改时间要求不存在");
        }
        apply(row, body, false);
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BusinessException(404, "整改时间要求不存在");
        }
        mapper.deleteById(id);
    }

    private void apply(GovQualityFixSla row, Map<String, Object> body, boolean creating) {
        if (body.containsKey("ruleCode") || creating) {
            row.setRuleCode(str(body.get("ruleCode"), null));
        }
        if (body.containsKey("ruleName") || creating) {
            row.setRuleName(str(body.get("ruleName"), creating ? "未命名要求" : row.getRuleName()));
        }
        if (body.containsKey("severity") || creating) {
            row.setSeverity(str(body.get("severity"), "IMPORTANT"));
        }
        if (body.containsKey("fixDays") || creating) {
            Integer days = toInt(body.get("fixDays"), creating ? 7 : row.getFixDays());
            if (days == null || days < 1 || days > 3650) {
                throw new BusinessException(400, "整改时限须为 1～3650 天");
            }
            row.setFixDays(days);
        }
        if (body.containsKey("overdueAction") || creating) {
            row.setOverdueAction(str(body.get("overdueAction"), "ALERT"));
        }
        if (body.containsKey("notifyRoles") || creating) {
            row.setNotifyRoles(str(body.get("notifyRoles"), null));
        }
        if (body.containsKey("remark")) {
            row.setRemark(str(body.get("remark"), null));
        }
        if (body.containsKey("sortNo") || creating) {
            row.setSortNo(toInt(body.get("sortNo"), 0));
        }
        if (body.containsKey("status") || creating) {
            row.setStatus(str(body.get("status"), "ACTIVE"));
        }
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static Integer toInt(Object v, Integer def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        return Integer.valueOf(String.valueOf(v));
    }
}
