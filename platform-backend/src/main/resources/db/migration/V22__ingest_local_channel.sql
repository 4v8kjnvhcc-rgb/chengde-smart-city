-- 本地文件接入通道（采集汇聚文件上传 Tab）
INSERT INTO ing_ingest_channel (channel_code, channel_name, channel_type, config_json, status) VALUES
('CH_LOCAL', '本地文件上传', 'LOCAL', '{"localPath":"/data/upload","writeMode":"append"}', 'IDLE')
ON DUPLICATE KEY UPDATE channel_name = VALUES(channel_name);
