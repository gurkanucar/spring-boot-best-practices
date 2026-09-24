package com.gucardev.entityauditing.common.audit;

/**
 * Who is making the current change. Both Spring Data auditing ({@code createdBy}) and the Envers
 * revision ({@code username}) read it from here.
 *
 * <p>In a real application this is {@code SecurityContextHolder.getContext().getAuthentication()};
 * this example has no Spring Security, so {@link CurrentUserFilter} fills it from a header.
 * Work outside an HTTP request (batch jobs, tests) is recorded as {@value #SYSTEM}.
 */
public final class CurrentUser {

    public static final String SYSTEM = "system";

    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static String get() {
        String user = USER.get();
        return user != null ? user : SYSTEM;
    }

    static void set(String user) {
        USER.set(user);
    }

    static void clear() {
        USER.remove();
    }
}
