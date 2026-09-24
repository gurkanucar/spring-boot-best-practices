package com.gucardev.restapiintegration.product;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
class ProductHttpApiConfig {

    /** Builds the {@link ProductHttpApi} implementation on top of the already configured client. */
    @Bean
    ProductHttpApi productHttpApi(@Qualifier("remoteApi") RestClient restClient) {
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(ProductHttpApi.class);
    }
}
