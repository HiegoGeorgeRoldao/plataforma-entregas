package br.com.hiego.analise.repository.redis;

import br.com.hiego.analise.repository.ApiCacheRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisApiCacheRepository implements ApiCacheRepository {
    private static final Logger log = LoggerFactory.getLogger(RedisApiCacheRepository.class);
    private final StringRedisTemplate redis;

    /** Recebe o cliente Redis usado pelo cache de respostas externas. */
    public RedisApiCacheRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** {@inheritDoc} */
    @Override
    public String get(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException exception) {
            log.warn("Cache indisponivel; consulta seguira para a API");
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void put(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (DataAccessException exception) {
            log.warn("Nao foi possivel atualizar o cache");
        }
    }
}
