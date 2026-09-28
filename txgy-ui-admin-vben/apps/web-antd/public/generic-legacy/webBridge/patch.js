const fs = require('fs');
const p = 'api.js';
let c = fs.readFileSync(p, 'utf8');
const old = 'o.src=function(t){return/localhost|(127.0.0.1)/.test(location.href)?"ofd"===t.document[0].awaitingSignatureFileType?"http://localhost:8858/web2/index.html":"http://localhost:8858/web/viewer.html":"ofd"===t.document[0].awaitingSignatureFileType?"/generic-legacy/web2/index.html":"/generic-legacy/web/viewer.html"}(n)+("?t="+ +new Date)';
const next = 'o.src=function(t){return"ofd"===t.document[0].awaitingSignatureFileType?"/generic-legacy/web2/index.html":"/generic-legacy/web/viewer.html"}(n)+("?t="+ +new Date)';
if (c.includes(old)) {
  fs.writeFileSync(p, c.replace(old, next));
  console.log('PATCHED');
} else {
  console.log('NOT_FOUND len=' + c.length);
  const idx = c.indexOf('o.src');
  console.log('o.src at ' + idx + ': ' + (idx >= 0 ? c.slice(idx, idx + 320) : ''));
}
