#!/bin/sh
# 逐张验证 report_dataset.sql_text 可执行（替换日期参数为默认值后包裹 COUNT）
PW=root
DB=ruoyi-vue-pro
M="mysql -uroot -p$PW --default-character-set=utf8mb4 -N -B $DB"
for id in 1 3 4 5 6 7 8 9 10 11 12 13; do
  code=$($M -e "SELECT code FROM report_dataset WHERE id=$id AND deleted=0")
  if [ -z "$code" ]; then
    echo "id=$id MISSING"
    continue
  fi
  $M -e "SELECT sql_text FROM report_dataset WHERE id=$id" \
    | sed "s/\${startDate}/'2000-01-01'/g; s/\${endDate}/'2099-12-31'/g" > /tmp/q.sql
  result=$({ echo "SELECT COUNT(*) FROM ("; cat /tmp/q.sql; echo ") t;"; } \
    | mysql -uroot -p$PW --default-character-set=utf8mb4 $DB 2>&1 | grep -v insecure)
  case "$result" in
    ERROR*) echo "id=$id $code FAIL: $result" ;;
    "")     echo "id=$id $code EMPTY-RESULT" ;;
    *)      echo "id=$id $code OK rows=$result" ;;
  esac
done
