package com.sopuro.appeal_system.configs;

import com.sopuro.appeal_system.clients.rover.RoverClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class RestClientConfig {
    private static final String BASE_URL = "https://registry.rover.link/api";

    @Bean
    public RoverClient roverClient(RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .requestInterceptor((request, body, execution) -> {
                    String auth = request.getHeaders().getFirst("Authorization");
                    if (auth != null && !auth.startsWith("Bearer ")) {
                        request.getHeaders().set("Authorization", "Bearer " + auth);
                    }
                    return execution.execute(request, body);
                })
                .build();

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(RoverClient.class);
    }
}
