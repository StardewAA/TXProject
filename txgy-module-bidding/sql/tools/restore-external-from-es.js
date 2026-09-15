const crypto = require('crypto');
const SRC = { QYZB: '秦源电子招标平台', GX: '国信e采（山西）', EBIDDING: '秦源招标电子招标平台' };
async function main() {
  const auth = 'Basic ' + Buffer.from('elastic:ww0qPgaEbSyT6oMMeART').toString('base64');
  const res = await fetch('http://127.0.0.1:9200/bidding-notice-external/_search?size=1000', {
    headers: { Authorization: auth, 'Content-Type': 'application/json' },
    method: 'POST', body: JSON.stringify({ query: { match_all: {} }, sort: [{ id: 'asc' }] })
  });
  if (!res.ok) { console.error('ES status', res.status, (await res.text()).slice(0, 200)); process.exit(3); }
  const j = await res.json();
  if (!j.hits || !j.hits.hits) { console.error('no hits', JSON.stringify(j).slice(0, 200)); process.exit(3); }
  const hits = j.hits.hits;
  console.error('ES docs:', hits.length);
  const esc = v => v === null || v === undefined ? 'NULL' : "'" + String(v).replace(/\\/g, '\\\\').replace(/'/g, "''") + "'";
  const out = [];
  for (const h of hits) {
    const d = h._source, id = h._id;
    const hash = crypto.createHash('sha256').update(d.origUrl || '').digest('hex');
    const isPdf = d.contentType === 'PDF';
    const vals = [id, d.sourceCode, SRC[d.sourceCode] || d.sourceCode, d.title, d.origUrl, hash,
      isPdf ? 'PDF' : 'HTML', 'ACTIVE', 'APPROVED', 'SOCIAL', d.publishTime || null,
      d.issuerName, d.projectCode, d.projectName, d.tenderProjectCode, d.bidSectionCodes,
      d.industryFlag, d.legalFlag, isPdf ? (d.contentText || null) : null,
      isPdf && d.contentText ? 'SUCCESS' : null, '{}'];
    out.push('INSERT IGNORE INTO bidding_notice_external (id, source_code, source_name, title, orig_url, orig_url_hash, content_type, status, audit_status, public_scope, publish_time, issuer_name, project_code, project_name, tender_project_code, bid_section_codes, industry_flag, legal_flag, extraction_text, extraction_status, meta_json) VALUES (' + vals.map(esc).join(',') + ');');
  }
  process.stdout.write(out.join('\n') + '\n');
  console.error('rows:', out.length);
}
main().catch(e => { console.error('ERR', e); process.exit(2); });
