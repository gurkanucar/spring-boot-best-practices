package com.gucardev.ratelimitingbucket4j.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Charges a named limit before the method runs, the declarative form of {@link RateLimiter#consume}:
 *
 * <pre>{@code
 * @RateLimited(limit = "report-per-email", key = "#email")
 * public Map<String, Object> generateMonthly(String email) { ... }
 * }</pre>
 *
 * <p>Works through a Spring proxy: only on public methods of a bean, called from another bean, not
 * from inside the same class.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    /** Name under {@code app.rate-limit.limits}. */
    String limit();

    /** SpEL over the method's parameters, e.g. {@code #email} or {@code #request.customerId}. */
    String key();

    /** Tokens per call. */
    long cost() default 1;
}
