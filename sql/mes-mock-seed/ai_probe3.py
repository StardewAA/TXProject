# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
M = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(M + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return p.stdout.decode('utf-8', 'replace') if p.returncode == 0 else 'ERR ' + p.stderr.decode('utf-8', 'replace')[:300]
print('--- model rows (chat type) all ---')
print(q("SELECT id, tenant_id, key_id, name, model, platform, type, status, temperature, max_tokens, max_contexts FROM ai_model WHERE id BETWEEN 60 AND 68 ORDER BY id"))
print('--- role 20 model id check tool key model? ---')
print(q("SELECT id, key_id, name, platform FROM ai_api_key WHERE id IN (SELECT key_id FROM ai_model WHERE id=65)"))
