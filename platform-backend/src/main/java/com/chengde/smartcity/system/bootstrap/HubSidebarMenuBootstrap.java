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
        // 角色菜单树不展示「删除登记项目」按钮项（与 V81 一致，兼容尚未跑迁移的环境）
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 0 WHERE id = 4100 OR permission = 'exchange:project:delete'");
        // 恢复菜单管理入口（与 V82 一致）
        int restored = jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1, visible = 1, parent_id = 6402, sort_order = 31, "
                        + "menu_name = '菜单管理', path = '/system/menus', component = 'system/MenuManage', "
                        + "permission = 'system:menu:list', integration_type = 'self' WHERE id = 27");
        if (restored > 0) {
            jdbcTemplate.update("UPDATE sys_menu SET status = 1, visible = 1 WHERE id = 6402");
            log.info("Menu management entry restored (id=27)");
        }
        // 恢复系统管理员全量菜单授权（与 V83 一致，避免误勾导致门户只剩归集）
        // 不含 6403：系统管理「访问控制」已迁至归集 Hub，侧栏不再展示
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 1, visible = 1 WHERE id IN "
                        + "(1,2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,6400,6401,6402,6500)");
        jdbcTemplate.update(
                "UPDATE sys_menu SET status = 0, visible = 0 WHERE id = 6403 OR path = '/system/access'");
        int granted = jdbcTemplate.update(
                "INSERT INTO sys_role_menu (role_id, menu_id) "
                        + "SELECT 1, m.id FROM sys_menu m WHERE "
                        + "(m.status = 1 OR (m.permission IS NOT NULL AND m.permission <> '')) "
                        + "AND IFNULL(m.integration_type,'') <> 'catalog' "
                        + "AND IFNULL(m.menu_name,'') NOT LIKE '%D05%' "
                        + "AND IFNULL(m.menu_name,'') NOT LIKE '%已并入%' "
                        + "AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id)");
        if (granted > 0) {
            log.info("SYSTEM_ADMIN menu grants restored: {} rows", granted);
        }
    }
}
