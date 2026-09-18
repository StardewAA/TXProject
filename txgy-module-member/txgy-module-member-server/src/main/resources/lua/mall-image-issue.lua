-- KEYS: challenge, active session (across tenants/purposes), session cooldown, name/session/peer budgets.
-- ARGV: id, binding HMAC, answer HMAC, ttl, cooldown, name/session/peer limits, budget ttl.
if redis.call('EXISTS', KEYS[3]) == 1 then return -1 end
for i = 4, 6 do
    if tonumber(redis.call('GET', KEYS[i]) or '0') >= tonumber(ARGV[i + 2]) then return -1 end
end
if redis.call('EXISTS', KEYS[1]) == 1 then return -2 end
for i = 4, 6 do
    if redis.call('INCR', KEYS[i]) == 1 then redis.call('EXPIRE', KEYS[i], ARGV[9]) end
end
redis.call('SET', KEYS[3], '1', 'EX', ARGV[5])
redis.call('HSET', KEYS[1], 'binding', ARGV[2], 'digest', ARGV[3], 'attempts', '0')
redis.call('EXPIRE', KEYS[1], ARGV[4])
redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[4])
return 1
