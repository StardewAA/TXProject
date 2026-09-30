USE `ruoyi-vue-pro`;
-- Legacy base-schema marker. Final ACL readiness is written by 011-ready.sql.
CREATE TABLE mall_release (
  version varchar(32) NOT NULL PRIMARY KEY,
  installed_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO mall_release(version) VALUES ('2026-09-24');
