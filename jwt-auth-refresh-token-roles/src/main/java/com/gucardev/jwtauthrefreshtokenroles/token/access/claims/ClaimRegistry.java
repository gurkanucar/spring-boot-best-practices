package com.gucardev.jwtauthrefreshtokenroles.token.access.claims;

import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserClaimRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Knows every reserved claim name: the standard ones plus whatever the contributor beans declare.
 * Built at startup, so a clash between two contributors, or with a user claim already stored in the
 * database, stops the application instead of producing ambiguous tokens later.
 */
@Component
public class ClaimRegistry {

    private static final String STANDARD_OWNER = "the standard claims";

    /** Claim name -> who owns it, for error messages. */
    private final Map<String, String> owners;

    public ClaimRegistry(List<JwtClaimsContributor> contributors, UserClaimRepository userClaimRepository) {
        Map<String, String> map = new LinkedHashMap<>();
        StandardClaims.NAMES.forEach(name -> map.put(name, STANDARD_OWNER));
        for (JwtClaimsContributor contributor : contributors) {
            String owner = ClassUtils.getUserClass(contributor).getSimpleName();
            for (String name : contributor.declaredClaims()) {
                String previous = map.putIfAbsent(name, owner);
                if (previous != null) {
                    throw new IllegalStateException(
                            "JWT claim '%s' is declared by both %s and %s".formatted(name, previous, owner));
                }
            }
        }
        this.owners = Map.copyOf(map);

        List<String> clashing = userClaimRepository.findDistinctNamesIn(owners.keySet());
        if (!clashing.isEmpty()) {
            throw new IllegalStateException("Stored user claims use reserved names " + clashing
                    + "; rename or delete them before starting the application");
        }
    }

    public boolean isReserved(String name) {
        return owners.containsKey(name);
    }

    public Set<String> reservedNames() {
        return owners.keySet();
    }
}
