package es.ucm.fdi.iw;

import com.intuit.karate.junit5.Karate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Suite de integración Karate. Arranca el servidor embebido en un puerto
 * aleatorio y lo comunica a Karate vía la system property {@code karate.port},
 * que karate-config.js usa para construir {@code baseUrl}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PruebaTest {

    @LocalServerPort
    int port;

    @Karate.Test
    Karate testTodos() {
        System.setProperty("karate.port", String.valueOf(port));
        return Karate.run().relativeTo(getClass());
    }
}
