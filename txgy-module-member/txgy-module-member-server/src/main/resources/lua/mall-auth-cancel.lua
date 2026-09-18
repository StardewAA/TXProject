-- A failed/ambiguous send invalidates only this challenge; never release abuse quotas.
redis.call('DEL', KEYS[1])
if redis.call('GET', KEYS[2]) == ARGV[1] then redis.call('DEL', KEYS[2]) end
return 1
