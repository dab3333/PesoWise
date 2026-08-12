package ph.pesowise.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ph.pesowise.auth.config.AdminProperties;
import ph.pesowise.auth.config.JwtProperties;
import ph.pesowise.auth.mail.MailProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, MailProperties.class, AdminProperties.class})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
