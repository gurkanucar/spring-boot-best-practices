package com.gucardev.jackson.config;

import com.gucardev.jackson.convert.MoneyDeserializer;
import com.gucardev.jackson.convert.MoneySerializer;
import com.gucardev.jackson.dto.Money;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonModuleConfig {

    // Registered on the auto-configured JsonMapper builder, so every Money field anywhere
    // in the app gets this representation - no per-field @JsonSerialize/@JsonDeserialize
    // needed, unlike CardPaymentRequest.cardNumber.
    @Bean
    JsonMapperBuilderCustomizer moneyModuleCustomizer() {
        SimpleModule module = new SimpleModule("money-module");
        module.addSerializer(Money.class, new MoneySerializer());
        module.addDeserializer(Money.class, new MoneyDeserializer());
        return builder -> builder.addModule(module);
    }
}
