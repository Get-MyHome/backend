package aichallenge.getmyhome.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

@Configuration
@RequiredArgsConstructor
public class CrawlerLambdaConfig {

    private final CrawlerLambdaProperties properties;

    @Bean
    public LambdaClient lambdaClient() {
        return LambdaClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }
}