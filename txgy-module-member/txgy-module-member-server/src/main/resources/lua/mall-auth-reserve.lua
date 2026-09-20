-- All keys use the same cluster hash tag. Limits span ALL tenants and purposes.
-- KEYS: challenge, active binding, phone cooldown, phone daily, peer hourly
-- ARGV: id, binding HMAC, code HMAC, ttl, cooldown, phone limit, peer limit
if redis.call('EXISTS', KEYS[3]) == 1 then return -1 end
if tonumber(redis.call('GET', KEYS[4]) or '0') >= tonumber(ARGV[6]) then return -1 end
if tonumber(redis.call('GET', KEYS[5]) or '0') >= tonumber(ARGV[7]) then return -1 end
if redis.call('EXISTS', KEYS[1]) == 1 then return -2 end
redis.call('SET', KEYS[3], '1', 'EX', ARGV[5])
if redis.call('INCR', KEYS[4]) == 1 then redis.call('EXPIRE', KEYS[4], 86400) end
if redis.call('INCR', KEYS[5]) == 1 then redis.call('EXPIRE', KEYS[5], 3600) end
redis.call('HSET', KEYS[1], 'binding', ARGV[2], 'digest', ARGV[3], 'attempts', '0', 'state', 'pending')
redis.call('EXPIRE', KEYS[1], ARGV[4])
redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[4])
return 1
