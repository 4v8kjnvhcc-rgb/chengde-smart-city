package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysExecCycle;
import com.chengde.smartcity.system.mapper.SysExecCycleMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecCycleService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysExecCycleMapper mapper;
    private final AuditService auditService;

    public ExecCycleService(SysExecCycleMapper mapper, AuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    public List<SysExecCycle> list(String status, String keyword) {
        LambdaQueryWrapper<SysExecCycle> q = new LambdaQueryWrapper<SysExecCycle>()
                .orderByAsc(SysExecCycle::getSortOrder)
                .orderByAsc(SysExecCycle::getId);
        if (status != null && !status.isBlank()) {
            q.eq(SysExecCycle::getStatus, status.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(SysExecCycle::getCycleCode, kw)
                    .or().like(SysExecCycle::getCycleName, kw)
                    .or().like(SysExecCycle::getCronExpr, kw));
        }
        return mapper.selectList(q);
    }

    public SysExecCycle get(Long id) {
        SysExecCycle row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "执行周期不存在");
        }
        return row;
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        String name = required(body.get("cycleName"), "周期名称");
        String cron = normalizeAndValidateCron(required(body.get("cronExpr"), "Cron表达式"));
        SysExecCycle row = new SysExecCycle();
        row.setCycleCode(str(body.get("cycleCode"), "CYCLE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        ensureUniqueCode(row.getCycleCode(), null);
        row.setCycleName(name);
        row.setCronExpr(cron);
        row.setDescription(str(body.get("description"), null));
        row.setStatus(str(body.get("status"), "ACTIVE").toUpperCase());
        row.setSortOrder(intVal(body.get("sortOrder"), 100));
        row.setCreatedBy(operator == null ? null : operator.getUsername());
        mapper.insert(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "EXEC_CYCLE_CREATE", "sys_exec_cycle", String.valueOf(row.getId()), row.getCycleName());
        return row.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        SysExecCycle row = get(id);
        if (body.get("cycleCode") != null && !String.valueOf(body.get("cycleCode")).isBlank()) {
            String code = String.valueOf(body.get("cycleCode")).trim();
            ensureUniqueCode(code, id);
            row.setCycleCode(code);
        }
        if (body.get("cycleName") != null) {
            row.setCycleName(required(body.get("cycleName"), "周期名称"));
        }
        if (body.get("cronExpr") != null) {
            row.setCronExpr(normalizeAndValidateCron(required(body.get("cronExpr"), "Cron表达式")));
        }
        if (body.containsKey("description")) {
            row.setDescription(str(body.get("description"), null));
        }
        if (body.get("status") != null) {
            row.setStatus(String.valueOf(body.get("status")).trim().toUpperCase());
        }
        if (body.get("sortOrder") != null) {
            row.setSortOrder(intVal(body.get("sortOrder"), row.getSortOrder()));
        }
        mapper.updateById(row);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "EXEC_CYCLE_UPDATE", "sys_exec_cycle", String.valueOf(id), row.getCycleName());
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        SysExecCycle row = get(id);
        mapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "EXEC_CYCLE_DELETE", "sys_exec_cycle", String.valueOf(id), row.getCycleName());
    }

    public Map<String, Object> preview(String cronExpr, Integer count) {
        String cron = normalizeAndValidateCron(required(cronExpr, "Cron表达式"));
        int n = count == null || count < 1 ? 10 : Math.min(count, 20);
        List<String> next = new ArrayList<>();
        String[] parts = cron.split("\\s+");
        String forParse = parts.length >= 6
                ? String.join(" ", parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
                : cron;
        CronExpression expr = CronExpression.parse(forParse);
        LocalDateTime cursor = LocalDateTime.now(ZoneId.systemDefault());
        for (int i = 0; i < n; i++) {
            LocalDateTime nextAt = expr.next(cursor);
            if (nextAt == null) {
                break;
            }
            next.add(FMT.format(nextAt));
            cursor = nextAt;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cronExpr", cron);
        out.put("nextRuns", next);
        return out;
    }

    private void ensureUniqueCode(String code, Long excludeId) {
        LambdaQueryWrapper<SysExecCycle> q = new LambdaQueryWrapper<SysExecCycle>()
                .eq(SysExecCycle::getCycleCode, code);
        if (excludeId != null) {
            q.ne(SysExecCycle::getId, excludeId);
        }
        if (mapper.selectCount(q) > 0) {
            throw new BusinessException(400, "周期编码已存在");
        }
    }

    private String normalizeAndValidateCron(String raw) {
        String cron = raw.trim();
        // 兼容 5 段（分 时 日 月 周）→ 补秒位
        String[] parts = cron.split("\\s+");
        if (parts.length == 5) {
            cron = "0 " + cron;
            parts = cron.split("\\s+");
        }
        if (parts.length < 6 || parts.length > 7) {
            throw new BusinessException(400, "Cron 须为 6 或 7 段（秒 分 时 日 月 周 [年]）");
        }
        // Spring CronExpression 仅支持 6 段；年字段仅作存储展示，校验时忽略
        String forParse = parts.length == 7
                ? String.join(" ", parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
                : cron;
        try {
            CronExpression.parse(forParse);
        } catch (Exception ex) {
            throw new BusinessException(400, "Cron 表达式非法: " + ex.getMessage());
        }
        return cron;
    }

    private static String required(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException(400, field + " required");
        }
        return String.valueOf(v).trim();
    }

    private static String str(Object v, String def) {
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v).trim();
    }

    private static int intVal(Object v, int def) {
        if (v == null || String.valueOf(v).isBlank()) {
            return def;
        }
        return Integer.parseInt(String.valueOf(v));
    }
}
