package br.com.hiego.analise;

import br.com.hiego.messaging.MessagingConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(MessagingConfiguration.class)
public class AnaliseApplication {
    public static void main(String[] args) { SpringApplication.run(AnaliseApplication.class, args); }
}
