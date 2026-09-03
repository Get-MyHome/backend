package aichallenge.getmyhome.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("complexList",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(10)
                        .build());
        manager.registerCustomCache("complexDetail",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(3000)
                        .build());
        manager.registerCustomCache("pdfAnalysis",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(500)
                        .build());
        manager.registerCustomCache("crawlerPdfUrl",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(500)
                        .build());
        return manager;
    }
}
