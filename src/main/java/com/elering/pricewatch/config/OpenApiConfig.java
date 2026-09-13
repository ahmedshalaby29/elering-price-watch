package com.elering.pricewatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Springdoc OpenAPI / Swagger UI customisation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Elering Price Watch API")
                        .description("""
                                Tracks Estonian Nord Pool day-ahead electricity prices via
                                the public Elering API and provides:
                                - Hourly price history for EE/FI/LV/LT zones
                                - Cheapest-window calculations (ideal for EV charging / washing machines)
                                - Price statistics over arbitrary date ranges
                                - Alert subscriptions (email, webhook, Telegram) for price thresholds
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Elering Price Watch")
                                .url("https://github.com/elering/price-watch"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local / Docker")
                ));
    }
}
