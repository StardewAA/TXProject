# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:500]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
print('== all columns matching user-ish names on mes_ tables ==')
print(q("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' AND (column_name LIKE '%user%' OR column_name LIKE '%creator%' OR column_name LIKE '%updater%' OR column_name LIKE '%_by%' OR column_name LIKE '%person%') ORDER BY table_name, column_name"))
print('== columns referencing dept/area/location/machinery/tool/warehouse like x_id (for info) sample head ==')
print(q("SELECT column_name, COUNT(*) FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' AND column_name='dept_id' GROUP BY column_name"))
print(q("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' AND column_name IN ('dept_id','post_id') ORDER BY table_name"))
