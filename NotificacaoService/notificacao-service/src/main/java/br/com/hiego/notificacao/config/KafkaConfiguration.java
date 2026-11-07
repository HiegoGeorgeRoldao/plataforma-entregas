package br.com.hiego.notificacao.config;

import br.com.hiego.messaging.MessagingConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Importa {@link MessagingConfiguration} da biblioteca {@code messaging-core}.
 * A configuracao compartilhada declara topicos e DLTs e registra o tratamento
 * de falhas com os valores de {@code app.messaging} definidos no YAML.
 * Sua ativacao depende de {@code app.messaging.enabled=true}.
 * Esta classe apenas importa a configuracao; nao declara metodos de criacao de beans.
 */
@Configuration(proxyBeanMethods = false)
@Import(MessagingConfiguration.class)
public class KafkaConfiguration {}
