package com.gucardev.validation.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@SpringBootTest
class ValidationMessageResolutionTest {

    @Autowired
    private Validator validator;

    @Autowired
    private ApplicationContext applicationContext;

    record Sample(@NotBlank(message = "{validation.user.email.notblank}") String email) {}

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolvesTurkishMessageByDefault() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("tr"));

        var violations = validator.validate(new Sample(""));

        assertThat(violations).singleElement()
                .extracting(v -> v.getMessage())
                .isEqualTo("E-posta bos olamaz");
    }

    @Test
    void resolvesEnglishMessageWhenLocaleIsEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        Set<?> violations = validator.validate(new Sample(""));

        assertThat(violations).singleElement()
                .extracting(v -> ((jakarta.validation.ConstraintViolation<?>) v).getMessage())
                .isEqualTo("Email must not be blank");
    }

    @Test
    void validatorAndLocaleResolverBeansAreRegistered() {
        assertThat(applicationContext.getBean(Validator.class)).isSameAs(validator);
        assertThat(applicationContext.containsBean("localeResolver")).isTrue();

        LocaleResolver localeResolver = applicationContext.getBean("localeResolver", LocaleResolver.class);
        assertThat(localeResolver).isInstanceOf(AcceptHeaderLocaleResolver.class);
        assertThat(localeResolver.resolveLocale(new MockHttpServletRequest()))
                .isEqualTo(Locale.forLanguageTag("tr"));
    }
}
