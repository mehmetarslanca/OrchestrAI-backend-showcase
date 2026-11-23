local key = KEYS[1]
local lease_token = ARGV[1]
local max_concurrent = tonumber(ARGV[2])
local current_time = tonumber(ARGV[3])
local ttl_ms = tonumber(ARGV[4])

-- Remove expired leases
redis.call('ZREMRANGEBYSCORE', key, '-inf', current_time - ttl_ms)

if redis.call('ZSCORE', key, lease_token) then
  -- Renew lease
  redis.call('ZADD', key, current_time, lease_token)
  redis.call('PEXPIRE', key, ttl_ms)
  return 1
end

if redis.call('ZCARD', key) >= max_concurrent then
  return 0
end

redis.call('ZADD', key, current_time, lease_token)
redis.call('PEXPIRE', key, ttl_ms)
return 1
