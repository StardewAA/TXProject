ALTER TABLE hrm_personal_resume
    ADD COLUMN source_type tinyint NOT NULL DEFAULT 1 COMMENT '附件来源：1用户上传，2制作生成' AFTER resume_type,
    ADD COLUMN avatar_url varchar(512) DEFAULT NULL COMMENT '简历头像地址，仅用于页面展示' AFTER file_url,
    MODIFY COLUMN content mediumtext NULL COMMENT '在线简历内容或附件提取纯文本';

ALTER TABLE hrm_recruit_post
    ADD COLUMN work_address varchar(255) DEFAULT NULL COMMENT '具体工作地址' AFTER area_id,
    ADD COLUMN longitude decimal(10,6) DEFAULT NULL COMMENT '工作地址经度' AFTER work_address,
    ADD COLUMN latitude decimal(10,6) DEFAULT NULL COMMENT '工作地址纬度' AFTER longitude;
