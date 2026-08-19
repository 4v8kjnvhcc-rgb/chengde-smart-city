-- 指标结果表：calc_at → update_time（注释：更新时间），列顺序移至末尾
-- 目标库：各指标结果库（非控制面 smart_city；见 app.indicator-db / INDICATOR_DB_*）
-- 可重复执行：无 calc_at 时跳过改名；已有 update_time 时仅校正注释/位置/索引
--
-- 说明：
--   1) 应用 IndicatorJdbcSupport.ensureResultTable / 任务执行时会自动迁移
--   2) 运维也可对本脚本「当前库」内所有 ind_% 表手工跑一遍
--
-- 执行顺序：先 USE 目标指标库，再执行下方过程；每个指标库各执行一次。

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_mig_ind_calc_at_to_update_time $$
CREATE PROCEDURE sp_mig_ind_calc_at_to_update_time()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE v_table VARCHAR(64);
  DECLARE v_after VARCHAR(64);
  DECLARE v_has_calc INT;
  DECLARE v_has_upd INT;
  DECLARE cur CURSOR FOR
    SELECT TABLE_NAME
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_TYPE = 'BASE TABLE'
       AND TABLE_NAME LIKE 'ind\_%' ESCAPE '\\';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_table;
    IF done = 1 THEN
      LEAVE read_loop;
    END IF;

    SELECT COUNT(*) INTO v_has_calc
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table AND COLUMN_NAME = 'calc_at';
    SELECT COUNT(*) INTO v_has_upd
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table AND COLUMN_NAME = 'update_time';

    IF v_has_calc = 1 AND v_has_upd = 0 THEN
      SELECT COLUMN_NAME INTO v_after
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = v_table
         AND COLUMN_NAME <> 'calc_at'
       ORDER BY ORDINAL_POSITION DESC
       LIMIT 1;
      IF v_after IS NULL OR v_after = '' THEN
        SET @sql = CONCAT(
          'ALTER TABLE `', v_table,
          '` CHANGE COLUMN `calc_at` `update_time` DATETIME NOT NULL COMMENT ''更新时间'''
        );
      ELSE
        SET @sql = CONCAT(
          'ALTER TABLE `', v_table,
          '` CHANGE COLUMN `calc_at` `update_time` DATETIME NOT NULL COMMENT ''更新时间'' AFTER `', v_after, '`'
        );
      END IF;
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
      SET v_has_upd = 1;
      SET v_has_calc = 0;
    ELSEIF v_has_upd = 0 THEN
      SET @sql = CONCAT(
        'ALTER TABLE `', v_table,
        '` ADD COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''更新时间'''
      );
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
      SET v_has_upd = 1;
    END IF;

    IF v_has_calc = 1 AND v_has_upd = 1 THEN
      SET @sql = CONCAT(
        'UPDATE `', v_table,
        '` SET `update_time` = COALESCE(`update_time`, `calc_at`)'
      );
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
      SET @sql = CONCAT('ALTER TABLE `', v_table, '` DROP COLUMN `calc_at`');
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF v_has_upd = 1 THEN
      SELECT COLUMN_NAME INTO v_after
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = v_table
         AND COLUMN_NAME <> 'update_time'
       ORDER BY ORDINAL_POSITION DESC
       LIMIT 1;
      IF v_after IS NULL OR v_after = '' THEN
        SET @sql = CONCAT(
          'ALTER TABLE `', v_table,
          '` MODIFY COLUMN `update_time` DATETIME NOT NULL COMMENT ''更新时间'''
        );
      ELSE
        SET @sql = CONCAT(
          'ALTER TABLE `', v_table,
          '` MODIFY COLUMN `update_time` DATETIME NOT NULL COMMENT ''更新时间'' AFTER `', v_after, '`'
        );
      END IF;
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table AND INDEX_NAME = 'idx_calc_at'
    ) THEN
      SET @sql = CONCAT('ALTER TABLE `', v_table, '` DROP INDEX `idx_calc_at`');
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF v_has_upd = 1 AND NOT EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table AND INDEX_NAME = 'idx_update_time'
    ) THEN
      SET @sql = CONCAT('ALTER TABLE `', v_table, '` ADD INDEX `idx_update_time` (`update_time`)');
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END LOOP;
  CLOSE cur;
END $$

DELIMITER ;

CALL sp_mig_ind_calc_at_to_update_time();
DROP PROCEDURE IF EXISTS sp_mig_ind_calc_at_to_update_time;
