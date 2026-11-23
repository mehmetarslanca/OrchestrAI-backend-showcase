package com.arslanca.orchestrai;

import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.core.config.CredentialEncryptionProperties;
import com.arslanca.orchestrai.core.config.GeminiRuntimeProperties;
import com.arslanca.orchestrai.core.config.GitHubAiOAuthProperties;
import com.arslanca.orchestrai.core.config.GitHubOAuthProperties;
import com.arslanca.orchestrai.core.config.GoogleOAuthProperties;
import com.arslanca.orchestrai.core.config.JwtProperties;
import com.arslanca.orchestrai.core.config.RateLimitProperties;
import com.arslanca.orchestrai.core.config.RedisRuntimeProperties;
import com.arslanca.orchestrai.core.config.RestClientTimeoutProperties;
import com.arslanca.orchestrai.core.config.CorsProperties;
import com.arslanca.orchestrai.core.config.PipelineProperties;
import com.arslanca.orchestrai.core.config.ProjectGenerationWorkerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        GitHubOAuthProperties.class,
        GitHubAiOAuthProperties.class,
        GoogleOAuthProperties.class,
        AiModelProperties.class,
        AiProviderRuntimeProperties.class,
        CredentialEncryptionProperties.class,
        GeminiRuntimeProperties.class,
        RedisRuntimeProperties.class,
        RateLimitProperties.class,
        CorsProperties.class,
        PipelineProperties.class,
        RestClientTimeoutProperties.class,
        ProjectGenerationWorkerProperties.class })
public class OrchestraiApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrchestraiApplication.class, args);
	}

}
