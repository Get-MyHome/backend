package aichallenge.getmyhome.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai-server")
public class AiServerProperties {

    /** AI 서버 URL */
    private String baseUrl;

    /** AI 서버 인증용 API Key */
    private String apiKey;
}