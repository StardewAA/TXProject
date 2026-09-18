'use strict';
// Real Lua regression in a disposable, network-less Redis container. Never connects to project Redis.
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync, execFile } = require('node:child_process');
const { promisify } = require('node:util');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { randomUUID } = require('node:crypto');
const run = promisify(execFile);
let container;
const scripts = Object.fromEntries(['reserve', 'ready', 'consume', 'cancel', 'budget'].map(name =>
  [name, readFileSync(join(__dirname, '../../main/resources/lua/mall-auth-' + name + '.lua'), 'utf8')]));
const docker = args => execFileSync('docker', args, { encoding: 'utf8' }).trim();
const redis = (...args) => docker(['exec', container, 'redis-cli', '--raw', ...args.map(String)]);
const lua = (name, keys, args) => Number(redis('EVAL', scripts[name], keys.length, ...keys, ...args));
before(async () => {
  container = docker(['run', '--rm', '-d', '--name', 'mall-auth-lua-test-' + randomUUID().slice(0, 8),
    '--label', 'com.txgy.test=mall-auth-lua', '--network', 'none', '--memory', '96m',
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
  assert.equal(info.Config.Labels['com.txgy.test'], 'mall-auth-lua');
  assert.equal(info.HostConfig.NetworkMode, 'none');
  docker(['stop', container]); // --rm removes only this exact owned test container; no volumes created.
});
function fixture() {
  const prefix = 'test:{mall-auth}:' + randomUUID() + ':';
  const keys = [prefix + 'challenge', prefix + 'active', prefix + 'cooldown', prefix + 'daily', prefix + 'peer'];
  const consumeKeys = [keys[0], keys[1], prefix + 'verify-phone', prefix + 'verify-peer'];
  const args = ['id-a', 'binding-a', 'digest-a', '300', '60', '10', '100'];
  const verify = ['id-a', 'binding-a', 'digest-a', '5', '30', '300'];
  return { prefix, keys, consumeKeys, args, verify,
    reserve: () => lua('reserve', keys, args), ready: () => lua('ready', keys.slice(0, 2), ['id-a']),
    consume: () => lua('consume', consumeKeys, verify) };
}
test('pending cannot consume; ready consumes exactly once; replay denied', () => {
  const f = fixture(); assert.equal(f.reserve(), 1); assert.equal(f.consume(), 0);
  assert.equal(f.ready(), 1); assert.equal(f.consume(), 1); assert.equal(f.consume(), 0);
});
test('twenty simultaneous consume requests have exactly one winner', async () => {
  const f = fixture(); f.reserve(); f.ready();
  const results = await Promise.all(Array.from({ length: 20 }, () => run('docker', ['exec', container, 'redis-cli', '--raw',
    'EVAL', scripts.consume, '4', ...f.consumeKeys, ...f.verify], { encoding: 'utf8' }).then(r => Number(r.stdout.trim()))));
  assert.equal(results.filter(result => result === 1).length, 1); assert.equal(results.filter(result => result === 0).length, 19);
});
for (const kind of ['tenant', 'mobile', 'purpose']) test('wrong ' + kind + ' cannot consume or invalidate original', () => {
  const f = fixture(); f.reserve(); f.ready(); const wrong = [...f.verify]; wrong[1] = 'different-' + kind;
  assert.equal(lua('consume', f.consumeKeys, wrong), 0);
  assert.equal(redis('HGET', f.keys[0], 'attempts'), '0'); assert.equal(f.consume(), 1);
});
test('five incorrect codes exhaust a challenge; a later correct code is denied', () => {
  const f = fixture(); f.reserve(); f.ready(); const wrong = [...f.verify]; wrong[2] = 'bad-digest';
  for (let i = 0; i < 5; i++) assert.equal(lua('consume', f.consumeKeys, wrong), 0);
  assert.equal(redis('EXISTS', f.keys[0]), '0'); assert.equal(f.consume(), 0);
});
test('four incorrect codes still allow the final correct code', () => {
  const f = fixture(); f.reserve(); f.ready(); const wrong = [...f.verify]; wrong[2] = 'bad-digest';
  for (let i = 0; i < 4; i++) lua('consume', f.consumeKeys, wrong);
  assert.equal(f.consume(), 1);
});
test('challenge expiry fails closed without recreating it on delayed send success', async () => {
  const f = fixture(); f.reserve(); redis('PEXPIRE', f.keys[0], '1');
  await new Promise(resolve => setTimeout(resolve, 20));
  assert.equal(f.ready(), 0); assert.equal(f.consume(), 0); assert.equal(redis('EXISTS', f.keys[0]), '0');
});
test('cooldown and daily phone budget apply across tenant and purpose bindings', () => {
  const f = fixture(); f.args[5] = '2'; assert.equal(f.reserve(), 1);
  const other = [...f.keys]; other[0] += 'other'; other[1] += 'other';
  const args = [...f.args]; args[0] = 'id-b'; args[1] = 'other-tenant-purpose';
  assert.equal(lua('reserve', other, args), -1);
  redis('DEL', f.keys[2]); assert.equal(lua('reserve', other, args), 1);
  redis('DEL', f.keys[2]); other[0] += 'next'; assert.equal(lua('reserve', other, args), -1);
});
test('peer send budget stops rotating phone numbers', () => {
  const f = fixture(); f.args[6] = '1'; assert.equal(f.reserve(), 1);
  const other = f.keys.map((key, i) => i === 4 ? key : key + '-other');
  assert.equal(lua('reserve', other, f.args), -1);
});
test('verification budgets stop rotating challenge IDs', () => {
  const f = fixture(); f.verify[4] = '2'; f.reserve(); f.ready();
  const wrongKeys = [...f.consumeKeys]; wrongKeys[0] += '-missing';
  assert.equal(lua('consume', wrongKeys, f.verify), 0); assert.equal(lua('consume', wrongKeys, f.verify), 0);
  assert.equal(f.consume(), -2);
});
test('resend supersedes previous challenge and cancel cannot erase newer active binding', () => {
  const f = fixture(); f.reserve(); f.ready(); redis('DEL', f.keys[2]);
  const newerKeys = [...f.keys]; newerKeys[0] += '-new'; const args = [...f.args]; args[0] = 'id-b'; args[2] = 'digest-b';
  assert.equal(lua('reserve', newerKeys, args), 1); assert.equal(f.consume(), 0);
  assert.equal(lua('cancel', f.keys.slice(0, 2), ['id-a']), 1); assert.equal(redis('GET', f.keys[1]), 'id-b');
  assert.equal(lua('ready', newerKeys.slice(0, 2), ['id-b']), 1);
  const verify = [...f.verify]; verify[0] = 'id-b'; verify[2] = 'digest-b';
  assert.equal(lua('consume', [newerKeys[0], newerKeys[1], ...f.consumeKeys.slice(2)], verify), 1);
});
test('send failure cancels only challenge, retaining rate budgets', () => {
  const f = fixture(); f.reserve(); lua('cancel', f.keys.slice(0, 2), ['id-a']);
  assert.equal(redis('EXISTS', f.keys[0]), '0'); assert.equal(redis('GET', f.keys[3]), '1'); assert.equal(f.reserve(), -1);
});
test('TTL exists on challenge and every budget, password rate limiting is atomic', () => {
  const f = fixture(); f.reserve(); f.ready(); f.consume();
  for (const key of [...f.keys.slice(2), ...f.consumeKeys.slice(2)]) assert(Number(redis('TTL', key)) > 0);
  const keys = [f.prefix + 'password-phone', f.prefix + 'password-peer'];
  assert.equal(lua('budget', keys, ['1', '2']), 1); assert.equal(lua('budget', keys, ['1', '2']), 0);
  assert(Number(redis('TTL', keys[0])) > 0);
});
