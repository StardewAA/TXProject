-- KEYS: challenge, active session, name/session/peer verification budgets (all independent of tenant).
-- ARGV: id, binding HMAC, answer HMAC, attempts, name/session/peer limits, budget ttl.
for i = 3, 5 do
    if tonumber(redis.call('GET', KEYS[i]) or '0') >= tonumber(ARGV[i + 2]) then return -2 end
end
for i = 3, 5 do
    if redis.call('INCR', KEYS[i]) == 1 then redis.call('EXPIRE', KEYS[i], ARGV[8]) end
end
-- Foreign targets/names/sessions cannot spend the original image's attempts or delete another session.
if redis.call('HGET', KEYS[1], 'binding') ~= ARGV[2] or redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
if redis.call('HGET', KEYS[1], 'digest') ~= ARGV[3] then
    if redis.call('HINCRBY', KEYS[1], 'attempts', 1) >= tonumber(ARGV[4]) then redis.call('DEL', KEYS[1], KEYS[2]) end
    return 0
end
redis.call('DEL', KEYS[1], KEYS[2])
return 1
