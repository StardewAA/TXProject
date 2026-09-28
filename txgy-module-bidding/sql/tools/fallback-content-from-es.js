const auth = 'Basic ' + Buffer.from('elastic:ww0qPgaEbSyT6oMMeART').toString('base64');
(async () => {
  const res = await fetch('http://127.0.0.1:9200/bidding-notice-external/_search?size=1000', {
    headers: { Authorization: auth, 'Content-Type': 'application/json' },
    method: 'POST', body: JSON.stringify({ query: { match_all: {} }, sort: [{ id: 'asc' }] })
  });
  const j = await res.json();
  const esc = v => String(v).replace(/\\/g, '\\\\').replace(/'/g, "''");
  const out = [];
  for (const h of j.hits.hits) {
    const d = h._source;
    if (d.contentType === 'HTML' && d.contentText) {
      const html = '<p>' + esc(d.contentText).slice(0, 200000) + '</p>';
      out.push("UPDATE bidding_notice_external SET content_html = '" + html + "' WHERE id = " + h._id + " AND (content_html IS NULL OR content_html = '');");
    }
  }
  process.stdout.write(out.join('\n') + '\n');
  console.error('fallback rows:', out.length);
})();