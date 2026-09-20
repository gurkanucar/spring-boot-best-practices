package com.gucardev.validation.error;

import org.springframework.http.HttpStatus;

/** Business rule violation; subclasses fix status, title key, and the localized detail key/args. */
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String titleKey;
    private final String detailKey;
    private final Object[] args;

    protected BusinessException(String message, HttpStatus status, String titleKey,
                                String detailKey, Object... args) {
        super(message);
        this.status = status;
        this.titleKey = titleKey;
        this.detailKey = detailKey;
        this.args = args;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getDetailKey() {
        return detailKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
