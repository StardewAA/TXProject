/* 审批工作台 v2 前端可行性：三个台账分页接口的参数行为（真实数据） */
const { execSync } = require('child_process');
const API = 'http://127.0.0.1:48080/admin-api';
(async () => {
  const l = await (await fetch(API + '/system/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'tenant-id': '1' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })).json();
  const t = l.data.accessToken;
  const H = { Authorization: 'Bearer ' + t, 'tenant-id': '1' };
  const sql = "SELECT project_code, invest_project_code FROM bidding_project WHERE deleted=0 AND invest_project_code IS NOT NULL AND invest_project_code<>'' LIMIT 1;";
  const row = execSync(
    'docker exec -i txgy-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 --raw -N -B ruoyi-vue-pro -e ' + JSON.stringify(sql),
    { encoding: 'utf8' },
  ).trim().split('	');
  const pc = row[0]; const ic = row[1];
  console.log('sample projectCode=' + pc + ' investCode=' + ic);
  const g = async (u) => {
    const r = await (await fetch(API + u, { headers: H })).json();
    return r.data ? ('total=' + r.data.total) : ('code=' + r.code + ' msg=' + (r.msg || ''));
  };
  console.log('1 /project/page 全量          ->', await g('/bidding/project/page?pageNo=1&pageSize=5'));
  console.log('2 /project/page?projectCode   ->', await g('/bidding/project/page?pageNo=1&pageSize=5&projectCode=' + encodeURIComponent(pc)));
  console.log('3 /project/page?projectName   ->', await g('/bidding/project/page?pageNo=1&pageSize=5&projectName=' + encodeURIComponent('LNG')));
  console.log('4 /project/page?investProjectCode ->', await g('/bidding/project/page?pageNo=1&pageSize=5&investProjectCode=' + encodeURIComponent(ic)));
  console.log('5 /tender-project/page?tenderProjectCode(不存在) ->', await g('/bidding/tender-project/page?pageNo=1&pageSize=5&tenderProjectCode=ZZZ-NOT-EXIST'));
  console.log('6 /bid-section/page?bidSectionCode(不存在) ->', await g('/bidding/bid-section/page?pageNo=1&pageSize=5&bidSectionCode=ZZZ-NOT-EXIST'));
})();
