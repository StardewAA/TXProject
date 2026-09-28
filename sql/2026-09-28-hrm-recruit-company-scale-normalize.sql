-- HRM：把「企业规模」存量值归并到 8 档标准区间
--
-- 背景：该字段原先是管理端的自由文本框（placeholder「如：50-99 人」），
--   同一档出现过 13 种写法，含 50-60 / 59-89 / 100~999 这类非标准区间，
--   以及 "50-99人" 与 "50-99 人" 有无空格两种。
--   门户「企业名录」的规模筛选项是从数据里现算的
--   （company-directory.vue 的 scales computed），所以这些乱值会原样
--   并排显示给求职者；管理端改用下拉后，旧值也不在选项列表里、选不回去。
--
-- 归并口径：最接近档；跨档的取区间更大的那一档
--   30-60      → 50-99人   （横跨 1-49 与 50-99）
--   100~999    → 100-499人 （横跨 100-499 与 500-999）
--   1000 人以上 → 1000-2000人（原意开区间，取最接近的档）
--
-- 幂等：WHERE 用精确匹配旧值，重复执行不会误伤已归并的行。

UPDATE `hrm_recruit_company_profile`
SET `scale` = '100-499人'
WHERE `scale` IN ('100-500人', '100-500 人', '100~999');

UPDATE `hrm_recruit_company_profile`
SET `scale` = '50-99人'
WHERE `scale` IN ('50-99', '50-99人', '50-99 人', '50-60', '59-89', '60-70', '30-60');

UPDATE `hrm_recruit_company_profile`
SET `scale` = '1-49人'
WHERE `scale` IN ('0-49人', '30-49');

UPDATE `hrm_recruit_company_profile`
SET `scale` = '1000-2000人'
WHERE `scale` IN ('1000 人以上');

-- 核对：应只剩标准档位
-- SELECT scale, COUNT(*) FROM hrm_recruit_company_profile
-- WHERE scale IS NOT NULL AND scale <> '' GROUP BY scale ORDER BY scale;
