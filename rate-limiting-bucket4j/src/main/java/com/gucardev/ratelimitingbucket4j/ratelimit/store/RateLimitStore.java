package com.gucardev.ratelimitingbucket4j.ratelimit.store;

import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimitBucket;
import java.time.Duration;

/**
 * Where the token buckets live: in this JVM, or in Redis so that every app instance shares them.
 * Both implementations use the same Bucket4j token-bucket algorithm, so switching changes nothing
 * but the sharing.
 */
public interface RateLimitStore {

    /** Takes {@code tokens} from the bucket if all its limits have that many left. */
    Decision tryConsume(RateLimitBucket bucket, long tokens);

    /**
     * @param remaining  tokens left in the tightest limit
     * @param retryAfter when enough tokens will be back; zero when allowed
     */
    record Decision(boolean allowed, long remaining, Duration retryAfter) {
    }
}
