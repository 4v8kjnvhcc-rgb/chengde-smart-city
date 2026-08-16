package com.chengde.smartcity.system.bootstrap;

import java.sql.Connection;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/**
 * 启动时仅做「结构补齐 / 停用废弃菜单 / 超管授权」。
 * <p><b>禁止</b>覆盖 {@code sys_menu.visible}（菜单管理「是否隐藏」）：
 * 该字段由运维在界面维护，每次部署/重启不得改回，否则生产会出现「部署后大批菜单又隐藏」。</p>
 * 平台管理仅保留一级快捷入口（id=19，无子菜单）；禁止复活旧 /system/* 子树（仅 status=0）。
 */
@Component
public class HubSidebarMenuBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HubSidebarMenuBootstrap.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public HubSidebarMenuBootstrap(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer hubRoot = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE id = 7000", Integer.class);
        if (hubRoot == null || hubRoot == 0) {
            log.info("Hub sidebar menus missing (id=7000), applying V80__hub_sidebar_rbac_menus.sql");
            try (Connection conn = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V80__hub_sidebar_rbac_menus.sql"));
            }
            log.info("Hub sidebar menus seeded");
        }
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 0 WHERE id = 4100 OR permission = 'exchange:project:delete'");

        // 平台管理：结构对齐（不改 visible）
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1, parent_id = 1, menu_name = '平台管理', "
                        + "menu_type = 1, path = '/system', component = NULL, "
                        + "permission = 'hub:system:platform', integration_type = 'self', sort_order = 90 "
                        + "WHERE id = 19");

        // 业务功能平台 + 人口信息库：结构对齐（不改 visible）
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1, parent_id = 1, menu_name = '业务功能平台', "
                        + "menu_type = 1, path = '/business', permission = 'hub:business:platform', "
                        + "integration_type = 'self', sort_order = 18 WHERE id = 6000");
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1, parent_id = 6000, "
                        + "menu_name = '承德市高新区人口信息库', menu_type = 2, "
                        + "path = '/business/gaoxin-pop-lib', permission = 'hub:business:gaoxin-pop-lib', "
                        + "integration_type = 'self', sort_order = 1 WHERE id = 6010");

        // 废弃菜单：只停用 status，不改 visible（避免与「是否隐藏」运维配置纠缠）
        int retired = jdbcTemplate.update(
                "UPDATE sys_menu SET status = 0 WHERE status <> 0 AND ("
                        + "id IN (27, 6400, 6401, 6402, 6403, 6500, 30, 31, 7507, 7508)"
                        + " OR id BETWEEN 7600 AND 7611"
                        + " OR id IN (7630, 7631, 7632, 7633)"
                        + " OR path = '/integration'"
                        + " OR (IFNULL(path,'') LIKE '/system/%' AND path <> '/system')"
                        + ")");
        if (retired > 0) {
            log.info("Retired obsolete menus (status=0 only, visible untouched): {} rows", retired);
        }

        // 通用支撑：统一用户 / 智能BI / 任务管理 / 集成运维（7882/7883）
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1 WHERE id IN (12, 7880, 7881, 7882, 7883, 13, 14, 15, 16, 17, 18, 19, 6000, 6010)");
        jdbcTemplate.update(
                "UPDATE sys_menu SET parent_id = 7880, sort_order = 3, status = 1, "
                        + "menu_name = '任务管理', path = '/analytics/support?tab=tasks', "
                        + "permission = 'hub:analytics:support:tasks' WHERE id = 7882");
        jdbcTemplate.update(
                "UPDATE sys_menu SET parent_id = 7880, sort_order = 4, status = 1, "
                        + "menu_name = '集成运维', path = '/analytics/support?tab=ops.kettle', "
                        + "permission = 'hub:analytics:support:ops' WHERE id = 7883");

        int granted = jdbcTemplate.update(
                "INSERT INTO sys_role_menu (role_id, menu_id) "
                        + "SELECT 1, m.id FROM sys_menu m WHERE "
                        + "m.status = 1 "
                        + "AND IFNULL(m.integration_type,'') <> 'catalog' "
                        + "AND IFNULL(m.menu_name,'') NOT LIKE '%D05%' "
                        + "AND IFNULL(m.menu_name,'') NOT LIKE '%已并入%' "
                        + "AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id)");
        if (granted > 0) {
            log.info("SYSTEM_ADMIN menu grants restored: {} rows", granted);
        }
    }
}
