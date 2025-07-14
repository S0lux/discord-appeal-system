package com.sopuro.appeal_system.configs;

import com.sopuro.appeal_system.clients.opencloud.OpenCloudClient;
import com.sopuro.appeal_system.clients.rover.RoverClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class RestClientConfig {
    private static final String ROVER_BASE_URL = "https://registry.rover.link/api";
    private static final String OPEN_CLOUD_BASE_URL = "https://apis.roblox.com";

    @Bean
    public RoverClient roverClient(RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder
                .baseUrl(ROVER_BASE_URL)
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

    @Bean
    public OpenCloudClient openCloudClient(RestClient.Builder restClientBuilder, @Value("${open.cloud.token}") String openCloudToken) {
        RestClient restClient = restClientBuilder
                .baseUrl(OPEN_CLOUD_BASE_URL)
                .defaultHeader("x-api-key", openCloudToken)
                .build();

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(OpenCloudClient.class);
    }
}
