package es.ucm.fdi.iw.model;

import java.time.LocalDate;

/**
 * Frecuencia de repetición de una {@link Tarea} recurrente.
 * <p>
 * Cada valor conoce cómo calcular la siguiente fecha límite a partir de la actual,
 * lo que permite que {@link es.ucm.fdi.iw.service.TareaService} genere la siguiente
 * ocurrencia sin lógica dispersa.
 */
public enum FrecuenciaTarea {

    DIARIA {
        @Override
        public LocalDate siguienteFechaLimite(LocalDate actual) {
            return actual.plusDays(1);
        }
    },
    SEMANAL {
        @Override
        public LocalDate siguienteFechaLimite(LocalDate actual) {
            return actual.plusWeeks(1);
        }
    },
    QUINCENAL {
        @Override
        public LocalDate siguienteFechaLimite(LocalDate actual) {
            return actual.plusWeeks(2);
        }
    },
    MENSUAL {
        @Override
        public LocalDate siguienteFechaLimite(LocalDate actual) {
            return actual.plusMonths(1);
        }
    };

    /**
     * Calcula la fecha límite de la siguiente ocurrencia.
     *
     * @param actual fecha límite de la ocurrencia que se acaba de validar
     * @return nueva fecha límite desplazada según la frecuencia
     */
    public abstract LocalDate siguienteFechaLimite(LocalDate actual);
}
