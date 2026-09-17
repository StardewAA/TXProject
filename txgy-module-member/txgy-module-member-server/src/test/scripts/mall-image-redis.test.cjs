'use strict';
// Executes actual production Lua against an owned, disposable Redis. No project data or published ports.
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync, execFile } = require('node:child_process');
const { promisify } = require('node:util');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { randomUUID } = require('node:crypto');
const run = promisify(execFile);
const scripts = Object.fromEntries(['issue', 'consume', 'budget'].map(name =>
  [name, readFileSync(join(__dirname, '../../main/resources/lua/mall-image-' + name + '.lua'), 'utf8')]));
const docker = args => execFileSync('docker', args, { encoding: 'utf8' }).trim();
let container;
const redis = (...args) => docker(['exec', container, 'redis-cli', '--raw', ...args.map(String)]);
const lua = (name, keys, args) => Number(redis('EVAL', scripts[name], keys.length, ...keys, ...args));
before(async () => {
  container = docker(['run', '--rm', '-d', '--name', 'mall-image-lua-test-' + randomUUID().slice(0, 8),
    '--label', 'com.txgy.test=mall-image-lua', '--network', 'none', '--memory', '96m',
    '--pull', 'never', 'redis:7-alpine', 'redis-server', '--save', '', '--appendonly', 'no']);
  assert.match(container, /^[a-f0-9]{64}$/);
  for (let i = 0; i < 30; i++) {
    try { if (redis('PING') === 'PONG') return; } catch {}
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Isolated Redis did not become ready');
});
after(() => {
  if (!container) return;
  const info = JSON.parse(docker(['inspect', container]))[0];
  assert.equal(info.Config.Labels['com.txgy.test'], 'mall-image-lua');
  assert.equal(info.HostConfig.NetworkMode, 'none');
  docker(['stop', container]); // --rm removes only this exact owned test container; no volumes are created.
});
function fixture() {
  const prefix = 'test:{mall-image}:' + randomUUID() + ':';
  const issueKeys = ['challenge', 'active-session', 'cooldown-session', 'issue-name', 'issue-session', 'issue-peer'].map(k => prefix + k);
  const consumeKeys = [issueKeys[0], issueKeys[1], ...['verify-name', 'verify-session', 'verify-peer'].map(k => prefix + k)];
  const issueArgs = ['id-a', 'binding-a', 'digest-a', '300', '2', '30', '30', '300', '900'];
  const consumeArgs = ['id-a', 'binding-a', 'digest-a', '5', '30', '30', '300', '900'];
  return { prefix, issueKeys, consumeKeys, issueArgs, consumeArgs,
    issue: () => lua('issue', issueKeys, issueArgs), consume: () => lua('consume', consumeKeys, consumeArgs) };
}
test('challenge consumes once and cannot replay', () => {
  const f = fixture(); assert.equal(f.issue(), 1); assert.equal(f.consume(), 1); assert.equal(f.consume(), 0);
  assert.equal(redis('EXISTS', f.issueKeys[0], f.issueKeys[1]), '0');
});
test('twenty simultaneous submissions have exactly one success', async () => {
  const f = fixture(); f.issue();
  const results = await Promise.all(Array.from({ length: 20 }, () => run('docker', ['exec', container, 'redis-cli', '--raw',
    'EVAL', scripts.consume, '5', ...f.consumeKeys, ...f.consumeArgs], { encoding: 'utf8' }).then(r => Number(r.stdout.trim()))));
  assert.equal(results.filter(r => r === 1).length, 1);
  assert.equal(results.filter(r => r === 0).length, 19);
});
for (const kind of ['tenant', 'purpose', 'login-name', 'session']) test('wrong ' + kind + ' cannot consume or exhaust original image', () => {
  const f = fixture(); f.issue(); const args = [...f.consumeArgs]; args[1] = 'wrong-' + kind;
  for (let i = 0; i < 5; i++) assert.equal(lua('consume', f.consumeKeys, args), 0);
  assert.equal(redis('HGET', f.issueKeys[0], 'attempts'), '0');
  assert.equal(f.consume(), 1);
});
test('five wrong answers destroy image and original correct answer then fails', () => {
  const f = fixture(); f.issue(); const args = [...f.consumeArgs]; args[2] = 'wrong-answer';
  for (let i = 0; i < 5; i++) assert.equal(lua('consume', f.consumeKeys, args), 0);
  assert.equal(redis('EXISTS', f.issueKeys[0], f.issueKeys[1]), '0');
  assert.equal(f.consume(), 0);
});
test('correct fifth attempt still succeeds after four failures', () => {
  const f = fixture(); f.issue(); const args = [...f.consumeArgs]; args[2] = 'wrong-answer';
  for (let i = 0; i < 4; i++) lua('consume', f.consumeKeys, args);
  assert.equal(f.consume(), 1);
});
test('expired image cannot authenticate while active session key still exists', async () => {
  const f = fixture(); f.issue(); redis('PEXPIRE', f.issueKeys[0], 1);
  await new Promise(resolve => setTimeout(resolve, 20));
  assert.equal(f.consume(), 0);
});
test('cross-tenant refresh for same session supersedes old image, which cannot delete the new one', () => {
  const f = fixture(); f.issue(); redis('DEL', f.issueKeys[2]);
  const keys = [...f.issueKeys]; keys[0] += '-new';
  const args = [...f.issueArgs]; args[0] = 'id-b'; args[1] = 'other-tenant-binding'; args[2] = 'digest-b';
  assert.equal(lua('issue', keys, args), 1);
  for (let i = 0; i < 5; i++) assert.equal(f.consume(), 0);
  assert.equal(redis('GET', f.issueKeys[1]), 'id-b');
  const consumeArgs = [...f.consumeArgs]; consumeArgs[0] = 'id-b'; consumeArgs[1] = args[1]; consumeArgs[2] = args[2];
  assert.equal(lua('consume', [keys[0], ...f.consumeKeys.slice(1)], consumeArgs), 1);
});
test('two-second cooldown applies across tenant and name changes in same session', () => {
  const f = fixture(); f.issue();
  assert(Number(redis('PTTL', f.issueKeys[2])) <= 2000);
  const keys = [...f.issueKeys]; keys[0] += '-next'; keys[3] += '-other-name';
  const args = [...f.issueArgs]; args[0] = 'id-b'; args[1] = 'new-tenant-name';
  assert.equal(lua('issue', keys, args), -1);
  assert.equal(redis('GET', f.issueKeys[1]), 'id-a');
  assert.equal(redis('EXISTS', keys[3]), '0');
});
for (const [index, kind] of [[3, 'login-name'], [4, 'session'], [5, 'peer']]) test('issuance ' + kind + ' budget stops rotating other dimensions', () => {
  const f = fixture(); f.issueArgs[index + 2] = '1'; f.issue();
  const keys = f.issueKeys.map((key, i) => i === index ? key : key + '-other');
  assert.equal(lua('issue', keys, f.issueArgs), -1);
  assert.equal(redis('EXISTS', keys[0]), '0');
});
for (const [index, kind] of [[2, 'login-name'], [3, 'session'], [4, 'peer']]) test('verification ' + kind + ' budget counts missing IDs and cannot reset by tenant change', () => {
  const f = fixture(); f.issue(); f.consumeArgs[index + 2] = '1';
  const keys = [...f.consumeKeys]; keys[0] += '-missing';
  assert.equal(lua('consume', keys, f.consumeArgs), 0);
  assert.equal(f.consume(), -2);
});
test('challenge, active session and all counters have bounded TTLs', () => {
  const f = fixture(); f.issue();
  for (const key of f.issueKeys) assert(Number(redis('TTL', key)) > 0);
  assert(Number(redis('TTL', f.issueKeys[0])) <= 300);
  assert(Number(redis('TTL', f.issueKeys[3])) <= 900);
  f.consume();
  for (const key of f.consumeKeys.slice(2)) assert(Number(redis('TTL', key)) > 0);
});
test('password account and peer budgets are atomic and expire', () => {
  const f = fixture(); const keys = [f.prefix + 'password-account', f.prefix + 'password-peer'];
  assert.equal(lua('budget', keys, ['1', '2', '900']), 1);
  assert.equal(lua('budget', keys, ['1', '2', '900']), 0);
  assert.equal(redis('GET', keys[1]), '1');
  assert.equal(lua('budget', [keys[0] + '-other', keys[1]], ['1', '2', '900']), 1);
  assert.equal(lua('budget', [keys[0] + '-third', keys[1]], ['1', '2', '900']), 0);
  for (const key of keys) assert(Number(redis('TTL', key)) > 0);
});
