package com.gucardev.jwtauthrefreshtokenroles.token.access.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserClaimRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClaimRegistryTest {

    private final UserClaimRepository repository = mock(UserClaimRepository.class);

    @Test
    void reservesStandardAndDeclaredContributorNames() {
        when(repository.findDistinctNamesIn(anyCollection())).thenReturn(List.of());

        ClaimRegistry registry = new ClaimRegistry(
                List.of(new VerificationStatusClaimsContributor(), new UserClaimsContributor()), repository);

        assertThat(registry.isReserved("sub")).isTrue();
        assertThat(registry.isReserved("roles")).isTrue();
        assertThat(registry.isReserved("email_verified")).isTrue();
        assertThat(registry.isReserved("tenant_id")).isFalse();
    }

    @Test
    void failsWhenTwoContributorsDeclareTheSameName() {
        when(repository.findDistinctNamesIn(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> new ClaimRegistry(
                List.of(new VerificationStatusClaimsContributor(), declaring("email_verified")), repository))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email_verified")
                .hasMessageContaining("VerificationStatusClaimsContributor");
    }

    @Test
    void failsWhenContributorDeclaresStandardName() {
        when(repository.findDistinctNamesIn(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> new ClaimRegistry(List.of(declaring("sub")), repository))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'sub'");
    }

    @Test
    void failsWhenStoredUserClaimUsesReservedName() {
        when(repository.findDistinctNamesIn(anyCollection())).thenReturn(List.of("email_verified"));

        assertThatThrownBy(() -> new ClaimRegistry(List.of(new VerificationStatusClaimsContributor()), repository))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email_verified");
    }

    private static JwtClaimsContributor declaring(String name) {
        return new JwtClaimsContributor() {
            @Override
            public Set<String> declaredClaims() {
                return Set.of(name);
            }

            @Override
            public Map<String, Object> contribute(UserEntity user) {
                return Map.of();
            }
        };
    }
}
