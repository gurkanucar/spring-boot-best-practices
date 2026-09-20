package com.gucardev.validation.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Picks the response locale from the Accept-Language header, defaulting to Turkish.
 * Bean name must be {@code localeResolver}; DispatcherServlet looks it up by that name.
 */
@Configuration
public class LocaleConfig {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(TURKISH);
        resolver.setSupportedLocales(List.of(TURKISH, Locale.ENGLISH));
        return resolver;
    }
}
