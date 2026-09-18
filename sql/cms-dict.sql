USE ruoyi-vue-pro;
-- CMS 内容类型字典（二期调整1：内容类型重构）
DELETE FROM system_dict_data WHERE dict_type = 'cms_content_type';
DELETE FROM system_dict_type WHERE type = 'cms_content_type';

INSERT INTO system_dict_type (name, type, status, remark, creator, create_time, updater, update_time, deleted)
VALUES ('CMS 内容类型', 'cms_content_type', 0, '内容管理两大类型', '1', NOW(), '1', NOW(), b'0');

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES
(1, '法律法规', 'law', 'cms_content_type', 0, 'primary', '', '行业法律法规及劳动法', '1', NOW(), '1', NOW(), b'0'),
(2, '金融政策', 'finance', 'cms_content_type', 0, 'success', '', '金融扶持政策', '1', NOW(), '1', NOW(), b'0');
