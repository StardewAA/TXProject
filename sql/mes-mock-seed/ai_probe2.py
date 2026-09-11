# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
M = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(M + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return p.stdout.decode('utf-8', 'replace') if p.returncode == 0 else 'ERR ' + p.stderr.decode('utf-8', 'replace')[:300]
print('--- ai_model columns ---')
print(q("SELECT column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='ai_model' ORDER BY ordinal_position"))
print('--- model 60-66 ---')
print(q("SELECT id, tenant_id, name, platform, model, temperature, max_tokens, status, LEFT(api_key,6) ak FROM ai_model WHERE id BETWEEN 60 AND 66"))
print('--- role20 full system_message ---')
print(q("SELECT system_message FROM ai_chat_role WHERE id=20 AND tenant_id=2010"))
print('--- conversation recent (role 20) params ---')
print(q("SELECT id, user_id, model_id, temperature, max_tokens, max_contexts, LEFT(system_message,40) sm FROM ai_chat_conversation WHERE role_id=20 AND tenant_id=2010 ORDER BY id DESC LIMIT 6"))
print('--- tool ids 22-27 desc full (MES) ---')
print(q("SELECT id, name, LEFT(description,120) FROM ai_tool WHERE id IN (22,23,24,25,26,27)"))
