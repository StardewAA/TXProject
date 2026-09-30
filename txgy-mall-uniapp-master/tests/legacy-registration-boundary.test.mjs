import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

test('mobile compatibility wrapper never sends credentials to the fixed-tenant registration endpoint', () => {
  const source = readFileSync(new URL('../sheep/api/member/auth.js', import.meta.url), 'utf8');
  const formSource = readFileSync(new URL('../sheep/hooks/useMallAuthForm.js', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /url:\s*['"]\/member\/auth\/register['"]/);
  assert.match(source, /register:\s*\(_data\)\s*=>\s*Promise\.reject/);
  assert.match(formSource, /endpoint\s*=\s*useImage\s*\?\s*['"]register-image['"]/);
});
