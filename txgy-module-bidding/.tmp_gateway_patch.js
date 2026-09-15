
const fs = require('fs');
const p = "D:/Code/IDEA/txgy/txgycloud/txgy-gateway/src/main/resources/application.yaml";
let s = fs.readFileSync(p, 'utf8');
const routeBlock = `            ## bidding-server 服务
            - id: bidding-admin-api # 路由的编号
              uri: grayLb://bidding-server
              predicates:
                - Path=/admin-api/bidding/**
              filters:
                  - RewritePath=/admin-api/bidding/v3/api-docs, /v3/api-docs
`;
if (!s.includes('bidding-admin-api')) {
  const anchor = '            ## system-server 服务';
  s = s.replace(anchor, routeBlock + anchor);
  console.log('route inserted');
} else { console.log('route exists'); }
if (!s.includes('name: bidding-server')) {
  const knifeAnchor = '      - name: system-server';
  const knifeBlock = `      - name: bidding-server
        service-name: bidding-server
        url: /admin-api/bidding/v3/api-docs
`;
  s = s.replace(knifeAnchor, knifeBlock + knifeAnchor);
  console.log('knife4j inserted');
} else { console.log('knife4j exists'); }
fs.writeFileSync(p, s);
console.log('saved');
