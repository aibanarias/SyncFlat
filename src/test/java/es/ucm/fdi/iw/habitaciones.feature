Feature: gestión de habitaciones del piso

# Usa al usuario 'a' (PROPIETARIO de Piso Moncloa, habitación 1, piso con 3 habitaciones).
# Los escenarios son secuenciales; las mutaciones de 'a' no afectan a otros features
# porque los demás features usan 'a' solo para lecturas o usan otros usuarios.

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'a', password: 'aa' }

Scenario: el panel de home muestra la sección de habitaciones
  Given path 'modulos/home'
  When method get
  Then status 200
  And match response contains 'Habitaciones del piso'
  And match response contains 'Total'
  And match response contains 'Ocupadas'
  And match response contains 'Libres'

Scenario: se muestran los miembros con su número de habitación
  Given path 'modulos/home'
  When method get
  Then status 200
  # 'a' tiene habitación 1, 'b' tiene habitación 2 (import.sql)
  And match response contains '>1<'
  And match response contains '>2<'

Scenario: usuario puede actualizar su número de habitación
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/piso/habitaciones/mi-habitacion'
  And form field numHabitacion = '3'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Panel del piso'

Scenario: número de habitación fuera del rango del piso muestra error
  # El piso tiene 3 habitaciones; pedir la 99 debe rechazarse
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/piso/habitaciones/mi-habitacion'
  And form field numHabitacion = '99'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'no puede superar'

Scenario: configurar número total de habitaciones del piso
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/piso/habitaciones/configurar'
  And form field numHabitaciones = '4'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Panel del piso'

Scenario: configurar 0 habitaciones falla la validación del formulario
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/piso/habitaciones/configurar'
  And form field numHabitaciones = '0'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'al menos 1'
