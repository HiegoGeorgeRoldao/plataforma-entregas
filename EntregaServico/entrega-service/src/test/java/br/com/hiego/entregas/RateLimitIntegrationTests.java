package br.com.hiego.entregas;

import br.com.hiego.entregas.repository.RateLimitRepository;
import br.com.hiego.entregas.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"app.messaging.enabled=false", "app.rate-limit.enabled=false"})
@Import({TestcontainersConfiguration.class, RateLimitIntegrationTests.RedisContainerConfiguration.class})
class RateLimitIntegrationTests {
    @TestConfiguration(proxyBeanMethods = false)
    static class RedisContainerConfiguration {
        @Bean @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>(DockerImageName.parse("redis:7.4.6-alpine")).withExposedPorts(6379);
        }
    }
    @Autowired StringRedisTemplate redis;
    @Test void respeitaCotaMesmoComRequisicoesConcorrentes() throws Exception {
        var limiter = new RateLimitService(new RateLimitRepository(redis), UUID.randomUUID(), true, 5);
        try (var executor = Executors.newFixedThreadPool(10)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 50; i++) tasks.add(() -> {
                try { limiter.acquire(); return true; }
                catch (ResponseStatusException exception) {
                    assertThat(exception.getStatusCode().value()).isEqualTo(429); return false;
                }
            });
            int allowed = 0;
            for (var result : executor.invokeAll(tasks)) if (result.get()) allowed++;
            assertThat(allowed).isEqualTo(5);
        }
    }
}
