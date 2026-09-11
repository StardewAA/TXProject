# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
M = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(M + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return (p.stdout.decode('utf-8', 'replace'), p.stderr.decode('utf-8', 'replace'))
for sql in [
    "SELECT id, tenant_id, name, model, platform, status, temperature, max_tokens FROM ai_model WHERE id=65",
    "SELECT id, name, LEFT(system_message,60) sm FROM ai_chat_role WHERE id=20 AND tenant_id=2010",
    "SELECT COUNT(*) FROM ai_chat_conversation WHERE role_id=20 AND tenant_id=2010 AND temperature=0.3 AND max_tokens=500",
]:
    out, err = q(sql)
    print(sql[:40], '=>', out.strip(), err.strip()[:200])
