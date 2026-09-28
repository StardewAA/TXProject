# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
def run(tag, M):
    print('=' * 20, tag, '=' * 20)
    def q(sql):
        p = subprocess.run(M + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        return p.stdout.decode('utf-8', 'replace') if p.returncode == 0 else 'ERR ' + p.stderr.decode('utf-8', 'replace')[:300]
    print('--- ai_chat_role columns ---')
    print(q("SELECT column_name, data_type, character_maximum_length FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_chat_role' ORDER BY ordinal_position"))
    print('--- roles (id, tenant, name, model, status, public, has tools) ---')
    print(q("SELECT id, tenant_id, user_id, name, model_id, public_status, status, LEFT(system_message,40) AS sm, tool_ids FROM ai_chat_role ORDER BY id"))
    print('--- models (doubao / seed) ---')
    print(q("SELECT id, tenant_id, name, platform, model, temperature, max_tokens, top_p, LEFT(api_key,8) ak FROM ai_model WHERE model LIKE '%seed%' OR name LIKE '%豆包%' OR platform='Doubao'"))
    print('--- conversation columns ---')
    print(q("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_chat_conversation' ORDER BY ordinal_position"))
    print('--- conversations role 20 recent ---')
    print(q("SELECT id, tenant_id, user_id, role_id, model_id, LEFT(system_message,30) sm, temperature, max_tokens, top_p FROM ai_chat_conversation WHERE role_id=20 ORDER BY id DESC LIMIT 5"))
    print('--- ai_tool ---')
    print(q("SELECT id, name, LEFT(description,50) dsc FROM ai_tool ORDER BY id"))
run('DOCKER 13306', ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro'])
run('HOST 3306', ['mysql', '-h127.0.0.1', '-P3306', '-uroot', '-proot', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro'])
