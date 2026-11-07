package br.com.hiego.entregas.repository;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RateLimitRepository {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
        """
        local count = tonumber(redis.call('GET', KEYS[1]) or '0')
        if count >= tonumber(ARGV[1]) then return 0 end
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
        return 1
        """, Long.class);
    private final StringRedisTemplate redis;

    public RateLimitRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Executa um script Lua atomico para verificar e incrementar a cota no Redis.
     * A expiracao so e definida quando a chave e criada.
     *
     * @param key chave diaria do usuario
     * @param limit quantidade maxima permitida
     * @param ttl tempo de vida da chave em milissegundos
     * @return {@code true} quando a unidade foi consumida; {@code false} se atingiu o limite
     */
    public boolean acquire(String key, int limit, long ttl) {
        Long result = redis.execute(SCRIPT, List.of(key), Integer.toString(limit), Long.toString(ttl));
        return Long.valueOf(1).equals(result);
    }

    /**
     * Le o contador atual sem consumir uma unidade da cota.
     *
     * @param key chave diaria do usuario
     * @return quantidade usada, ou zero quando a chave ainda nao existe
     */
    public long used(String key) {
        String count = redis.opsForValue().get(key);
        return count == null ? 0 : Long.parseLong(count);
    }
}
