local key = KEYS[1]
local maxTokens = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

if not tokens or not lastRefill then
    tokens = maxTokens
    lastRefill = now
else
    local elapsedTime = math.max(0, now - lastRefill)
    local tokensToAdd = elapsedTime * refillRate
    tokens = math.min(maxTokens, tokens + tokensToAdd)
end

if tokens >= 1.0 then
    tokens = tokens - 1.0
    redis.call('HSET', key, 'tokens', tostring(tokens), 'lastRefill', tostring(now))
    redis.call('EXPIRE', key, 120) -- 2 minutes TTL
    return 1
else
    redis.call('HSET', key, 'tokens', tostring(tokens), 'lastRefill', tostring(now))
    redis.call('EXPIRE', key, 120)
    return 0
end
