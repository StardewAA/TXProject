-- 乱码还原（GBK 字节被当 utf8mb4 收下）2026-09-10
-- 生成自 e2e/fix_mojibake_values.py：每一条都验过 rt(原串) == 库里的坏串，
-- 不是猜的。原样保留仍未还原的行，见脚本输出的「还原不了」清单。
-- 只能整文件喂：mysql --default-character-set=utf8mb4 < 本文件

SET NAMES utf8mb4;

UPDATE `mes_set_standard` SET `remark`='P0冒烟' WHERE id=1;
