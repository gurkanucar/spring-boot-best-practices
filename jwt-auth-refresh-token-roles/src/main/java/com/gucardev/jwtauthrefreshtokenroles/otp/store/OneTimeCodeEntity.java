package com.gucardev.jwtauthrefreshtokenroles.otp.store;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "one_time_codes",
        uniqueConstraints = @UniqueConstraint(name = "uk_otp_user_purpose_channel",
                columnNames = {"user_id", "purpose", "channel"}),
        indexes = {
                @Index(name = "idx_otp_code_hash", columnList = "code_hash"),
                @Index(name = "idx_otp_expires", columnList = "expires_at")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OneTimeCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OtpPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OtpChannel channel;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    /** The e-mail or phone the code was sent to; the code is void once the user's address changes. */
    @Column(nullable = false, length = 320)
    private String target;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OneTimeCodeEntity(UUID userId, OtpPurpose purpose, OtpChannel channel, String codeHash,
                             String target, Instant expiresAt, Instant createdAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.channel = channel;
        this.codeHash = codeHash;
        this.target = target;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }
}
