package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngBizSystem;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.entity.IngDataTable;
import com.chengde.smartcity.exchange.entity.IngProject;
import com.chengde.smartcity.exchange.mapper.IngBizSystemMapper;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.exchange.mapper.IngDataTableMapper;
import com.chengde.smartcity.exchange.mapper.IngProjectMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.CrossDeptAccessRequest;
import com.chengde.smartcity.system.entity.SysDataGrant;
import com.chengde.smartcity.system.entity.SysProjectGrant;
import com.chengde.smartcity.system.entity.SysRole;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.CrossDeptAccessRequestMapper;
import com.chengde.smartcity.system.mapper.SysDataGrantMapper;
import com.chengde.smartcity.system.mapper.SysProjectGrantMapper;
import com.chengde.smartcity.system.mapper.SysRoleMapper;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessControlService {

    private static final Logger log = LoggerFactory.getLogger(AccessControlService.class);

    private final SysProjectGrantMapper projectGrantMapper;
    private final SysDataGrantMapper dataGrantMapper;
    private final CrossDeptAccessRequestMapper crossDeptMapper;
    private final IngProjectMapper projectMapper;
    private final IngBizSystemMapper bizSystemMapper;
    private final IngDataSourceMapper dataSourceMapper;
    private final IngDataTableMapper tableMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final JdbcTemplate jdbcTemplate;

    public AccessControlService(SysProjectGrantMapper projectGrantMapper,
                                SysDataGrantMapper dataGrantMapper,
                                CrossDeptAccessRequestMapper crossDeptMapper,
                                IngProjectMapper projectMapper,
                                IngBizSystemMapper bizSystemMapper,
                                IngDataSourceMapper dataSourceMapper,
                                IngDataTableMapper tableMapper,
                                SysUserMapper userMapper,
                                SysRoleMapper roleMapper,
                                JdbcTemplate jdbcTemplate) {
        this.projectGrantMapper = projectGrantMapper;
        this.dataGrantMapper = dataGrantMapper;
        this.crossDeptMapper = crossDeptMapper;
        this.projectMapper = projectMapper;
        this.bizSystemMapper = bizSystemMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.tableMapper = tableMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> overview(UserPrincipal operator) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roleCount", roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1)));
        out.put("projectGrantCount", projectGrantMapper.selectCount(null));
        out.put("dataGrantCount", dataGrantMapper.selectCount(null));
        out.put("pendingCrossDept", crossDeptMapper.selectCount(
                new LambdaQueryWrapper<CrossDeptAccessRequest>().eq(CrossDeptAccessRequest::getStatus, 0)));
        out.put("myProjectIds", new ArrayList<>(effectiveProjectIds(operator)));
        out.put("isSystemAdmin", operator.isSystemAdmin());
        out.put("isDeptAdmin", operator.isDeptAdmin());
        out.put("orgId", operator.getOrgId());
        return out;
    }

    /** 部门管理员可写资源/数据授权；系统管理员禁止直接授信息访问权。 */
    public void assertCanGrantResourceOrData(UserPrincipal operator) {
        if (operator.isSystemAdmin() && !operator.isDeptAdmin()) {
            throw new BusinessException(403, "系统管理员不能直接授予项目或数据访问权，请由部门管理员授权或走跨部门审批");
        }
        if (!operator.isDeptAdmin()) {
            throw new BusinessException(403, "仅部门管理员可授予项目或数据访问权");
        }
    }

    public Set<Long> effectiveProjectIds(UserPrincipal user) {
        Set<Long> ids = new HashSet<>();

        // 系统管理员：可见全部登记项目（运维与验收需要；写授权仍受限）
        if (user.isSystemAdmin()) {
            projectMapper.selectList(new LambdaQueryWrapper<IngProject>().select(IngProject::getId))
                    .forEach(p -> {
                        if (p.getId() != null) ids.add(p.getId());
                    });
            return ids;
        }

        List<Long> roleIds = roleIdsOfUser(user.getUserId());

        // 部门管理员：本机构全部项目
        if (user.isDeptAdmin()) {
            List<IngProject> orgProjects = projectMapper.selectList(
                    new LambdaQueryWrapper<IngProject>().eq(IngProject::getBoundOrgId, user.getOrgId()));
            orgProjects.forEach(p -> ids.add(p.getId()));
        }

        // 显式 USER / ROLE 授权
        List<SysProjectGrant> userGrants = projectGrantMapper.selectList(new LambdaQueryWrapper<SysProjectGrant>()
                .eq(SysProjectGrant::getGranteeType, "USER")
                .eq(SysProjectGrant::getGranteeId, user.getUserId()));
        userGrants.forEach(g -> ids.add(g.getProjectId()));
        if (!roleIds.isEmpty()) {
            List<SysProjectGrant> roleGrants = projectGrantMapper.selectList(new LambdaQueryWrapper<SysProjectGrant>()
                    .eq(SysProjectGrant::getGranteeType, "ROLE")
                    .in(SysProjectGrant::getGranteeId, roleIds));
            roleGrants.forEach(g -> ids.add(g.getProjectId()));
        }

        // 本部门默认「其他」项目（PRJ_OTHER_{orgId}）：部门内可见，互不串部门
        if (user.getOrgId() != null) {
            IngProject other = projectMapper.selectOne(new LambdaQueryWrapper<IngProject>()
                    .eq(IngProject::getProjectCode, "PRJ_OTHER_" + user.getOrgId())
                    .last("LIMIT 1"));
            if (other != null && other.getId() != null) {
                ids.add(other.getId());
            }
        }
        return ids;
    }

    /**
     * 为当前登录用户所属部门确保「其他」项目 +「其他」业务系统 +「手动上传」FILE 数据源。
     * 编码：PRJ_OTHER_{orgId} / SYS_OTHER_{orgId} / DS_MANUAL_UPLOAD_{orgId}，各部门隔离。
     */
    @Transactional
    public IngProject ensureOrgOtherProject(UserPrincipal user) {
        if (user == null || user.getOrgId() == null) {
            return null;
        }
        Long orgId = user.getOrgId();
        String projectCode = "PRJ_OTHER_" + orgId;
        IngProject project = projectMapper.selectOne(new LambdaQueryWrapper<IngProject>()
                .eq(IngProject::getProjectCode, projectCode)
                .last("LIMIT 1"));
        if (project == null) {
            project = new IngProject();
            project.setProjectCode(projectCode);
            project.setProjectName("其他");
            project.setSystemName("其他");
            project.setBoundOrgId(orgId);
            project.setStatus("ACTIVE");
            project.setCreatedBy(user.getUsername() != null ? user.getUsername() : "system");
            projectMapper.insert(project);
            ensureCreatorProjectGrant(user, project.getId());
            log.info("Created per-org OTHER project code={} orgId={}", projectCode, orgId);
        }
        String systemCode = "SYS_OTHER_" + orgId;
        IngBizSystem system = bizSystemMapper.selectOne(new LambdaQueryWrapper<IngBizSystem>()
                .eq(IngBizSystem::getSystemCode, systemCode)
                .last("LIMIT 1"));
        if (system == null) {
            system = bizSystemMapper.selectOne(new LambdaQueryWrapper<IngBizSystem>()
                    .eq(IngBizSystem::getProjectId, project.getId())
                    .eq(IngBizSystem::getSystemName, "其他")
                    .last("LIMIT 1"));
        }
        if (system == null) {
            system = new IngBizSystem();
            system.setProjectId(project.getId());
            system.setSystemCode(systemCode);
            system.setSystemName("其他");
            system.setStatus("ACTIVE");
            system.setCreatedBy(user.getUsername() != null ? user.getUsername() : "system");
            bizSystemMapper.insert(system);
            log.info("Created per-org OTHER system code={} orgId={}", systemCode, orgId);
        } else if (!project.getId().equals(system.getProjectId())) {
            system.setProjectId(project.getId());
            bizSystemMapper.updateById(system);
        }
        String sourceCode = "DS_MANUAL_UPLOAD_" + orgId;
        IngDataSource ds = dataSourceMapper.selectOne(new LambdaQueryWrapper<IngDataSource>()
                .eq(IngDataSource::getSourceCode, sourceCode)
                .last("LIMIT 1"));
        if (ds == null) {
            ds = new IngDataSource();
            ds.setProjectId(project.getId());
            ds.setSystemId(system.getId());
            ds.setSourceCode(sourceCode);
            ds.setSourceName("手动上传");
            ds.setSystemName("其他");
            ds.setSourceType("FILE");
            ds.setConnStatus("OK");
            ds.setTableCount(0);
            ds.setConnConfigJson("{\"channel\":\"MANUAL_UPLOAD\",\"odsDb\":\"smart_city_ods\"}");
            ds.setSourceSchema("smart_city_ods");
            ds.setSyncStatus("PENDING");
            dataSourceMapper.insert(ds);
            log.info("Created per-org manual upload source code={} orgId={}", sourceCode, orgId);
        } else {
            boolean dirty = false;
            if (!project.getId().equals(ds.getProjectId())) {
                ds.setProjectId(project.getId());
                dirty = true;
            }
            if (!system.getId().equals(ds.getSystemId())) {
                ds.setSystemId(system.getId());
                dirty = true;
            }
            if (ds.getSystemName() == null || ds.getSystemName().isBlank()) {
                ds.setSystemName("其他");
                dirty = true;
            }
            if (dirty) {
                dataSourceMapper.updateById(ds);
            }
        }
        return project;
    }

    /**
     * 登记项目创建后：给创建人 USER/ADMIN 授权，避免「建成功但列表看不见」。
     * 系统管理员写授权受限，此处用内部直写，不走 assertCanGrantResourceOrData。
     */
    @Transactional
    public void ensureCreatorProjectGrant(UserPrincipal creator, Long projectId) {
        if (creator == null || projectId == null || creator.getUserId() == null) {
            return;
        }
        SysProjectGrant exist = projectGrantMapper.selectOne(new LambdaQueryWrapper<SysProjectGrant>()
                .eq(SysProjectGrant::getProjectId, projectId)
                .eq(SysProjectGrant::getGranteeType, "USER")
                .eq(SysProjectGrant::getGranteeId, creator.getUserId())
                .last("LIMIT 1"));
        if (exist != null) {
            if (!"ADMIN".equalsIgnoreCase(exist.getPerm())) {
                exist.setPerm("ADMIN");
                projectGrantMapper.updateById(exist);
            }
            return;
        }
        SysProjectGrant g = new SysProjectGrant();
        g.setProjectId(projectId);
        g.setGranteeType("USER");
        g.setGranteeId(creator.getUserId());
        g.setOrgId(creator.getOrgId());
        g.setPerm("ADMIN");
        g.setGrantedBy(creator.getUserId());
        g.setCreatedAt(LocalDateTime.now());
        projectGrantMapper.insert(g);
    }

    public boolean canAccessProject(UserPrincipal user, Long projectId) {
        if (projectId == null) return false;
        return effectiveProjectIds(user).contains(projectId);
    }

    public void assertProjectAccess(UserPrincipal user, Long projectId) {
        if (!canAccessProject(user, projectId)) {
            throw new BusinessException(403, "无权访问该项目");
        }
    }

    /**
     * 有效表：可访问项目下全部表 ∪ 显式 TABLE/SOURCE 授权解析出的表。
     * 若用户存在显式 TABLE 白名单 grant，则对「非本机构部门管理员」收紧为白名单 ∪ 项目内默认（部门管理员不收紧）。
     */
    public Set<Long> effectiveTableIds(UserPrincipal user) {
        Set<Long> projectIds = effectiveProjectIds(user);
        Set<Long> tableIds = new HashSet<>();
        if (!projectIds.isEmpty()) {
            List<IngDataSource> sources = dataSourceMapper.selectList(
                    new LambdaQueryWrapper<IngDataSource>().in(IngDataSource::getProjectId, projectIds));
            Set<Long> sourceIds = sources.stream().map(IngDataSource::getId).collect(Collectors.toSet());
            if (!sourceIds.isEmpty()) {
                tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>().in(IngDataTable::getSourceId, sourceIds))
                        .forEach(t -> tableIds.add(t.getId()));
            }
        }

        List<Long> roleIds = roleIdsOfUser(user.getUserId());
        List<SysDataGrant> grants = matchingDataGrants(user, roleIds, projectIds);
        for (SysDataGrant g : grants) {
            if ("TABLE".equalsIgnoreCase(g.getScopeType())) {
                tableIds.add(g.getScopeId());
            } else if ("SOURCE".equalsIgnoreCase(g.getScopeType())) {
                tableMapper.selectList(new LambdaQueryWrapper<IngDataTable>().eq(IngDataTable::getSourceId, g.getScopeId()))
                        .forEach(t -> tableIds.add(t.getId()));
            }
        }
        return tableIds;
    }

    public Set<Long> effectiveSourceIds(UserPrincipal user) {
        Set<Long> projectIds = effectiveProjectIds(user);
        Set<Long> sourceIds = new HashSet<>();
        if (!projectIds.isEmpty()) {
            dataSourceMapper.selectList(new LambdaQueryWrapper<IngDataSource>().in(IngDataSource::getProjectId, projectIds))
                    .forEach(s -> sourceIds.add(s.getId()));
        }
        List<Long> roleIds = roleIdsOfUser(user.getUserId());
        for (SysDataGrant g : matchingDataGrants(user, roleIds, projectIds)) {
            if ("SOURCE".equalsIgnoreCase(g.getScopeType())) {
                sourceIds.add(g.getScopeId());
            } else if ("TABLE".equalsIgnoreCase(g.getScopeType())) {
                IngDataTable t = tableMapper.selectById(g.getScopeId());
                if (t != null && t.getSourceId() != null) sourceIds.add(t.getSourceId());
            }
        }
        return sourceIds;
    }

    public void assertTableAccess(UserPrincipal user, Long tableId) {
        if (tableId == null || !effectiveTableIds(user).contains(tableId)) {
            throw new BusinessException(403, "无权访问该数据表");
        }
    }

    public void assertSourceAccess(UserPrincipal user, Long sourceId) {
        if (sourceId == null || !effectiveSourceIds(user).contains(sourceId)) {
            throw new BusinessException(403, "无权访问该数据源");
        }
    }

    public Map<String, Object> myEffective(UserPrincipal user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectIds", new ArrayList<>(effectiveProjectIds(user)));
        out.put("sourceIds", new ArrayList<>(effectiveSourceIds(user)));
        out.put("tableIds", new ArrayList<>(effectiveTableIds(user)));
        return out;
    }

    public List<Map<String, Object>> listProjectGrants(UserPrincipal operator, Long projectId) {
        return listProjectGrants(operator, projectId, null);
    }

    public List<Map<String, Object>> listProjectGrants(UserPrincipal operator, Long projectId, Long granteeUserId) {
        LambdaQueryWrapper<SysProjectGrant> q = new LambdaQueryWrapper<SysProjectGrant>().orderByDesc(SysProjectGrant::getId);
        if (projectId != null) q.eq(SysProjectGrant::getProjectId, projectId);
        if (granteeUserId != null) {
            q.eq(SysProjectGrant::getGranteeType, "USER").eq(SysProjectGrant::getGranteeId, granteeUserId);
        }
        if (!operator.isSystemAdmin()) q.eq(SysProjectGrant::getOrgId, operator.getOrgId());
        List<SysProjectGrant> list = projectGrantMapper.selectList(q);
        return list.stream().map(this::projectGrantView).collect(Collectors.toList());
    }

    /**
     * 可被项目授权的用户：启用账号，且角色具备「数据资产登记管理」相关菜单权限（含系统管理员）。
     */
    public List<Map<String, Object>> listUsersForProjectGrant(UserPrincipal operator) {
        if (!operator.isSystemAdmin() && !operator.isDeptAdmin()
                && !operator.getPermissions().contains("access:project-grant:manage")
                && !operator.getPermissions().contains("system:user:edit")) {
            throw new BusinessException(403, "无权查看可授权用户");
        }
        String sql = """
                SELECT DISTINCT u.id, u.username, u.display_name AS displayName, u.org_id AS orgId,
                       COALESCE(o.org_name, '未分配机构') AS orgName
                FROM sys_user u
                LEFT JOIN sys_org o ON o.id = u.org_id
                WHERE u.status = 1
                  AND (
                    EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      INNER JOIN sys_role r ON r.id = ur.role_id
                      WHERE ur.user_id = u.id AND UPPER(r.role_code) = 'SYSTEM_ADMIN'
                    )
                    OR EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      INNER JOIN sys_role_menu rm ON rm.role_id = ur.role_id
                      INNER JOIN sys_menu m ON m.id = rm.menu_id
                      WHERE ur.user_id = u.id
                        AND (
                          m.id = 7000
                          OR m.parent_id = 7000
                          OR (m.permission IS NOT NULL AND m.permission LIKE 'hub:ingestion:register%')
                        )
                    )
                  )
                ORDER BY orgName, displayName, username
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.get("id"));
            m.put("username", row.get("username"));
            m.put("displayName", row.get("displayName"));
            m.put("orgId", row.get("orgId"));
            m.put("orgName", row.get("orgName"));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Long createProjectGrant(UserPrincipal operator, Map<String, Object> body) {
        boolean sysAdmin = operator.isSystemAdmin();
        if (!sysAdmin) {
            assertCanGrantResourceOrData(operator);
        }
        Long projectId = longVal(body.get("projectId"));
        String granteeType = str(body.get("granteeType"), "USER").toUpperCase(Locale.ROOT);
        Long granteeId = longVal(body.get("granteeId"));
        String perm = str(body.get("perm"), "VIEW").toUpperCase(Locale.ROOT);
        if (projectId == null || granteeId == null) throw new BusinessException(400, "projectId/granteeId required");
        IngProject project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(404, "项目不存在");
        if (!sysAdmin) {
            if (!Objects.equals(project.getBoundOrgId(), operator.getOrgId())) {
                throw new BusinessException(403, "只能授权本机构项目");
            }
            validateGrantee(granteeType, granteeId, operator.getOrgId());
        } else if ("USER".equals(granteeType)) {
            SysUser u = userMapper.selectById(granteeId);
            if (u == null) throw new BusinessException(404, "用户不存在");
        } else if ("ROLE".equals(granteeType)) {
            SysRole r = roleMapper.selectById(granteeId);
            if (r == null) throw new BusinessException(404, "角色不存在");
        } else {
            throw new BusinessException(400, "granteeType 须为 USER 或 ROLE");
        }

        Long grantOrgId = project.getBoundOrgId() != null ? project.getBoundOrgId() : operator.getOrgId();

        SysProjectGrant exist = projectGrantMapper.selectOne(new LambdaQueryWrapper<SysProjectGrant>()
                .eq(SysProjectGrant::getProjectId, projectId)
                .eq(SysProjectGrant::getGranteeType, granteeType)
                .eq(SysProjectGrant::getGranteeId, granteeId));
        if (exist != null) {
            exist.setPerm(perm);
            exist.setGrantedBy(operator.getUserId());
            if (grantOrgId != null) {
                exist.setOrgId(grantOrgId);
            }
            projectGrantMapper.updateById(exist);
            return exist.getId();
        }
        SysProjectGrant g = new SysProjectGrant();
        g.setProjectId(projectId);
        g.setGranteeType(granteeType);
        g.setGranteeId(granteeId);
        g.setOrgId(grantOrgId);
        g.setPerm(perm);
        g.setGrantedBy(operator.getUserId());
        g.setCreatedAt(LocalDateTime.now());
        projectGrantMapper.insert(g);
        log.info("project grant created id={} by={}", g.getId(), operator.getUsername());
        return g.getId();
    }

    @Transactional
    public void deleteProjectGrant(UserPrincipal operator, Long id) {
        boolean sysAdmin = operator.isSystemAdmin();
        if (!sysAdmin) {
            assertCanGrantResourceOrData(operator);
        }
        SysProjectGrant g = projectGrantMapper.selectById(id);
        if (g == null) throw new BusinessException(404, "授权不存在");
        if (!sysAdmin && !Objects.equals(g.getOrgId(), operator.getOrgId())) {
            throw new BusinessException(403, "只能删除本机构授权");
        }
        projectGrantMapper.deleteById(id);
    }

    public List<Map<String, Object>> listDataGrants(UserPrincipal operator, String scopeType, Long scopeId) {
        LambdaQueryWrapper<SysDataGrant> q = new LambdaQueryWrapper<SysDataGrant>().orderByDesc(SysDataGrant::getId);
        if (scopeType != null && !scopeType.isBlank()) q.eq(SysDataGrant::getScopeType, scopeType.toUpperCase(Locale.ROOT));
        if (scopeId != null) q.eq(SysDataGrant::getScopeId, scopeId);
        if (!operator.isSystemAdmin()) q.eq(SysDataGrant::getOrgId, operator.getOrgId());
        return dataGrantMapper.selectList(q).stream().map(this::dataGrantView).collect(Collectors.toList());
    }

    @Transactional
    public Long createDataGrant(UserPrincipal operator, Map<String, Object> body) {
        assertCanGrantResourceOrData(operator);
        String scopeType = str(body.get("scopeType"), "TABLE").toUpperCase(Locale.ROOT);
        Long scopeId = longVal(body.get("scopeId"));
        String granteeType = str(body.get("granteeType"), "USER").toUpperCase(Locale.ROOT);
        Long granteeId = longVal(body.get("granteeId"));
        String perm = str(body.get("perm"), "READ").toUpperCase(Locale.ROOT);
        if (scopeId == null || granteeId == null) throw new BusinessException(400, "scopeId/granteeId required");
        assertScopeInOrg(scopeType, scopeId, operator.getOrgId());
        if ("USER".equals(granteeType) || "ROLE".equals(granteeType)) {
            validateGrantee(granteeType, granteeId, operator.getOrgId());
        } else if ("ORG".equals(granteeType) && !Objects.equals(granteeId, operator.getOrgId())) {
            throw new BusinessException(403, "只能授给本机构");
        } else if ("PROJECT".equals(granteeType)) {
            IngProject p = projectMapper.selectById(granteeId);
            if (p == null || !Objects.equals(p.getBoundOrgId(), operator.getOrgId())) {
                throw new BusinessException(403, "只能授给本机构项目");
            }
        }

        SysDataGrant exist = dataGrantMapper.selectOne(new LambdaQueryWrapper<SysDataGrant>()
                .eq(SysDataGrant::getScopeType, scopeType)
                .eq(SysDataGrant::getScopeId, scopeId)
                .eq(SysDataGrant::getGranteeType, granteeType)
                .eq(SysDataGrant::getGranteeId, granteeId));
        if (exist != null) {
            exist.setPerm(perm);
            exist.setGrantedBy(operator.getUserId());
            dataGrantMapper.updateById(exist);
            return exist.getId();
        }
        SysDataGrant g = new SysDataGrant();
        g.setScopeType(scopeType);
        g.setScopeId(scopeId);
        g.setGranteeType(granteeType);
        g.setGranteeId(granteeId);
        g.setOrgId(operator.getOrgId());
        g.setPerm(perm);
        g.setGrantedBy(operator.getUserId());
        g.setCreatedAt(LocalDateTime.now());
        dataGrantMapper.insert(g);
        return g.getId();
    }

    @Transactional
    public void deleteDataGrant(UserPrincipal operator, Long id) {
        assertCanGrantResourceOrData(operator);
        SysDataGrant g = dataGrantMapper.selectById(id);
        if (g == null) throw new BusinessException(404, "授权不存在");
        if (!Objects.equals(g.getOrgId(), operator.getOrgId())) {
            throw new BusinessException(403, "只能删除本机构授权");
        }
        dataGrantMapper.deleteById(id);
    }

    public List<Map<String, Object>> listCrossDept(UserPrincipal operator, Integer status) {
        LambdaQueryWrapper<CrossDeptAccessRequest> q = new LambdaQueryWrapper<CrossDeptAccessRequest>()
                .orderByDesc(CrossDeptAccessRequest::getId);
        if (status != null) q.eq(CrossDeptAccessRequest::getStatus, status);
        if (!operator.isSystemAdmin() && !operator.isDeptAdmin()) {
            q.eq(CrossDeptAccessRequest::getApplicantUserId, operator.getUserId());
        } else if (operator.isDeptAdmin() && !operator.isSystemAdmin()) {
            q.and(w -> w.eq(CrossDeptAccessRequest::getTargetOrgId, operator.getOrgId())
                    .or().eq(CrossDeptAccessRequest::getApplicantOrgId, operator.getOrgId()));
        }
        return crossDeptMapper.selectList(q).stream().map(this::crossView).collect(Collectors.toList());
    }

    @Transactional
    public Long applyCrossDept(UserPrincipal operator, Map<String, Object> body) {
        Long targetOrgId = longVal(body.get("targetOrgId"));
        String resourceType = str(body.get("resourceType"), "PROJECT").toUpperCase(Locale.ROOT);
        String resourceId = str(body.get("resourceId"), null);
        String reason = str(body.get("reason"), "");
        if (targetOrgId == null || resourceId == null || resourceId.isBlank()) {
            throw new BusinessException(400, "targetOrgId/resourceId required");
        }
        if (Objects.equals(targetOrgId, operator.getOrgId())) {
            throw new BusinessException(400, "本机构资源请由部门管理员直接授权，无需跨部门申请");
        }
        CrossDeptAccessRequest req = new CrossDeptAccessRequest();
        req.setApplicantUserId(operator.getUserId());
        req.setApplicantOrgId(operator.getOrgId());
        req.setTargetOrgId(targetOrgId);
        req.setResourceType(resourceType);
        req.setResourceId(resourceId);
        req.setReason(reason);
        req.setStatus(0);
        req.setCreatedAt(LocalDateTime.now());
        crossDeptMapper.insert(req);
        return req.getId();
    }

    @Transactional
    public void approveCrossDept(UserPrincipal operator, Long id, boolean pass, String comment) {
        if (!operator.isDeptAdmin()) {
            throw new BusinessException(403, "仅目标机构部门管理员可审批");
        }
        CrossDeptAccessRequest req = crossDeptMapper.selectById(id);
        if (req == null) throw new BusinessException(404, "申请不存在");
        if (!Objects.equals(req.getTargetOrgId(), operator.getOrgId())) {
            throw new BusinessException(403, "只能审批指向本机构的申请");
        }
        if (req.getStatus() != null && req.getStatus() != 0) {
            throw new BusinessException(400, "申请已处理");
        }
        req.setStatus(pass ? 1 : 2);
        req.setApproverUserId(operator.getUserId());
        req.setApprovedAt(LocalDateTime.now());
        req.setApproveComment(comment);
        crossDeptMapper.updateById(req);
        if (pass) {
            materializeCrossDeptGrant(operator, req);
        }
    }

    private void materializeCrossDeptGrant(UserPrincipal approver, CrossDeptAccessRequest req) {
        Long resourcePk = parseLong(req.getResourceId());
        if (resourcePk == null) return;
        String type = req.getResourceType() == null ? "PROJECT" : req.getResourceType().toUpperCase(Locale.ROOT);
        if ("PROJECT".equals(type)) {
            IngProject p = projectMapper.selectById(resourcePk);
            if (p == null || !Objects.equals(p.getBoundOrgId(), approver.getOrgId())) {
                throw new BusinessException(400, "资源不属于本机构");
            }
            Map<String, Object> body = new HashMap<>();
            body.put("projectId", resourcePk);
            body.put("granteeType", "USER");
            body.put("granteeId", req.getApplicantUserId());
            body.put("perm", "VIEW");
            // 审批人已是 dept admin；临时绕过申请人机构校验：直接 insert
            SysProjectGrant exist = projectGrantMapper.selectOne(new LambdaQueryWrapper<SysProjectGrant>()
                    .eq(SysProjectGrant::getProjectId, resourcePk)
                    .eq(SysProjectGrant::getGranteeType, "USER")
                    .eq(SysProjectGrant::getGranteeId, req.getApplicantUserId()));
            if (exist == null) {
                SysProjectGrant g = new SysProjectGrant();
                g.setProjectId(resourcePk);
                g.setGranteeType("USER");
                g.setGranteeId(req.getApplicantUserId());
                g.setOrgId(approver.getOrgId());
                g.setPerm("VIEW");
                g.setGrantedBy(approver.getUserId());
                g.setCreatedAt(LocalDateTime.now());
                projectGrantMapper.insert(g);
            }
        } else if ("TABLE".equals(type) || "SOURCE".equals(type)) {
            SysDataGrant exist = dataGrantMapper.selectOne(new LambdaQueryWrapper<SysDataGrant>()
                    .eq(SysDataGrant::getScopeType, type)
                    .eq(SysDataGrant::getScopeId, resourcePk)
                    .eq(SysDataGrant::getGranteeType, "USER")
                    .eq(SysDataGrant::getGranteeId, req.getApplicantUserId()));
            if (exist == null) {
                SysDataGrant g = new SysDataGrant();
                g.setScopeType(type);
                g.setScopeId(resourcePk);
                g.setGranteeType("USER");
                g.setGranteeId(req.getApplicantUserId());
                g.setOrgId(approver.getOrgId());
                g.setPerm("READ");
                g.setGrantedBy(approver.getUserId());
                g.setCreatedAt(LocalDateTime.now());
                dataGrantMapper.insert(g);
            }
        }
    }

    private List<SysDataGrant> matchingDataGrants(UserPrincipal user, List<Long> roleIds, Set<Long> projectIds) {
        List<SysDataGrant> all = dataGrantMapper.selectList(null);
        List<SysDataGrant> matched = new ArrayList<>();
        for (SysDataGrant g : all) {
            String gt = g.getGranteeType() == null ? "" : g.getGranteeType().toUpperCase(Locale.ROOT);
            if ("USER".equals(gt) && Objects.equals(g.getGranteeId(), user.getUserId())) {
                matched.add(g);
            } else if ("ROLE".equals(gt) && roleIds.contains(g.getGranteeId())) {
                matched.add(g);
            } else if ("ORG".equals(gt) && Objects.equals(g.getGranteeId(), user.getOrgId())) {
                matched.add(g);
            } else if ("PROJECT".equals(gt) && projectIds.contains(g.getGranteeId())) {
                matched.add(g);
            }
        }
        return matched;
    }

    private void validateGrantee(String granteeType, Long granteeId, Long orgId) {
        if ("USER".equals(granteeType)) {
            SysUser u = userMapper.selectById(granteeId);
            if (u == null) throw new BusinessException(404, "用户不存在");
            if (!Objects.equals(u.getOrgId(), orgId)) {
                throw new BusinessException(403, "只能授权给本机构用户（跨机构请走审批）");
            }
        } else if ("ROLE".equals(granteeType)) {
            SysRole r = roleMapper.selectById(granteeId);
            if (r == null) throw new BusinessException(404, "角色不存在");
        } else {
            throw new BusinessException(400, "granteeType 须为 USER 或 ROLE");
        }
    }

    private void assertScopeInOrg(String scopeType, Long scopeId, Long orgId) {
        if ("TABLE".equals(scopeType)) {
            IngDataTable t = tableMapper.selectById(scopeId);
            if (t == null) throw new BusinessException(404, "表不存在");
            IngDataSource s = dataSourceMapper.selectById(t.getSourceId());
            if (s == null) throw new BusinessException(404, "数据源不存在");
            IngProject p = projectMapper.selectById(s.getProjectId());
            if (p == null || !Objects.equals(p.getBoundOrgId(), orgId)) {
                throw new BusinessException(403, "只能授权本机构数据表");
            }
        } else if ("SOURCE".equals(scopeType)) {
            IngDataSource s = dataSourceMapper.selectById(scopeId);
            if (s == null) throw new BusinessException(404, "数据源不存在");
            IngProject p = projectMapper.selectById(s.getProjectId());
            if (p == null || !Objects.equals(p.getBoundOrgId(), orgId)) {
                throw new BusinessException(403, "只能授权本机构数据源");
            }
        } else {
            throw new BusinessException(400, "scopeType 须为 SOURCE 或 TABLE");
        }
    }

    private List<Long> roleIdsOfUser(Long userId) {
        return jdbcTemplate.queryForList("SELECT role_id FROM sys_user_role WHERE user_id = ?", Long.class, userId);
    }

    private Map<String, Object> projectGrantView(SysProjectGrant g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("projectId", g.getProjectId());
        IngProject p = projectMapper.selectById(g.getProjectId());
        m.put("projectName", p == null ? null : p.getProjectName());
        m.put("projectOrgId", p == null ? null : p.getBoundOrgId());
        if (p != null && p.getBoundOrgId() != null) {
            // 列表展示用：机构名（若已加载 boundOrgName 则复用查询）
            try {
                var org = jdbcTemplate.queryForMap("SELECT org_name FROM sys_org WHERE id = ? LIMIT 1", p.getBoundOrgId());
                m.put("projectOrgName", org.get("org_name"));
            } catch (Exception ignored) {
                m.put("projectOrgName", null);
            }
        } else {
            m.put("projectOrgName", null);
        }
        m.put("granteeType", g.getGranteeType());
        m.put("granteeId", g.getGranteeId());
        m.put("granteeName", resolveGranteeName(g.getGranteeType(), g.getGranteeId()));
        m.put("orgId", g.getOrgId());
        m.put("perm", g.getPerm());
        m.put("grantedBy", g.getGrantedBy());
        m.put("createdAt", g.getCreatedAt());
        return m;
    }

    private Map<String, Object> dataGrantView(SysDataGrant g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("scopeType", g.getScopeType());
        m.put("scopeId", g.getScopeId());
        m.put("scopeLabel", resolveScopeLabel(g.getScopeType(), g.getScopeId()));
        m.put("granteeType", g.getGranteeType());
        m.put("granteeId", g.getGranteeId());
        m.put("granteeName", resolveGranteeName(g.getGranteeType(), g.getGranteeId()));
        m.put("orgId", g.getOrgId());
        m.put("perm", g.getPerm());
        m.put("grantedBy", g.getGrantedBy());
        m.put("createdAt", g.getCreatedAt());
        return m;
    }

    private Map<String, Object> crossView(CrossDeptAccessRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("applicantUserId", r.getApplicantUserId());
        SysUser u = userMapper.selectById(r.getApplicantUserId());
        m.put("applicantName", u == null ? null : u.getDisplayName());
        m.put("applicantOrgId", r.getApplicantOrgId());
        m.put("targetOrgId", r.getTargetOrgId());
        m.put("resourceType", r.getResourceType());
        m.put("resourceId", r.getResourceId());
        m.put("reason", r.getReason());
        m.put("status", r.getStatus());
        m.put("statusLabel", statusLabel(r.getStatus()));
        m.put("approverUserId", r.getApproverUserId());
        m.put("approvedAt", r.getApprovedAt());
        m.put("approveComment", r.getApproveComment());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private String statusLabel(Integer s) {
        if (s == null) return "未知";
        return switch (s) {
            case 0 -> "待审";
            case 1 -> "通过";
            case 2 -> "拒绝";
            default -> "未知";
        };
    }

    private String resolveGranteeName(String type, Long id) {
        if (type == null || id == null) return null;
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "USER" -> {
                SysUser u = userMapper.selectById(id);
                yield u == null ? String.valueOf(id) : u.getDisplayName();
            }
            case "ROLE" -> {
                SysRole r = roleMapper.selectById(id);
                yield r == null ? String.valueOf(id) : r.getRoleName();
            }
            case "ORG" -> "机构#" + id;
            case "PROJECT" -> {
                IngProject p = projectMapper.selectById(id);
                yield p == null ? String.valueOf(id) : p.getProjectName();
            }
            default -> String.valueOf(id);
        };
    }

    private String resolveScopeLabel(String type, Long id) {
        if ("TABLE".equalsIgnoreCase(type)) {
            IngDataTable t = tableMapper.selectById(id);
            return t == null ? String.valueOf(id) : t.getTableName();
        }
        if ("SOURCE".equalsIgnoreCase(type)) {
            IngDataSource s = dataSourceMapper.selectById(id);
            return s == null ? String.valueOf(id) : s.getSourceName();
        }
        return String.valueOf(id);
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return Long.valueOf(String.valueOf(v));
    }

    private static Long parseLong(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object v, String def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        return String.valueOf(v);
    }
}
