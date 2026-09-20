package com.gucardev.validation.error;

import java.net.URI;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Produces every error response in one shape: an RFC 9457 {@link ProblemDetail}
 * body with an added {@code errors} array.
 */
@Component
public class ProblemDetailFactory {

    private final MessageSource messageSource;

    public ProblemDetailFactory(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** 400 - validation failure. */
    public ProblemDetail validation(List<ValidationErrorDetail> errors, URI instance) {
        return of(HttpStatus.BAD_REQUEST, "error.validation.title",
                message("error.validation.detail"), errors, instance);
    }

    public ProblemDetail of(HttpStatus status, String titleKey, String detail,
                            List<ValidationErrorDetail> errors, URI instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(message(titleKey));
        problem.setInstance(instance);
        problem.setProperty("errors", errors == null ? List.of() : errors);
        return problem;
    }

    public String message(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    /** For keys with {0}-style placeholders, e.g. a business exception's detail. */
    public String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
