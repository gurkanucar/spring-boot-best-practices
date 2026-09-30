package com.gucardev.jwtauthrefreshtokenroles.demo.dto;

import java.util.List;

public record MessageResponse(String message, String subject, List<String> authorities) {
}
