package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.MenuTreeNode;
import com.chengde.smartcity.system.dto.RegisterMenuUpsertRequest;
import com.chengde.smartcity.system.entity.SysMenu;
import com.chengde.smartcity.system.mapper.SysMenuMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {

    /** 数据资产登记管理根节点 */
    public static final long REGISTER_ROOT_ID = 7000L;

    /** 种子菜单不可删除（含根与登记侧栏初始化项） */
    private static final Set<Long> REGISTER_SEED_IDS = Set.of(
            7000L, 7001L, 7002L, 7003L, 7004L, 7005L, 7006L, 7007L, 7008L, 7009L,
            7010L, 7011L, 7012L, 7013L, 7014L, 7015L);

    private final SysMenuMapper menuMapper;
    private final JdbcTemplate jdbcTemplate;

    public MenuService(SysMenuMapper menuMapper, JdbcTemplate jdbcTemplate) {
        this.menuMapper = menuMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MenuTreeNode> treeForUser(Long userId) {
        return buildTree(menuMapper.findMenusByUserId(userId));
    }

    /** 系统管理员不受角色菜单勾选削减，始终返回全量交付菜单树。 */
    public List<MenuTreeNode> treeForUser(UserPrincipal principal) {
        if (principal != null && principal.isSystemAdmin()) {
            List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getStatus, 1)
                    .orderByAsc(SysMenu::getSortOrder)
                    .orderByAsc(SysMenu::getId));
            return buildTree(all);
        }
        return treeForUser(principal.getUserId());
    }

    private List<MenuTreeNode> buildTree(List<SysMenu> source) {
        List<SysMenu> menus = source.stream()
                .filter(m -> m.getMenuType() == null || m.getMenuType() != 3)
                .filter(m -> m.getVisible() == null || m.getVisible() == 1)
                .filter(this::isDeliveryMenu)
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() == null ? 0 : m.getSortOrder()))
                .toList();
        Map<Long, MenuTreeNode> index = new HashMap<>();
        List<MenuTreeNode> roots = new ArrayList<>();
        for (SysMenu m : menus) {
            index.put(m.getId(), toNode(m));
        }
        for (SysMenu m : menus) {
            MenuTreeNode node = index.get(m.getId());
            if (m.getParentId() == null || m.getParentId() == 0) {
                roots.add(node);
            } else if (index.containsKey(m.getParentId())) {
                index.get(m.getParentId()).children().add(node);
            }
        }
        return roots;
    }

    /**
     * 角色/组织菜单授权用：仅启用且属交付界面的菜单（排除 D05 catalog、已并入旧入口）。
     */
    public List<SysMenu> listAll() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getStatus, 1)
                        .orderByAsc(SysMenu::getSortOrder)
                        .orderByAsc(SysMenu::getId))
                .stream()
                .filter(this::isDeliveryMenu)
                .toList();
    }

    /** 菜单管理页：含隐藏项与按钮，排除已停用与 D05 catalog */
    public List<SysMenu> listForManage() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .ne(SysMenu::getStatus, 0)
                        .orderByAsc(SysMenu::getSortOrder)
                        .orderByAsc(SysMenu::getId))
                .stream()
                .filter(this::isDeliveryMenu)
                .toList();
    }

    @Transactional
    public Long createMenu(RegisterMenuUpsertRequest req) {
        validateType(req.getMenuType());
        Long parentId = req.getParentId() == null ? 0L : req.getParentId();
        if (parentId != 0L) {
            SysMenu parent = menuMapper.selectById(parentId);
            if (parent == null || (parent.getStatus() != null && parent.getStatus() == 0)) {
                throw new BusinessException(400, "父节点不存在");
            }
        }
        SysMenu m = new SysMenu();
        applyUpsert(m, req, true);
        m.setParentId(parentId);
        m.setStatus(1);
        if (m.getIntegrationType() == null || m.getIntegrationType().isBlank()) {
            m.setIntegrationType("self");
        }
        if (m.getPermission() == null || m.getPermission().isBlank()) {
            m.setPermission("system:menu:custom:" + sanitizeSlug(req.getRouteName()));
        }
        menuMapper.insert(m);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (role_id, menu_id) "
                        + "SELECT 1, ? FROM DUAL "
                        + "WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = ?)",
                m.getId(), m.getId());
        return m.getId();
    }

    @Transactional
    public void updateMenu(Long id, RegisterMenuUpsertRequest req) {
        SysMenu existing = menuMapper.selectById(id);
        if (existing == null || (existing.getStatus() != null && existing.getStatus() == 0)) {
            throw new BusinessException(404, "菜单不存在");
        }
        validateType(req.getMenuType());
        Long parentId = req.getParentId() == null ? 0L : req.getParentId();
        if (id.equals(parentId)) {
            throw new BusinessException(400, "父节点不能是自身");
        }
        if (parentId != 0L) {
            if (collectSubtreeIds(id).contains(parentId)) {
                throw new BusinessException(400, "不能将节点移动到其子节点下");
            }
            SysMenu parent = menuMapper.selectById(parentId);
            if (parent == null || (parent.getStatus() != null && parent.getStatus() == 0)) {
                throw new BusinessException(400, "父节点不存在");
            }
        }
        String keepPermission = existing.getPermission();
        applyUpsert(existing, req, false);
        existing.setParentId(parentId);
        if (req.getPermission() == null || req.getPermission().isBlank()) {
            existing.setPermission(keepPermission);
        }
        menuMapper.updateById(existing);
    }

    @Transactional
    public void deleteMenus(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请选择要删除的菜单");
        }
        for (Long id : ids) {
            if (id == null) continue;
            if (id == 1L) {
                throw new BusinessException(400, "根菜单不可删除");
            }
            SysMenu m = menuMapper.selectById(id);
            if (m == null || (m.getStatus() != null && m.getStatus() == 0)) {
                throw new BusinessException(404, "菜单不存在: " + id);
            }
            long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getParentId, id)
                    .ne(SysMenu::getStatus, 0));
            if (childCount > 0) {
                throw new BusinessException(400, "请先删除「" + m.getMenuName() + "」下的子菜单");
            }
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id = ?", id);
            menuMapper.deleteById(id);
        }
    }

    /** 同级上移/下移：direction=-1 上移，1 下移 */
    @Transactional
    public void moveSort(Long id, int direction) {
        if (direction != -1 && direction != 1) {
            throw new BusinessException(400, "direction 只能为 -1 或 1");
        }
        SysMenu current = menuMapper.selectById(id);
        if (current == null || (current.getStatus() != null && current.getStatus() == 0)) {
            throw new BusinessException(404, "菜单不存在");
        }
        Long parentId = current.getParentId() == null ? 0L : current.getParentId();
        List<SysMenu> siblings = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, parentId)
                .ne(SysMenu::getStatus, 0)
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
        int idx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(id)) {
                idx = i;
                break;
            }
        }
        int swapIdx = idx + direction;
        if (idx < 0 || swapIdx < 0 || swapIdx >= siblings.size()) {
            return;
        }
        SysMenu other = siblings.get(swapIdx);
        Integer a = current.getSortOrder() == null ? 0 : current.getSortOrder();
        Integer b = other.getSortOrder() == null ? 0 : other.getSortOrder();
        if (a.equals(b)) {
            current.setSortOrder(a + direction);
        } else {
            current.setSortOrder(b);
            other.setSortOrder(a);
            menuMapper.updateById(other);
        }
        menuMapper.updateById(current);
    }

    public List<String> allActivePermissions() {
        // 含 status=0 的运维入口（如角色/用户/机构管理被侧栏隐藏但仍有权限码）
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .isNotNull(SysMenu::getPermission)
                        .ne(SysMenu::getPermission, ""))
                .stream()
                .filter(this::isDeliveryMenu)
                .map(SysMenu::getPermission)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .toList();
    }

    /** 与前端 menu.ts isD05MenuExcluded 对齐 */
    private boolean isDeliveryMenu(SysMenu m) {
        String name = m.getMenuName() == null ? "" : m.getMenuName();
        if (name.contains("D05") || name.contains("已并入")) {
            return false;
        }
        if ("catalog".equalsIgnoreCase(m.getIntegrationType())) {
            return false;
        }
        String path = m.getPath() == null ? "" : m.getPath();
        if ("/catalog".equals(path) || path.startsWith("/catalog/") || path.startsWith("/modules/")) {
            return false;
        }
        return true;
    }

    private MenuTreeNode toNode(SysMenu m) {
        return new MenuTreeNode(
                m.getId(),
                m.getParentId(),
                m.getMenuName(),
                m.getMenuType(),
                m.getPath(),
                m.getComponent(),
                m.getPermission(),
                m.getIcon(),
                m.getMCode(),
                m.getIntegrationType(),
                new ArrayList<>()
        );
    }

    /** 登记系统菜单子树（含根 7000），扁平列表，含隐藏项，供菜单管理页。 */
    public List<SysMenu> listRegisterScope() {
        Set<Long> ids = collectSubtreeIds(REGISTER_ROOT_ID);
        if (ids.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .in(SysMenu::getId, ids)
                        .ne(SysMenu::getStatus, 0)
                        .orderByAsc(SysMenu::getSortOrder)
                        .orderByAsc(SysMenu::getId));
    }

    @Transactional
    public Long createRegisterMenu(RegisterMenuUpsertRequest req) {
        assertParentInRegisterScope(req.getParentId());
        validateType(req.getMenuType());
        SysMenu m = new SysMenu();
        applyUpsert(m, req, true);
        m.setStatus(1);
        m.setIntegrationType("hub");
        // 新增一律走自定义权限码，便于删除与侧栏识别
        String slug = sanitizeSlug(req.getRouteName());
        m.setPermission("hub:ingestion:register:custom:" + slug);
        menuMapper.insert(m);
        // 赋权系统管理员角色，侧栏立即可见
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (role_id, menu_id) "
                        + "SELECT 1, ? FROM DUAL "
                        + "WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = ?)",
                m.getId(), m.getId());
        return m.getId();
    }

    @Transactional
    public void updateRegisterMenu(Long id, RegisterMenuUpsertRequest req) {
        SysMenu existing = requireInRegisterScope(id);
        assertParentInRegisterScope(req.getParentId());
        if (id.equals(req.getParentId())) {
            throw new BusinessException(400, "父节点不能是自身");
        }
        if (collectSubtreeIds(id).contains(req.getParentId())) {
            throw new BusinessException(400, "不能将节点移动到其子节点下");
        }
        validateType(req.getMenuType());
        String keepPermission = existing.getPermission();
        String keepMCode = existing.getMCode();
        String keepIntegration = existing.getIntegrationType();
        applyUpsert(existing, req, false);
        if (REGISTER_SEED_IDS.contains(id)) {
            existing.setPermission(keepPermission);
            existing.setMCode(keepMCode);
            existing.setIntegrationType(keepIntegration);
        }
        menuMapper.updateById(existing);
    }

    @Transactional
    public void deleteRegisterMenus(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请选择要删除的菜单");
        }
        Set<Long> scope = collectSubtreeIds(REGISTER_ROOT_ID);
        for (Long id : ids) {
            if (id == null) continue;
            if (!scope.contains(id)) {
                throw new BusinessException(403, "只能删除数据资产登记管理系统下的菜单");
            }
            if (isBuiltinRegisterMenu(id)) {
                throw new BusinessException(400, "系统初始化菜单不可删除");
            }
            long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getParentId, id)
                    .ne(SysMenu::getStatus, 0));
            if (childCount > 0) {
                throw new BusinessException(400, "请先删除子菜单后再删除该节点");
            }
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id = ?", id);
            menuMapper.deleteById(id);
        }
    }

    /** 种子内置不可删；带 custom 权限码的可删 */
    private boolean isBuiltinRegisterMenu(Long id) {
        if (id == null) return true;
        SysMenu m = menuMapper.selectById(id);
        if (m == null) return true;
        String p = m.getPermission() == null ? "" : m.getPermission();
        if (p.contains(":custom:")) return false;
        return REGISTER_SEED_IDS.contains(id);
    }

    private void applyUpsert(SysMenu m, RegisterMenuUpsertRequest req, boolean creating) {
        m.setParentId(req.getParentId());
        m.setRouteName(req.getRouteName().trim());
        m.setMenuName(req.getMenuName().trim());
        m.setIcon(blankToNull(req.getIcon()));
        if (req.getMenuType() != null && req.getMenuType() == 1) {
            m.setPath(blankToNull(req.getPath()));
            m.setComponent(null);
        } else {
            if (req.getPath() == null || req.getPath().isBlank()) {
                throw new BusinessException(400, "访问地址不能为空");
            }
            m.setPath(req.getPath().trim());
            m.setComponent(blankToNull(req.getComponent()));
        }
        m.setMenuType(req.getMenuType());
        m.setVisible(req.getVisible() != null && req.getVisible() == 0 ? 0 : 1);
        m.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        if (req.getPermission() != null && !req.getPermission().isBlank()) {
            m.setPermission(req.getPermission().trim());
        } else if (creating) {
            m.setPermission(null);
        }
        if (req.getMCode() != null && !req.getMCode().isBlank()) {
            m.setMCode(req.getMCode().trim());
        }
    }

    private void validateType(Integer menuType) {
        if (menuType == null || menuType < 1 || menuType > 3) {
            throw new BusinessException(400, "类型无效");
        }
    }

    private void assertParentInRegisterScope(Long parentId) {
        if (parentId == null) {
            throw new BusinessException(400, "父节点不能为空");
        }
        Set<Long> scope = collectSubtreeIds(REGISTER_ROOT_ID);
        if (!scope.contains(parentId)) {
            throw new BusinessException(403, "父节点必须在数据资产登记管理系统下");
        }
    }

    private SysMenu requireInRegisterScope(Long id) {
        if (id == null || !collectSubtreeIds(REGISTER_ROOT_ID).contains(id)) {
            throw new BusinessException(403, "菜单不在数据资产登记管理系统范围内");
        }
        SysMenu m = menuMapper.selectById(id);
        if (m == null || (m.getStatus() != null && m.getStatus() == 0)) {
            throw new BusinessException(404, "菜单不存在");
        }
        return m;
    }

    private Set<Long> collectSubtreeIds(Long rootId) {
        SysMenu root = menuMapper.selectById(rootId);
        if (root == null) {
            return Set.of();
        }
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .ne(SysMenu::getStatus, 0));
        Map<Long, List<Long>> children = new HashMap<>();
        for (SysMenu m : all) {
            Long p = m.getParentId() == null ? 0L : m.getParentId();
            children.computeIfAbsent(p, k -> new ArrayList<>()).add(m.getId());
        }
        Set<Long> result = new HashSet<>();
        ArrayList<Long> stack = new ArrayList<>();
        stack.add(rootId);
        while (!stack.isEmpty()) {
            Long id = stack.remove(stack.size() - 1);
            if (!result.add(id)) continue;
            List<Long> kids = children.get(id);
            if (kids != null) stack.addAll(kids);
        }
        return result;
    }

    private static String sanitizeSlug(String routeName) {
        String s = routeName == null ? "item" : routeName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        return s.isBlank() ? "item" : s;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
