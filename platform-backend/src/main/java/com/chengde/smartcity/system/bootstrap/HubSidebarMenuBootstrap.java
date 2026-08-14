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
 * 若 Flyway V80 尚未写入 Hub 侧栏菜单（例如后端在迁移脚本加入前已长期运行），启动时补跑 V80。
 * 平台管理仅保留一级快捷入口（id=19，无子菜单）；禁止复活旧 /system/* 子树。
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

        // 平台管理：无下级目录快捷入口，首页直达统一用户
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1, visible = 1, parent_id = 1, menu_name = '平台管理', "
                        + "menu_type = 1, path = '/system', component = NULL, "
                        + "permission = 'hub:system:platform', integration_type = 'self', sort_order = 90 "
                        + "WHERE id = 19");

        // 业务功能平台 + 人口信息库
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1, visible = 1, parent_id = 1, menu_name = '业务功能平台', "
                        + "menu_type = 1, path = '/business', permission = 'hub:business:platform', "
                        + "integration_type = 'self', sort_order = 18 WHERE id = 6000");
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1, visible = 0, parent_id = 6000, "
                        + "menu_name = '承德市高新区人口信息库', menu_type = 2, "
                        + "path = '/business/gaoxin-pop-lib', permission = 'hub:business:gaoxin-pop-lib', "
                        + "integration_type = 'self', sort_order = 1 WHERE id = 6010");

        // 旧平台管理子树 / 集成运维一级壳 / V210 误复活的旧 UUM「日志审计」保持停用
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 0, visible = 0 WHERE id IN (27, 6400, 6401, 6402, 6403, 6500, 30, 31)"
                        + " OR id BETWEEN 7600 AND 7611"
                        + " OR id IN (7630, 7631, 7632, 7633)"
                        + " OR path = '/integration'"
                        + " OR (IFNULL(path,'') LIKE '/system/%' AND path <> '/system')");

        // 通用支撑：统一用户 / 智能BI / 任务管理 / 集成运维（7882/7883 为配置与门户入口）
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1 WHERE id IN (12, 7880, 7881, 7882, 7883, 13, 14, 15, 16, 17, 18, 19, 6000, 6010)");
        jdbcTemplate.update(
                "UPDATE sys_menu SET parent_id = 7880, sort_order = 3, status = 1, visible = 1, "
                        + "menu_name = '任务管理', path = '/analytics/support?tab=tasks', "
                        + "permission = 'hub:analytics:support:tasks' WHERE id = 7882");
        jdbcTemplate.update(
                "UPDATE sys_menu SET parent_id = 7880, sort_order = 4, status = 1, visible = 1, "
                        + "menu_name = '集成运维', path = '/analytics/support?tab=ops.kettle', "
                        + "permission = 'hub:analytics:support:ops' WHERE id = 7883");
        // UUM 内不再保留重复配置项（侧栏仍按 permission 控制）
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 0, visible = 0 WHERE id IN (7507, 7508)");

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
