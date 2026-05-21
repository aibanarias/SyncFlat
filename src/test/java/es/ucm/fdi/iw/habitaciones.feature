Feature: gestión de habitaciones del piso

# Usa al usuario 'a' (PROPIETARIO de Piso Moncloa, habitación 1, piso con 3 habitaciones).
# Los escenarios son secuenciales; las mutaciones de 'a' no afectan a otros features
# porque los demás features usan 'a' solo para lecturas o usan otros usuarios.

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

Scenario: usuario puede actualizar su número de habitación
  Given path 'home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/habitaciones/mi-habitacion'
  And form field numHabitacion = '3'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Panel del piso'

Scenario: número de habitación fuera del rango del piso muestra error
  # El piso tiene 3 habitaciones; pedir la 99 debe rechazarse
  Given path 'home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/habitaciones/mi-habitacion'
  And form field numHabitacion = '99'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'no puede superar'

Scenario: configurar número total de habitaciones del piso
  Given path 'home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/habitaciones/configurar'
  And form field numHabitaciones = '4'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Panel del piso'

Scenario: configurar 0 habitaciones falla la validación del formulario
  Given path 'home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/habitaciones/configurar'
  And form field numHabitaciones = '0'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'al menos 1'
