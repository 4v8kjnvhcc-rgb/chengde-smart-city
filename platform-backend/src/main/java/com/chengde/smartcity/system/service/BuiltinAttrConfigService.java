package com.chengde.smartcity.system.service;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysBuiltinAttrConfig;
import com.chengde.smartcity.system.mapper.SysBuiltinAttrConfigMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 内置属性管理：全局控制数据项各属性维度是否可编辑 */
@Service
public class BuiltinAttrConfigService {

    private static final Logger log = LoggerFactory.getLogger(BuiltinAttrConfigService.class);
    private static final long ROW_ID = 1L;

    private final SysBuiltinAttrConfigMapper mapper;

    public BuiltinAttrConfigService(SysBuiltinAttrConfigMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Boolean> getControl() {
        return toMap(ensureRow());
    }

    public boolean isEditable(String attrKey) {
        Map<String, Boolean> m = getControl();
        return !Boolean.FALSE.equals(m.get(attrKey));
    }

    @Transactional
    public void save(UserPrincipal operator, Map<String, Object> body) {
        SysBuiltinAttrConfig row = ensureRow();
        row.setColumnCode(flag(body, "columnCode", row.getColumnCode()));
        row.setColumnName(flag(body, "columnName", row.getColumnName()));
        row.setDataType(flag(body, "dataType", row.getDataType()));
        row.setLengthVal(flag(body, "lengthVal", row.getLengthVal()));
        row.setComponentType(flag(body, "componentType", row.getComponentType()));
        row.setNullableFlag(flag(body, "nullableFlag", row.getNullableFlag()));
        row.setUpdatedAt(LocalDateTime.now());
        row.setUpdatedBy(operator != null ? operator.getUsername() : null);
        mapper.updateById(row);
        log.info("builtin attr config updated by={}", operator != null ? operator.getUsername() : null);
    }

    private SysBuiltinAttrConfig ensureRow() {
        SysBuiltinAttrConfig row = mapper.selectById(ROW_ID);
        if (row != null) {
            return row;
        }
        row = new SysBuiltinAttrConfig();
        row.setId(ROW_ID);
        row.setColumnCode(1);
        row.setColumnName(1);
        row.setDataType(1);
        row.setLengthVal(1);
        row.setComponentType(1);
        row.setNullableFlag(1);
        row.setUpdatedAt(LocalDateTime.now());
        row.setUpdatedBy("system");
        mapper.insert(row);
        return row;
    }

    private static Map<String, Boolean> toMap(SysBuiltinAttrConfig row) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("columnCode", on(row.getColumnCode()));
        m.put("columnName", on(row.getColumnName()));
        m.put("dataType", on(row.getDataType()));
        m.put("lengthVal", on(row.getLengthVal()));
        m.put("componentType", on(row.getComponentType()));
        m.put("nullableFlag", on(row.getNullableFlag()));
        return m;
    }

    private static boolean on(Integer v) {
        return v == null || v == 1;
    }

    private static Integer flag(Map<String, Object> body, String key, Integer fallback) {
        if (body == null || !body.containsKey(key)) {
            return fallback == null ? 1 : fallback;
        }
        Object v = body.get(key);
        if (v == null) {
            return 1;
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        String s = String.valueOf(v).trim();
        if ("0".equals(s) || "false".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s)) {
            return 0;
        }
        return 1;
    }

    public void assertEditable(String attrKey, String label) {
        if (!isEditable(attrKey)) {
            throw new BusinessException(400, "属性「" + label + "」当前不可编辑，请在「内置属性管理」中开启");
        }
    }
}
