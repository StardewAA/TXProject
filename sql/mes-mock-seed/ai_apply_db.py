# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
M = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def run(sql):
    p = subprocess.run(M + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return p.stdout.decode('utf-8', 'replace'), p.stderr.decode('utf-8', 'replace')
# 1) MES 生产助手（tenant 2010, id=20）替换角色设定
new_prompt = """你是天信管业防腐保温智慧平台的 MES 生产助手。
- 职责：仅处理生产/工单/报工/检验（IPQC）等业务查询
- 回复原则：≤3 句话，80 字以内，直入主题
- 超出范围：礼貌一句\"该问题不在 MES 业务范围内，请提供生产相关内容\"即可，不要解释原因
- 信息不全：直接反问 1 句要哪个字段，不要自己假设
- 数据呈现：表格用 markdown 表格，单条用\"字段：值\"格式
- 禁止：思考过程、推理链、\"让我分析一下\"等元话语
- 禁止：重复用户问题、重复自己的答案
- 禁止：任何 emoji、表情、装饰符号"""
sql = ("UPDATE ai_chat_role SET system_message='%s' WHERE id=20 AND tenant_id=2010;" %
       new_prompt.replace('\\', '\\\\').replace("'", "''"))
out, err = run(sql)
print('role20 update rc out=%r err=%s' % (out[:200], err[:200]))
# 2) 模型行 65（豆包）参数
out, err = run("UPDATE ai_model SET temperature=0.3, max_tokens=500 WHERE id=65 AND tenant_id=2010;")
print('model65 update out=%r err=%s' % (out[:200], err[:200]))
# 3) 已有 MES 对话参数同步（新建会话会从模型行复制，旧会话一并更新避免不一致）
out, err = run("UPDATE ai_chat_conversation SET temperature=0.3, max_tokens=500 WHERE role_id=20 AND tenant_id=2010;")
print('conversation update out=%r err=%s' % (out[:200], err[:200]))
# 4) 复核
out, _ = run("SELECT id, tenant_id, temperature, max_tokens, LEFT(system_message, 30) FROM ai_model WHERE id=65")
print('model65 now:', out)
out, _ = run("SELECT id, tenant_id, LEFT(system_message, 30) sm FROM ai_chat_role WHERE id=20 AND tenant_id=2010")
print('role20 now:', out)
out, _ = run("SELECT COUNT(*), MAX(temperature), MAX(max_tokens) FROM ai_chat_conversation WHERE role_id=20 AND tenant_id=2010")
print('convs now:', out)
