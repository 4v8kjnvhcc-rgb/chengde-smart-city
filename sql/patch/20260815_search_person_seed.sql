-- 同 Flyway V224：数据搜索写入 3 条自然人身份与文档
-- 目标库: smart_city；可重复执行

INSERT INTO ing_search_global_field (field_code, field_name, semantic, data_type, control_type, match_name_regex, match_comment_keywords, status, description)
SELECT 'GF_NAME', '姓名', '自然人姓名', 'STRING', 'INPUT',
       '.*(name|xm|姓名|person_name).*', '姓名,名称', 'ACTIVE', '模糊/组合查询常用'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_global_field WHERE field_code = 'GF_NAME');

INSERT INTO ing_search_global_field (field_code, field_name, semantic, data_type, control_type, match_name_regex, match_comment_keywords, status, description)
SELECT 'GF_GENDER', '性别', '自然人性别（男/女）', 'STRING', 'SELECT',
       '.*(gender|sex|xb|性别).*', '性别', 'ACTIVE', '组合查询常用'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_global_field WHERE field_code = 'GF_GENDER');

INSERT INTO ing_search_identity (entity_id, id_type, id_value, source_system, display_name, profile_json, related_docs, status)
SELECT 'PERSON_CD_001', 'ID_CARD', '130802199001011234', 'ods_person', '张伟',
       '{"name":"张伟","gender":"男","region":"承德市双桥区","nation":"汉族","tags":["常住人口"]}',
       'DOC_PERSON_CD_001', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM ing_search_identity WHERE id_type = 'ID_CARD' AND id_value = '130802199001011234'
);

INSERT INTO ing_search_document (doc_key, doc_type, asset_code, title, summary, content_text, tags_json, category_json,
  physical_table, weight_score, hot_score, identity_keys, high_attrs_json, mid_attrs_json, status, indexed_at)
SELECT 'IDENTITY:PERSON_CD_001', 'IDENTITY', 'PERSON_CD_001', '张伟 · 常住人口',
       '承德市双桥区常住人口档案',
       '姓名 张伟 性别 男 身份证号 130802199001011234 户籍 承德市双桥区 民族 汉族 联系方式脱敏',
       '["人口","常住","双桥"]', '{"domain":"人口","layer":"ODS"}',
       'ods_person_basic', 88.00, 12,
       '130802199001011234|张伟|男',
       '{"name":"张伟","idCard":"130802199001011234","gender":"男"}',
       '{"region":"承德市双桥区"}', 'ACTIVE', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_document WHERE doc_key = 'IDENTITY:PERSON_CD_001');

INSERT INTO ing_search_identity (entity_id, id_type, id_value, source_system, display_name, profile_json, related_docs, status)
SELECT 'PERSON_CD_002', 'ID_CARD', '130803198505052345', 'ods_person', '李娜',
       '{"name":"李娜","gender":"女","region":"承德市双滦区","nation":"汉族","tags":["常住人口"]}',
       'DOC_PERSON_CD_002', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM ing_search_identity WHERE id_type = 'ID_CARD' AND id_value = '130803198505052345'
);

INSERT INTO ing_search_document (doc_key, doc_type, asset_code, title, summary, content_text, tags_json, category_json,
  physical_table, weight_score, hot_score, identity_keys, high_attrs_json, mid_attrs_json, status, indexed_at)
SELECT 'IDENTITY:PERSON_CD_002', 'IDENTITY', 'PERSON_CD_002', '李娜 · 常住人口',
       '承德市双滦区常住人口档案',
       '姓名 李娜 性别 女 身份证号 130803198505052345 户籍 承德市双滦区 民族 汉族 联系方式脱敏',
       '["人口","常住","双滦"]', '{"domain":"人口","layer":"ODS"}',
       'ods_person_basic', 86.00, 9,
       '130803198505052345|李娜|女',
       '{"name":"李娜","idCard":"130803198505052345","gender":"女"}',
       '{"region":"承德市双滦区"}', 'ACTIVE', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_document WHERE doc_key = 'IDENTITY:PERSON_CD_002');

INSERT INTO ing_search_identity (entity_id, id_type, id_value, source_system, display_name, profile_json, related_docs, status)
SELECT 'PERSON_CD_003', 'ID_CARD', '130804199203153456', 'ods_person', '王强',
       '{"name":"王强","gender":"男","region":"承德市鹰手营子矿区","nation":"汉族","tags":["常住人口"]}',
       'DOC_PERSON_CD_003', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM ing_search_identity WHERE id_type = 'ID_CARD' AND id_value = '130804199203153456'
);

INSERT INTO ing_search_document (doc_key, doc_type, asset_code, title, summary, content_text, tags_json, category_json,
  physical_table, weight_score, hot_score, identity_keys, high_attrs_json, mid_attrs_json, status, indexed_at)
SELECT 'IDENTITY:PERSON_CD_003', 'IDENTITY', 'PERSON_CD_003', '王强 · 常住人口',
       '承德市鹰手营子矿区常住人口档案',
       '姓名 王强 性别 男 身份证号 130804199203153456 户籍 承德市鹰手营子矿区 民族 汉族 联系方式脱敏',
       '["人口","常住","鹰手营子"]', '{"domain":"人口","layer":"ODS"}',
       'ods_person_basic', 85.00, 7,
       '130804199203153456|王强|男',
       '{"name":"王强","idCard":"130804199203153456","gender":"男"}',
       '{"region":"承德市鹰手营子矿区"}', 'ACTIVE', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_document WHERE doc_key = 'IDENTITY:PERSON_CD_003');
