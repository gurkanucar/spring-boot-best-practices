package com.gucardev.restapidesign.error;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Builds every error response in one shape: ProblemDetail + errors[]. */
@Component
public class ProblemDetailFactory {

    public ProblemDetail of(HttpStatus status, String title, String detail, List<ApiErrorDetail> errors, URI instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(instance);
        problem.setProperty("errors", errors == null ? List.of() : errors);
        return problem;
    }
}
