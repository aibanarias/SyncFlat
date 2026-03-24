package es.ucm.fdi.iw;

import com.intuit.karate.junit5.Karate;

class PruebaTest {

    @Karate.Test
    Karate testPrueba() {
        return Karate.run("prueba").relativeTo(getClass());
    }
}
