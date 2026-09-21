-- Do not extend expiration or recreate an expired/resuperseded challenge after slow SMS RPC.
if redis.call('GET', KEYS[2]) ~= ARGV[1] or redis.call('HGET', KEYS[1], 'state') ~= 'pending' then return 0 end
redis.call('HSET', KEYS[1], 'state', 'ready')
return 1
