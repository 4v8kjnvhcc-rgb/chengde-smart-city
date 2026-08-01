package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.SysDictItemRequest;
import com.chengde.smartcity.system.dto.SysDictRequest;
import com.chengde.smartcity.system.entity.SysDict;
import com.chengde.smartcity.system.entity.SysDictItem;
import com.chengde.smartcity.system.mapper.SysDictItemMapper;
import com.chengde.smartcity.system.mapper.SysDictMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SysDictService {

    private final SysDictMapper dictMapper;
    private final SysDictItemMapper itemMapper;
    private final AuditService auditService;

    public SysDictService(SysDictMapper dictMapper, SysDictItemMapper itemMapper, AuditService auditService) {
        this.dictMapper = dictMapper;
        this.itemMapper = itemMapper;
        this.auditService = auditService;
    }

    public List<SysDict> listDicts() {
        return dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .orderByAsc(SysDict::getSortOrder)
                .orderByAsc(SysDict::getId));
    }

    public SysDict getDict(Long id) {
        return requireDict(id);
    }

    public SysDict getByCode(String dictCode) {
        if (!StringUtils.hasText(dictCode)) {
            throw new BusinessException(400, "dictCode required");
        }
        SysDict dict = dictMapper.selectOne(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictCode, dictCode.trim()));
        if (dict == null) {
            throw new BusinessException(404, "字典不存在: " + dictCode);
        }
        return dict;
    }

    @Transactional
    public Long createDict(UserPrincipal operator, SysDictRequest req) {
        String code = req.dictCode().trim();
        Long exists = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>().eq(SysDict::getDictCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "字典编码已存在");
        }
        SysDict dict = new SysDict();
        dict.setDictCode(code);
        dict.setDictName(req.dictName().trim());
        dict.setRemark(blankToNull(req.remark()));
        dict.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        dict.setStatus(req.status() == null ? 1 : req.status());
        dictMapper.insert(dict);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_DICT_CREATE", "sys_dict", String.valueOf(dict.getId()), dict.getDictCode());
        return dict.getId();
    }

    @Transactional
    public void updateDict(UserPrincipal operator, Long id, SysDictRequest req) {
        SysDict dict = requireDict(id);
        String code = req.dictCode().trim();
        Long dup = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictCode, code)
                .ne(SysDict::getId, id));
        if (dup != null && dup > 0) {
            throw new BusinessException(400, "字典编码已存在");
        }
        dict.setDictCode(code);
        dict.setDictName(req.dictName().trim());
        dict.setRemark(blankToNull(req.remark()));
        if (req.sortOrder() != null) dict.setSortOrder(req.sortOrder());
        if (req.status() != null) dict.setStatus(req.status());
        dictMapper.updateById(dict);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_DICT_UPDATE", "sys_dict", String.valueOf(id), dict.getDictCode());
    }

    @Transactional
    public void deleteDict(UserPrincipal operator, Long id) {
        SysDict dict = requireDict(id);
        Long itemCount = itemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getDictId, id));
        if (itemCount != null && itemCount > 0) {
            throw new BusinessException(400, "存在字典项，请先删除字典项");
        }
        dictMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_DICT_DELETE", "sys_dict", String.valueOf(id), dict.getDictCode());
    }

    public List<SysDictItem> listItems(Long dictId) {
        requireDict(dictId);
        return itemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictId, dictId)
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByAsc(SysDictItem::getId));
    }

    public List<SysDictItem> listItemsByCode(String dictCode) {
        SysDict dict = getByCode(dictCode);
        return itemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictId, dict.getId())
                .eq(SysDictItem::getStatus, 1)
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByAsc(SysDictItem::getId));
    }

    /** 认证中心兼容：映射为原 ana_platform_config 字段名（含停用项） */
    public List<Map<String, Object>> authConfigsLegacy() {
        SysDict dict = getByCode("AUTH");
        return listItems(dict.getId()).stream().map(this::toLegacyConfig).collect(Collectors.toList());
    }

    public List<Map<String, Object>> systemConfigsLegacy() {
        SysDict dict = getByCode("SYSTEM");
        return listItems(dict.getId()).stream().map(this::toLegacyConfig).collect(Collectors.toList());
    }

    @Transactional
    public Long createItem(UserPrincipal operator, Long dictId, SysDictItemRequest req) {
        requireDict(dictId);
        String key = req.itemKey().trim();
        Long exists = itemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictId, dictId)
                .eq(SysDictItem::getItemKey, key));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "字典项编码已存在");
        }
        SysDictItem item = new SysDictItem();
        item.setDictId(dictId);
        item.setItemKey(key);
        item.setItemValue(req.itemValue() == null ? "" : req.itemValue());
        item.setItemLabel(blankToNull(req.itemLabel()));
        item.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        item.setStatus(req.status() == null ? 1 : req.status());
        item.setRemark(blankToNull(req.remark()));
        itemMapper.insert(item);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_DICT_ITEM_CREATE", "sys_dict_item", String.valueOf(item.getId()), key);
        return item.getId();
    }

    @Transactional
    public void updateItem(UserPrincipal operator, Long itemId, SysDictItemRequest req) {
        SysDictItem item = requireItem(itemId);
        String key = req.itemKey().trim();
        Long dup = itemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictId, item.getDictId())
                .eq(SysDictItem::getItemKey, key)
                .ne(SysDictItem::getId, itemId));
        if (dup != null && dup > 0) {
            throw new BusinessException(400, "字典项编码已存在");
        }
        item.setItemKey(key);
        if (req.itemValue() != null) item.setItemValue(req.itemValue());
        if (req.itemLabel() != null) item.setItemLabel(blankToNull(req.itemLabel()));
        if (req.sortOrder() != null) item.setSortOrder(req.sortOrder());
        if (req.status() != null) item.setStatus(req.status());
        if (req.remark() != null) item.setRemark(blankToNull(req.remark()));
        itemMapper.updateById(item);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_DICT_ITEM_UPDATE", "sys_dict_item", String.valueOf(itemId), key);
    }

    /** 仅更新值（认证中心快捷保存） */
    @Transactional
    public void updateItemValue(UserPrincipal operator, Long itemId, String itemValue) {
        SysDictItem item = requireItem(itemId);
        item.setItemValue(itemValue == null ? "" : itemValue);
        itemMapper.updateById(item);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_DICT_ITEM_VALUE", "sys_dict_item", String.valueOf(itemId), item.getItemKey());
    }

    @Transactional
    public void deleteItem(UserPrincipal operator, Long itemId) {
        SysDictItem item = requireItem(itemId);
        itemMapper.deleteById(itemId);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "SYS_DICT_ITEM_DELETE", "sys_dict_item", String.valueOf(itemId), item.getItemKey());
    }

    private Map<String, Object> toLegacyConfig(SysDictItem item) {
        SysDict dict = dictMapper.selectById(item.getDictId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("configKey", item.getItemKey());
        m.put("configValue", item.getItemValue());
        m.put("configGroup", dict == null ? null : dict.getDictCode());
        m.put("description", item.getItemLabel());
        m.put("status", item.getStatus() != null && item.getStatus() == 1 ? "ACTIVE" : "DISABLED");
        return m;
    }

    private SysDict requireDict(Long id) {
        SysDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw new BusinessException(404, "字典不存在");
        }
        return dict;
    }

    private SysDictItem requireItem(Long id) {
        SysDictItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "字典项不存在");
        }
        return item;
    }

    private static String blankToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }
}
