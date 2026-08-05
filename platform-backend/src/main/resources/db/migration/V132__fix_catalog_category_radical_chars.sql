-- 修正分类名称中的康熙部首异体字（⽬ U+2F6C → 目、⻔ U+2ED4 → 门），避免前端按标准名匹配失败

UPDATE gov_catalog_category
SET category_name = REPLACE(REPLACE(category_name, UNHEX('E2BDAC'), '目'), UNHEX('E2BB94'), '门'),
    category_path = REPLACE(REPLACE(IFNULL(category_path, ''), UNHEX('E2BDAC'), '目'), UNHEX('E2BB94'), '门')
WHERE category_name LIKE CONCAT('%', UNHEX('E2BDAC'), '%')
   OR category_name LIKE CONCAT('%', UNHEX('E2BB94'), '%')
   OR IFNULL(category_path, '') LIKE CONCAT('%', UNHEX('E2BDAC'), '%')
   OR IFNULL(category_path, '') LIKE CONCAT('%', UNHEX('E2BB94'), '%');
