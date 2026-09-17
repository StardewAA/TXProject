import { describe, expect, it } from 'vitest';
import type { AgentGrant, ChatMessage, FaqEntry, ScopedSession } from '../../../../api/mall/promotion/kefu-scoped';
import { actorName, canClaim, canReadSession, createRequestFence, kefuError, mergeMessages, ownsActive, scopeQuery, validateFaq, validateGrant } from './model';

const grant = (): AgentGrant => ({ scope: 'SHOP', shopId: 41, actorType: 1, actorId: 71, enabled: true, version: 0, reason: '本地独立测试' });
const session = (): ScopedSession => ({ scope: 'SHOP', shopId: 41, id: 'session-a', tenantId: 1, memberId: 8, state: 'ACTIVE', agentType: 2, agentId: 71, version: 3, createTime: '', updateTime: '' });
const faq = (): FaqEntry => ({ id: 'delivery', question: '配送说明', answer: '以订单实际物流信息为准', keywords: ['物流'], enabled: true });
const message = (id: number, sessionId = 'session-a'): ChatMessage => ({ id, sessionId, text: '测试纯文本', senderRole: 'MEMBER', createdAt: 1 });

describe('independent customer-service scopes', () => {
  it('forces platform scope to shop zero', () => expect(scopeQuery('PLATFORM', 41)).toEqual({ scope: 'PLATFORM', shopId: 0 }));
  it.each([undefined, 0, -1, 1.2, Number.MAX_SAFE_INTEGER + 1])('rejects invalid shop %s', (id) => expect(scopeQuery('SHOP', id)).toBeUndefined());
  it('retains explicit shop', () => expect(scopeQuery('SHOP', 41)?.shopId).toBe(41));
  it('accepts a reasoned versioned grant', () => expect(validateGrant(grant())).toBe(''));
  it('distinguishes actor types', () => { expect(actorName(1)).not.toBe(actorName(2)); expect(validateGrant({ ...grant(), actorType: 3 as 1 })).not.toBe(''); });
  it('rejects platform grants with a shop', () => expect(validateGrant({ ...grant(), scope: 'PLATFORM' })).not.toBe(''));
  it('rejects missing reason and unsafe version', () => { expect(validateGrant({ ...grant(), reason: ' ' })).not.toBe(''); expect(validateGrant({ ...grant(), version: -1 })).not.toBe(''); });
  it('allows only waiting claims', () => { expect(canClaim({ ...session(), state: 'WAITING' })).toBe(true); expect(canClaim(session())).toBe(false); });
  it('never treats same-number member as administrator', () => { expect(ownsActive(session(), 71)).toBe(true); expect(ownsActive({ ...session(), agentType: 1 }, 71)).toBe(false); });
  it('refuses another assignee or closed send', () => { expect(ownsActive(session(), 72)).toBe(false); expect(ownsActive({ ...session(), state: 'CLOSED' }, 71)).toBe(false); });
  it('reads waiting queue but assigned history only as owner', () => { expect(canReadSession({ ...session(), state: 'WAITING' }, 72)).toBe(true); expect(canReadSession(session(), 72)).toBe(false); expect(canReadSession({ ...session(), state: 'CLOSED' }, 71)).toBe(true); });
});
describe('safe errors and lifecycle', () => {
  it.each([1013016000, 1013016001, 1013016002, 1013016003, 401, 403])('maps code %s without exposing server internals', (code) => expect(kefuError({ response: { data: { code, msg: 'private-secret' } } })).not.toContain('private-secret'));
  it('does not display arbitrary network text', () => expect(kefuError(new Error('private-secret'))).not.toContain('private-secret'));
  it('invalidates old replies and aborts old transport on scope change', () => { const fence = createRequestFence(); const first = fence.begin(); fence.invalidate(); expect(first.current()).toBe(false); expect(first.signal.aborted).toBe(true); expect(fence.begin().current()).toBe(true); });
  it('new reads cannot be overwritten by old reads', () => { const fence = createRequestFence(); const first = fence.begin(); const second = fence.begin(); expect(first.current()).toBe(false); expect(second.current()).toBe(true); });
  it('deduplicates and sorts messages, excludes other session', () => expect(mergeMessages([message(2)], [message(1), message(2), message(3, 'foreign')], 'session-a').map((row) => row.id)).toEqual([1, 2]));
  it('keeps text as text, never interprets markup', () => expect(mergeMessages([], [{ ...message(1), text: '<script>alert(1)</script>' }], 'session-a')[0]?.text).toBe('<script>alert(1)</script>'));
});
describe('fixed FAQ draft validation', () => {
  it('accepts explicit fixed FAQ and empty draft', () => { expect(validateFaq([faq()])).toBe(''); expect(validateFaq([])).toBe(''); });
  it('refuses duplicate identifiers', () => expect(validateFaq([faq(), faq()])).not.toBe(''));
  it('refuses invalid identifier', () => expect(validateFaq([{ ...faq(), id: '<tag>' }])).not.toBe(''));
  it('enforces question and answer limits', () => { expect(validateFaq([{ ...faq(), question: '' }])).not.toBe(''); expect(validateFaq([{ ...faq(), answer: 'a'.repeat(1001) }])).not.toBe(''); });
  it('enforces keyword count and length', () => { expect(validateFaq([{ ...faq(), keywords: ['a'] }])).not.toBe(''); expect(validateFaq([{ ...faq(), keywords: Array.from({ length: 9 }, () => '物流') }])).not.toBe(''); });
  it('limits scope FAQ count', () => expect(validateFaq(Array.from({ length: 31 }, (_, index) => ({ ...faq(), id: `q${index}` })))).not.toBe(''));
});
