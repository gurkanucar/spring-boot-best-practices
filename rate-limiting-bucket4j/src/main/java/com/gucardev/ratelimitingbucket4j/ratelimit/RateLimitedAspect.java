package com.gucardev.ratelimitingbucket4j.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/** Runs {@link RateLimiter#consume} before every {@link RateLimited} method. */
@Aspect
class RateLimitedAspect {

    private final RateLimiter rateLimiter;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    RateLimitedAspect(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Before("@annotation(rateLimited)")
    void charge(JoinPoint joinPoint, RateLimited rateLimited) {
        var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        // Parameter names (#email) are available because Spring Boot compiles with -parameters.
        var context = new MethodBasedEvaluationContext(null, method, joinPoint.getArgs(), parameterNames);
        Object key = expressions.computeIfAbsent(rateLimited.key(), parser::parseExpression).getValue(context);
        if (key == null) {
            throw new IllegalArgumentException("@RateLimited key " + rateLimited.key() + " is null on " + method);
        }
        rateLimiter.consume(rateLimited.limit(), key.toString(), rateLimited.cost());
    }
}
