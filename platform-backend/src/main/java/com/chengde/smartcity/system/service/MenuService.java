package com.chengde.smartcity.system.service;

import com.chengde.smartcity.system.entity.SysMenu;
import com.chengde.smartcity.system.mapper.SysMenuMapper;
import com.chengde.smartcity.system.dto.MenuTreeNode;
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
        List<SysMenu> menus = menuMapper.findMenusByUserId(userId);
        menus = menus.stream()
                .filter(m -> m.getMenuType() != 3)
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() == null ? 0 : m.getSortOrder()))
                .toList();
        Map<Long, MenuTreeNode> index = new HashMap<>();
        List<MenuTreeNode> roots = new ArrayList<>();
        for (SysMenu m : menus) {
            MenuTreeNode node = toNode(m);
            index.put(m.getId(), node);
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

    public List<SysMenu> listAll() {
        return menuMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder));
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
