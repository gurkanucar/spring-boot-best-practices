package com.gucardev.jwtauthrefreshtokenroles.auth.service;

import com.gucardev.jwtauthrefreshtokenroles.auth.dto.LoginRequest;
import com.gucardev.jwtauthrefreshtokenroles.auth.dto.TokenResponse;
import com.gucardev.jwtauthrefreshtokenroles.common.config.JwtProperties;
import com.gucardev.jwtauthrefreshtokenroles.common.error.InvalidTokenException;
import com.gucardev.jwtauthrefreshtokenroles.common.security.RandomTokenGenerator;
import com.gucardev.jwtauthrefreshtokenroles.token.access.AccessTokenIssuer;
import com.gucardev.jwtauthrefreshtokenroles.token.refresh.RefreshTokenStore;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_REFRESH_TOKEN = "Invalid or expired refresh token";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final RandomTokenGenerator randomTokenGenerator;
    private final JwtProperties jwtProperties;

    /** Throws BadCredentialsException (401) or DisabledException (403, unverified e-mail). */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = UserEntity.normalizeEmail(request.email());
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid e-mail or password"));
        return issueTokens(user);
    }

    /** Rotation: the presented token is consumed, and a new pair with fresh roles and claims is issued. */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenResponse refresh(String refreshToken) {
        String userId = refreshTokenStore.consume(refreshToken)
                .orElseThrow(() -> new InvalidTokenException(INVALID_REFRESH_TOKEN));
        UserEntity user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new InvalidTokenException(INVALID_REFRESH_TOKEN));
        return issueTokens(user);
    }

    /** Idempotent: an unknown or already used token is simply ignored. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenStore.consume(refreshToken);
    }

    private TokenResponse issueTokens(UserEntity user) {
        String refreshToken = randomTokenGenerator.opaqueToken();
        refreshTokenStore.save(refreshToken, user.getId().toString(), jwtProperties.refreshTokenTtl());
        return TokenResponse.bearer(accessTokenIssuer.issue(user), refreshToken);
    }
}
