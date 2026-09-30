package com.gucardev.jwtauthrefreshtokenroles.otp.service;

/** EMAIL codes are delivered as links with a 256-bit token; SMS codes are 6 digits. */
public enum OtpChannel {
    EMAIL,
    SMS
}
