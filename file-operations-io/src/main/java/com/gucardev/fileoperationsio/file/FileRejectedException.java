package com.gucardev.fileoperationsio.file;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An upload that is refused (400, 413, 415). Spring MVC renders it as ProblemDetail JSON, so no
 * custom exception handler is needed. The detail never contains raw user input.
 */
public class FileRejectedException extends ErrorResponseException {

    public FileRejectedException(HttpStatus status, String detail) {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
