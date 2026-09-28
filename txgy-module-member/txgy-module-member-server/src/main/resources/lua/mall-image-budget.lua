-- KEYS: password account, password peer; ARGV: account/peer limits, ttl. No tenant component.
for i = 1, 2 do
    if tonumber(redis.call('GET', KEYS[i]) or '0') >= tonumber(ARGV[i]) then return 0 end
end
for i = 1, 2 do
    if redis.call('INCR', KEYS[i]) == 1 then redis.call('EXPIRE', KEYS[i], ARGV[3]) end
end
return 1
