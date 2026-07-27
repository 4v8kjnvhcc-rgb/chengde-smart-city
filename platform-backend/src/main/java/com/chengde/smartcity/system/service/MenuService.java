package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.MenuTreeNode;
import com.chengde.smartcity.system.entity.SysMenu;
import com.chengde.smartcity.system.mapper.SysMenuMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MenuService {

    private final SysMenuMapper menuMapper;

    public MenuService(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
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
}
