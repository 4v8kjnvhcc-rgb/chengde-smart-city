package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.PortalNavNodeRequest;
import com.chengde.smartcity.system.dto.PortalNavNodeTree;
import com.chengde.smartcity.system.entity.PortalNavNode;
import com.chengde.smartcity.system.entity.SysMenu;
import com.chengde.smartcity.system.mapper.PortalNavNodeMapper;
import com.chengde.smartcity.system.mapper.SysMenuMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PortalNavService {

    private static final Set<String> NODE_TYPES = Set.of("platform", "sub_platform", "system", "module");
    private static final Set<String> OPEN_MODES = Set.of("route", "new_tab");
    private static final Set<String> SSO_MODES = Set.of("none", "portal_ticket");
    private static final Set<String> THEME_KEYS = Set.of(
            "/exchange", "/master-data", "/analytics", "/business");

    private final PortalNavNodeMapper nodeMapper;
    private final SysMenuMapper menuMapper;
    private final AuditService auditService;

    public PortalNavService(PortalNavNodeMapper nodeMapper, SysMenuMapper menuMapper, AuditService auditService) {
        this.nodeMapper = nodeMapper;
        this.menuMapper = menuMapper;
        this.auditService = auditService;
    }

    /** 管理端：全量树（含停用） */
    public List<PortalNavNodeTree> listTree() {
        return buildTree(listAllOrdered(), false);
    }

    /** 管理端：扁平列表（含停用，树序） */
    public List<PortalNavNode> listFlat() {
        List<PortalNavNodeTree> tree = listTree();
        List<PortalNavNode> flat = new ArrayList<>();
        flatten(tree, flat);
        return flat;
    }

    /** 首页：启用节点树 + 按用户菜单 path 过滤 */
    public List<PortalNavNodeTree> enabledTreeForUser(UserPrincipal principal) {
        List<PortalNavNode> enabled = nodeMapper.selectList(new LambdaQueryWrapper<PortalNavNode>()
                .eq(PortalNavNode::getStatus, 1)
                .orderByAsc(PortalNavNode::getSortOrder)
                .orderByAsc(PortalNavNode::getId));
        Set<String> menuPaths = collectUserMenuPaths(principal);
        List<PortalNavNodeTree> tree = buildTree(enabled, true);
        return pruneByPermission(tree, menuPaths);
    }

    @Transactional
    public Long create(UserPrincipal operator, PortalNavNodeRequest req) {
        PortalNavNode node = new PortalNavNode();
        apply(node, req, true);
        nodeMapper.insert(node);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_NAV_CREATE", "portal_nav_node", String.valueOf(node.getId()), node.getName());
        return node.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, PortalNavNodeRequest req) {
        PortalNavNode node = require(id);
        apply(node, req, false);
        if (req.parentId() != null && req.parentId().equals(id)) {
            throw new BusinessException(400, "上级节点不能是自身");
        }
        if (req.parentId() != null && isDescendant(id, req.parentId())) {
            throw new BusinessException(400, "上级节点不能是当前节点的子孙");
        }
        nodeMapper.updateById(node);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_NAV_UPDATE", "portal_nav_node", String.valueOf(id), node.getName());
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        PortalNavNode node = require(id);
        Long childCount = nodeMapper.selectCount(new LambdaQueryWrapper<PortalNavNode>()
                .eq(PortalNavNode::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(400, "存在子节点，请先删除子项");
        }
        nodeMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "PORTAL_NAV_DELETE", "portal_nav_node", String.valueOf(id), node.getName());
    }

    private void apply(PortalNavNode node, PortalNavNodeRequest req, boolean creating) {
        String type = req.nodeType().trim().toLowerCase(Locale.ROOT);
        if (!NODE_TYPES.contains(type)) {
            throw new BusinessException(400, "节点类型无效，须为 platform / sub_platform / system / module");
        }
        long parentId = req.parentId() == null ? 0L : req.parentId();
        validateHierarchy(type, parentId, creating ? null : node.getId());

        String openMode = StringUtils.hasText(req.openMode()) ? req.openMode().trim() : "route";
        if (!OPEN_MODES.contains(openMode)) {
            throw new BusinessException(400, "打开方式无效，须为 route 或 new_tab");
        }
        String ssoMode = StringUtils.hasText(req.ssoMode()) ? req.ssoMode().trim().toLowerCase(Locale.ROOT) : "none";
        if (!SSO_MODES.contains(ssoMode)) {
            throw new BusinessException(400, "SSO 模式无效，须为 none 或 portal_ticket");
        }
        if ("platform".equals(type)) {
            if (parentId != 0L) {
                throw new BusinessException(400, "平台节点的上级必须为空（根）");
            }
            String theme = StringUtils.hasText(req.themeKey()) ? req.themeKey().trim() : null;
            if (theme != null && !THEME_KEYS.contains(theme)) {
                throw new BusinessException(400, "主题键无效，须为 /exchange|/master-data|/analytics|/business");
            }
            node.setThemeKey(theme);
        } else {
            node.setThemeKey(null);
        }

        String url = trimToNull(req.url());
        String menuPath = trimToNull(req.menuPath());
        if (url != null && !(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/"))) {
            throw new BusinessException(400, "地址须以 http(s):// 或 / 开头");
        }
        if (menuPath != null && !menuPath.startsWith("/")) {
            throw new BusinessException(400, "菜单路径须以 / 开头");
        }

        node.setParentId(parentId);
        node.setName(req.name().trim());
        node.setNodeType(type);
        node.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        node.setUrl(url);
        node.setMenuPath(menuPath);
        node.setOpenMode(openMode);
        node.setSsoMode(ssoMode);
        node.setRemark(trimToNull(req.remark()));
        if (req.status() != null) {
            node.setStatus(req.status());
        } else if (creating) {
            node.setStatus(1);
        }
    }

    private void validateHierarchy(String type, long parentId, Long selfId) {
        if ("platform".equals(type)) {
            return;
        }
        if (parentId == 0L) {
            throw new BusinessException(400, "子平台/系统/模块必须指定上级节点");
        }
        PortalNavNode parent = nodeMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(400, "上级节点不存在");
        }
        if (selfId != null && parentId == selfId) {
            throw new BusinessException(400, "上级节点不能是自身");
        }
        String parentType = parent.getNodeType();
        if ("sub_platform".equals(type) && !"platform".equals(parentType)) {
            throw new BusinessException(400, "子平台的上级必须是平台");
        }
        if ("system".equals(type) && !"sub_platform".equals(parentType)) {
            throw new BusinessException(400, "系统的上级必须是子平台");
        }
        if ("module".equals(type) && !"system".equals(parentType)) {
            throw new BusinessException(400, "模块的上级必须是系统");
        }
    }

    private boolean isDescendant(Long ancestorId, Long candidateParentId) {
        Long cursor = candidateParentId;
        int guard = 0;
        while (cursor != null && cursor > 0 && guard++ < 32) {
            if (cursor.equals(ancestorId)) {
                return true;
            }
            PortalNavNode n = nodeMapper.selectById(cursor);
            if (n == null) {
                return false;
            }
            cursor = n.getParentId();
        }
        return false;
    }

    private PortalNavNode require(Long id) {
        PortalNavNode node = nodeMapper.selectById(id);
        if (node == null) {
            throw new BusinessException(404, "导航节点不存在");
        }
        return node;
    }

    private List<PortalNavNode> listAllOrdered() {
        return nodeMapper.selectList(new LambdaQueryWrapper<PortalNavNode>()
                .orderByAsc(PortalNavNode::getSortOrder)
                .orderByAsc(PortalNavNode::getId));
    }

    private List<PortalNavNodeTree> buildTree(List<PortalNavNode> source, boolean enabledOnly) {
        Map<Long, PortalNavNodeTree> index = new HashMap<>();
        List<PortalNavNodeTree> roots = new ArrayList<>();
        for (PortalNavNode n : source) {
            if (enabledOnly && (n.getStatus() == null || n.getStatus() != 1)) {
                continue;
            }
            index.put(n.getId(), toTree(n));
        }
        List<PortalNavNodeTree> ordered = index.values().stream()
                .sorted(Comparator
                        .comparingInt((PortalNavNodeTree t) -> t.getSortOrder() == null ? 0 : t.getSortOrder())
                        .thenComparingLong(t -> t.getId() == null ? 0L : t.getId()))
                .toList();
        for (PortalNavNodeTree node : ordered) {
            long pid = node.getParentId() == null ? 0L : node.getParentId();
            if (pid == 0L || !index.containsKey(pid)) {
                roots.add(node);
            } else {
                index.get(pid).getChildren().add(node);
            }
        }
        sortRecursive(roots);
        return roots;
    }

    private void sortRecursive(List<PortalNavNodeTree> nodes) {
        nodes.sort(Comparator
                .comparingInt((PortalNavNodeTree t) -> t.getSortOrder() == null ? 0 : t.getSortOrder())
                .thenComparingLong(t -> t.getId() == null ? 0L : t.getId()));
        for (PortalNavNodeTree n : nodes) {
            sortRecursive(n.getChildren());
        }
    }

    private void flatten(List<PortalNavNodeTree> tree, List<PortalNavNode> out) {
        for (PortalNavNodeTree t : tree) {
            PortalNavNode n = new PortalNavNode();
            n.setId(t.getId());
            n.setParentId(t.getParentId());
            n.setName(t.getName());
            n.setNodeType(t.getNodeType());
            n.setSortOrder(t.getSortOrder());
            n.setUrl(t.getUrl());
            n.setMenuPath(t.getMenuPath());
            n.setOpenMode(t.getOpenMode());
            n.setSsoMode(t.getSsoMode());
            n.setThemeKey(t.getThemeKey());
            n.setRemark(t.getRemark());
            n.setStatus(t.getStatus());
            out.add(n);
            flatten(t.getChildren(), out);
        }
    }

    private PortalNavNodeTree toTree(PortalNavNode n) {
        PortalNavNodeTree t = new PortalNavNodeTree();
        t.setId(n.getId());
        t.setParentId(n.getParentId());
        t.setName(n.getName());
        t.setNodeType(n.getNodeType());
        t.setSortOrder(n.getSortOrder());
        t.setUrl(n.getUrl());
        t.setMenuPath(n.getMenuPath());
        t.setOpenMode(n.getOpenMode());
        t.setSsoMode(n.getSsoMode());
        t.setThemeKey(n.getThemeKey());
        t.setRemark(n.getRemark());
        t.setStatus(n.getStatus());
        return t;
    }

    /**
     * 收集用户已授权菜单 path（含 Hub visible=0 项，否则门户配置无法匹配登记/采集入口）。
     */
    private Set<String> collectUserMenuPaths(UserPrincipal principal) {
        List<SysMenu> menus;
        if (principal != null && principal.isSystemAdmin()) {
            menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getStatus, 1));
        } else {
            menus = menuMapper.findMenusByUserId(principal.getUserId());
        }
        Set<String> paths = new HashSet<>();
        for (SysMenu m : menus) {
            if (m.getPath() != null && !m.getPath().isBlank()) {
                paths.add(m.getPath().trim());
            }
        }
        return paths;
    }

    /**
     * 有 menu_path：须匹配用户菜单；仅有 url：登录可见；无子且无可点地址的父节点剔除。
     */
    private List<PortalNavNodeTree> pruneByPermission(List<PortalNavNodeTree> nodes, Set<String> menuPaths) {
        List<PortalNavNodeTree> kept = new ArrayList<>();
        for (PortalNavNodeTree node : nodes) {
            List<PortalNavNodeTree> children = pruneByPermission(node.getChildren(), menuPaths);
            node.setChildren(children);

            boolean selfAllowed = isNodeAllowed(node, menuPaths);
            boolean hasChildren = !children.isEmpty();
            boolean navigable = hasNavigableTarget(node);

            if ("platform".equals(node.getNodeType())) {
                // 业务功能平台等：无子也可展示空卡片
                if (hasChildren || navigable || THEME_KEYS.contains(nullToEmpty(node.getThemeKey()))) {
                    kept.add(node);
                }
                continue;
            }
            if (hasChildren || (selfAllowed && navigable)) {
                kept.add(node);
            } else if (selfAllowed && !navigable && hasChildren) {
                kept.add(node);
            }
        }
        return kept;
    }

    private boolean isNodeAllowed(PortalNavNodeTree node, Set<String> menuPaths) {
        String menuPath = node.getMenuPath();
        if (!StringUtils.hasText(menuPath)) {
            // 无 menu_path：仅有 url 则登录可见；皆空则作为分组标题，留给子节点决定
            return true;
        }
        return menuPaths.stream().anyMatch(p -> pathMatches(menuPath, p));
    }

    private boolean hasNavigableTarget(PortalNavNodeTree node) {
        return StringUtils.hasText(node.getUrl()) || StringUtils.hasText(node.getMenuPath());
    }

    /** 路由/菜单 path 匹配：精确、去 query 后相等、或带 system= 时要求一致 */
    static boolean pathMatches(String configured, String menuPath) {
        if (!StringUtils.hasText(configured) || !StringUtils.hasText(menuPath)) {
            return false;
        }
        String a = configured.trim();
        String b = menuPath.trim();
        if (a.equals(b)) {
            return true;
        }
        String ap = stripQuery(a);
        String bp = stripQuery(b);
        String aq = queryOf(a);
        String bq = queryOf(b);
        if (ap.equals(bp)) {
            if (!StringUtils.hasText(aq) || !StringUtils.hasText(bq)) {
                return true;
            }
            String asys = paramValue(aq, "system");
            String bsys = paramValue(bq, "system");
            if (StringUtils.hasText(asys) && StringUtils.hasText(bsys)) {
                return asys.equalsIgnoreCase(bsys);
            }
            return aq.equals(bq) || bq.contains(aq) || aq.contains(bq);
        }
        if (b.startsWith(a + "/") || a.startsWith(b + "/")) {
            return true;
        }
        if (b.startsWith(ap + "/") || ap.startsWith(bp + "/")) {
            return true;
        }
        return false;
    }

    private static String stripQuery(String path) {
        int i = path.indexOf('?');
        return i < 0 ? path : path.substring(0, i);
    }

    private static String queryOf(String path) {
        int i = path.indexOf('?');
        return i < 0 ? "" : path.substring(i + 1);
    }

    private static String paramValue(String query, String key) {
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equalsIgnoreCase(key)) {
                return part.substring(eq + 1);
            }
        }
        return "";
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
