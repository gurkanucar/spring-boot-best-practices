package com.gucardev.casdoorbasicsecurity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AppUserServiceTest {

    private final AppUserRepository repository = mock(AppUserRepository.class);
    private final AppUserService service = new AppUserService(repository);
    private final AppUser user = mock(AppUser.class);

    @Test
    void savesTrimmedPreferencesOnTheUsersRow() {
        when(repository.findByCasdoorId("casdoor-user-1")).thenReturn(Optional.of(user));

        service.updatePreferences("casdoor-user-1", "  Ally ", "dark");

        verify(user).updatePreferences("Ally", "dark");
    }

    @Test
    void blankNicknameAndNoThemeClearThem() {
        when(repository.findByCasdoorId("casdoor-user-1")).thenReturn(Optional.of(user));

        service.updatePreferences("casdoor-user-1", "   ", null);

        verify(user).updatePreferences(null, null);
    }

    @Test
    void rejectsAnUnknownThemeOrALongNickname() {
        when(repository.findByCasdoorId("casdoor-user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updatePreferences("casdoor-user-1", "Ally", "pink"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updatePreferences("casdoor-user-1", "x".repeat(51), "dark"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
