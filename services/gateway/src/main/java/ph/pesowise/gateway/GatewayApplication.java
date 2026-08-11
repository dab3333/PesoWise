package ph.pesowise.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ph.pesowise.gateway.security.GatewayAuthProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
