-- KEYS: challenge, active binding, global phone attempts, global peer attempts
-- ARGV: id, binding HMAC, code HMAC, challenge max attempts, phone limit, peer limit
if tonumber(redis.call('GET', KEYS[3]) or '0') >= tonumber(ARGV[5]) then return -2 end
if tonumber(redis.call('GET', KEYS[4]) or '0') >= tonumber(ARGV[6]) then return -2 end
if redis.call('INCR', KEYS[3]) == 1 then redis.call('EXPIRE', KEYS[3], 900) end
if redis.call('INCR', KEYS[4]) == 1 then redis.call('EXPIRE', KEYS[4], 900) end
if redis.call('HGET', KEYS[1], 'state') ~= 'ready' then return 0 end
-- A mismatched target/purpose cannot consume or lock out the original challenge.
if redis.call('HGET', KEYS[1], 'binding') ~= ARGV[2] or redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
if redis.call('HGET', KEYS[1], 'digest') ~= ARGV[3] then
    if redis.call('HINCRBY', KEYS[1], 'attempts', 1) >= tonumber(ARGV[4]) then
        redis.call('DEL', KEYS[1], KEYS[2])
    end
    return 0
end
redis.call('DEL', KEYS[1], KEYS[2])
return 1
