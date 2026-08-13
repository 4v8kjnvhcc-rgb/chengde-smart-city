package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.SysErrorLogReportRequest;
import com.chengde.smartcity.system.entity.SysErrorLog;
import com.chengde.smartcity.system.mapper.SysErrorLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class SysErrorLogService {

    private static final Logger log = LoggerFactory.getLogger(SysErrorLogService.class);
    private static final int MAX_MESSAGE = 1000;
    private static final int MAX_STACK = 16000;

    private final SysErrorLogMapper sysErrorLogMapper;

    public SysErrorLogService(SysErrorLogMapper sysErrorLogMapper) {
        this.sysErrorLogMapper = sysErrorLogMapper;
    }

    public Page<SysErrorLog> page(int page, int size, String source, String moduleCode, String level,
                                  String keyword, LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<SysErrorLog> q = new LambdaQueryWrapper<SysErrorLog>()
                .orderByDesc(SysErrorLog::getOccurredAt)
                .orderByDesc(SysErrorLog::getId);
        if (source != null && !source.isBlank()) {
            q.eq(SysErrorLog::getSource, source.trim().toUpperCase(Locale.ROOT));
        }
        if (moduleCode != null && !moduleCode.isBlank()) {
            q.like(SysErrorLog::getModuleCode, moduleCode.trim());
        }
        if (level != null && !level.isBlank()) {
            q.eq(SysErrorLog::getLevel, level.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(SysErrorLog::getMessage, kw)
                    .or().like(SysErrorLog::getErrorType, kw)
                    .or().like(SysErrorLog::getModuleName, kw)
                    .or().like(SysErrorLog::getRequestUri, kw));
        }
        if (from != null) {
            q.ge(SysErrorLog::getOccurredAt, from);
        }
        if (to != null) {
            q.le(SysErrorLog::getOccurredAt, to);
        }
        return sysErrorLogMapper.selectPage(new Page<>(page, size), q);
    }

    public SysErrorLog getById(Long id) {
        return sysErrorLogMapper.selectById(id);
    }

    public Long report(SysErrorLogReportRequest req, UserPrincipal principal, HttpServletRequest request) {
        SysErrorLog row = new SysErrorLog();
        String source = normalizeSource(req.getSource());
        row.setSource(source);
        row.setModuleCode(trimTo(req.getModuleCode(), 64));
        row.setModuleName(trimTo(req.getModuleName(), 128));
        row.setLevel(normalizeLevel(req.getLevel()));
        row.setErrorCode(trimTo(req.getErrorCode(), 64));
        row.setErrorType(trimTo(req.getErrorType(), 128));
        row.setMessage(trimTo(req.getMessage() == null || req.getMessage().isBlank()
                ? "(empty)" : req.getMessage(), MAX_MESSAGE));
        row.setStackTrace(trimTo(req.getStackTrace(), MAX_STACK));
        row.setRequestUri(trimTo(req.getRequestUri(), 512));
        row.setHttpMethod(trimTo(req.getHttpMethod(), 16));
        row.setHttpStatus(req.getHttpStatus());
        row.setPageUrl(trimTo(req.getPageUrl(), 1024));
        row.setTraceId(trimTo(req.getTraceId(), 64));
        row.setAppVersion(trimTo(req.getAppVersion(), 32));
        row.setEnv(trimTo(req.getEnv(), 16));
        row.setExtraJson(req.getExtraJson());
        row.setOccurredAt(parseOccurredAt(req.getOccurredAt()));
        row.setCreatedAt(LocalDateTime.now());
        fillActor(row, principal, request);
        sysErrorLogMapper.insert(row);
        return row.getId();
    }

    /** 后端异常落库；失败仅打本地日志，不影响主流程 */
    public void recordBackendException(Throwable ex, String moduleCode, String moduleName,
                                       Integer httpStatus, String errorCode) {
        try {
            SysErrorLog row = new SysErrorLog();
            row.setSource("BACKEND");
            row.setModuleCode(trimTo(moduleCode, 64));
            row.setModuleName(trimTo(moduleName, 128));
            row.setLevel("ERROR");
            row.setErrorCode(trimTo(errorCode, 64));
            row.setErrorType(trimTo(ex == null ? null : ex.getClass().getName(), 128));
            String msg = ex == null ? "unknown" : (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            row.setMessage(trimTo(msg, MAX_MESSAGE));
            row.setStackTrace(trimTo(stackOf(ex), MAX_STACK));
            row.setHttpStatus(httpStatus);
            row.setOccurredAt(LocalDateTime.now());
            row.setCreatedAt(LocalDateTime.now());

            UserPrincipal principal = currentPrincipal();
            HttpServletRequest request = currentRequest();
            if (request != null) {
                row.setRequestUri(trimTo(request.getRequestURI(), 512));
                row.setHttpMethod(trimTo(request.getMethod(), 16));
                String trace = request.getHeader("X-Trace-Id");
                if (trace == null || trace.isBlank()) {
                    trace = request.getHeader("X-Request-Id");
                }
                row.setTraceId(trimTo(trace, 64));
            }
            fillActor(row, principal, request);
            sysErrorLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("persist sys_error_log failed: {}", e.getMessage());
        }
    }

    private void fillActor(SysErrorLog row, UserPrincipal principal, HttpServletRequest request) {
        if (principal != null) {
            row.setUserId(principal.getUserId());
            row.setUsername(principal.getUsername());
            row.setOrgId(principal.getOrgId());
        }
        if (request != null) {
            if (row.getClientIp() == null) {
                row.setClientIp(trimTo(clientIp(request), 64));
            }
            if (row.getUserAgent() == null) {
                row.setUserAgent(trimTo(request.getHeader("User-Agent"), 512));
            }
        }
    }

    private static UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        return null;
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "FRONTEND";
        }
        String s = source.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "FRONTEND", "BACKEND", "JOB", "GATEWAY" -> s;
            default -> "FRONTEND";
        };
    }

    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return "ERROR";
        }
        String s = level.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "DEBUG", "INFO", "WARN", "ERROR", "FATAL" -> s;
            default -> "ERROR";
        };
    }

    private static LocalDateTime parseOccurredAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        String v = raw.trim();
        try {
            return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now();
        }
    }

    private static String stackOf(Throwable ex) {
        if (ex == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}
