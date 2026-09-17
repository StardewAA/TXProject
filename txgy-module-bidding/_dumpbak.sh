#!/bin/sh
cd /tmp || exit 1
mysqldump --default-character-set=utf8mb4 -uroot -p123456 --no-create-info --skip-comments ruoyi-vue-pro bidding_notice_external > ext_backup.sql 2>/dev/null
mysqldump --default-character-set=utf8mb4 -uroot -p123456 --no-create-info --skip-comments ruoyi-vue-pro bidding_fetch_task bidding_fetch_job > crawl_backup.sql 2>/dev/null
wc -c ext_backup.sql crawl_backup.sql
