"""
扫描全库：找出所有"实际包含 /infra/file/ 值"的表.列，作为公开/私有列分类的权威依据。
只读，不做任何修改。

实现：先取 information_schema 里的文本列（1 次查询），再拼一条大 UNION SQL 一次性统计
（1 次查询），避免逐列往返导致超时。
"""
import subprocess, sys

DB = "ruoyi-vue-pro"
MYSQL = ["docker", "exec", "-i", "-e", "MYSQL_PWD=root", "deploy-mysql-1",
         "mysql", "-h127.0.0.1", "-uroot", "-N", "--default-character-set=utf8mb4", DB]


def q(sql, timeout=600):
    # SQL 通过 stdin 传入，避免命令行超长
    p = subprocess.run(MYSQL, input=sql.encode("utf-8"), capture_output=True, timeout=timeout)
    if p.returncode != 0:
        sys.stderr.write(p.stderr.decode("utf-8", "ignore") + "\n")
        raise RuntimeError("mysql failed")
    return [l for l in p.stdout.decode("utf-8", "ignore").split("\n") if l.strip() != ""]


def main():
    rows = q("""
        SELECT TABLE_NAME, COLUMN_NAME
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='%s'
          AND DATA_TYPE IN ('varchar','text','mediumtext','longtext','json','char')
        ORDER BY TABLE_NAME, COLUMN_NAME
    """ % DB)
    cols = []
    for line in rows:
        p = line.split("\t")
        if len(p) == 2:
            cols.append((p[0], p[1]))
    print("候选文本列: %d" % len(cols), flush=True)

    # 拼一条大 UNION
    parts = []
    for t, c in cols:
        parts.append("SELECT '%s.%s' AS col, SUM(`%s` LIKE '%%/infra/file/%%') AS n FROM `%s`"
                     % (t, c, c, t))
    sql = "SELECT col, n FROM (" + " UNION ALL ".join(parts) + ") x WHERE n > 0 ORDER BY n DESC"
    out = q(sql)
    print("=== 实际包含 /infra/file/ 的列 (%d) ===" % len(out), flush=True)
    for line in out:
        p = line.split("\t")
        if len(p) == 2:
            print("%-55s %s" % (p[0], p[1]), flush=True)


if __name__ == "__main__":
    main()
