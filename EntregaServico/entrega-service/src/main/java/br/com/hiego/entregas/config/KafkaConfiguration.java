package br.com.hiego.entregas.config;

import br.com.hiego.messaging.MessagingConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Importa de {@code messaging-core} a criacao dos topicos, o agendamento e o tratamento de falhas Kafka. */
@Configuration(proxyBeanMethods = false)
@Import(MessagingConfiguration.class)
public class KafkaConfiguration {}
