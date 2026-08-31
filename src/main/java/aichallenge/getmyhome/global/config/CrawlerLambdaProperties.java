package aichallenge.getmyhome.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "crawler.lambda")
public class CrawlerLambdaProperties {

    private String functionName;
    private String region;
}