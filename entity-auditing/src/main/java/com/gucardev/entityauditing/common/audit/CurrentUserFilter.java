package com.gucardev.entityauditing.common.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * DEMO ONLY: takes the user name from the {@code X-User} header. A header can be set by anyone,
 * so a real application takes the user from its authentication (Spring Security) instead.
 */
@Component
public class CurrentUserFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-User";
    static final String ANONYMOUS = "anonymous";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._@-]{1,50}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String user = request.getHeader(HEADER);
        CurrentUser.set(user != null && VALID.matcher(user).matches() ? user : ANONYMOUS);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled: without this the next request on this thread would inherit the user.
            CurrentUser.clear();
        }
    }
}
