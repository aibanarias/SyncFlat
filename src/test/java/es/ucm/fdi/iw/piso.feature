Feature: gestión de pertenencia a un piso

# Todos los escenarios son secuenciales y usan al usuario 'c' (sin piso en import.sql).
# El estado de la BD persiste entre escenarios dentro de la misma ejecución de tests.

Background:
  * url baseUrl
  * callonce read('helpers/login.feature') { username: 'c', password: 'aa' }

Scenario: usuario sin piso ve la pantalla de selección de piso
  Given path 'piso'
  When method get
  Then status 200
  And match response contains 'Crear un piso'
  And match response contains 'Unirse a un piso'

Scenario: código de invitación inválido devuelve mensaje de error
  Given path 'piso'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/unirse'
  And form field codigo = 'INVALIDO'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Código de invitación no válido'

Scenario: usuario sin piso puede crear un piso nuevo
  Given path 'piso'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/crear'
  And form field nombre = 'Piso Test Karate'
  And form field direccion = 'Calle Test 1, Madrid'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Panel del piso'

Scenario: usuario ya en piso no puede crear otro (redirige a home)
  # c ya está en su piso tras el escenario anterior;
  # GET /piso redirige a home porque c tiene piso activo
  Given path 'piso'
  When method get
  Then status 200
  And match response contains 'Panel del piso'

Scenario: usuario puede abandonar su piso
  Given path 'home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/abandonar'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Crear un piso'

Scenario: usuario (sin piso de nuevo) puede unirse con código válido
  Given path 'piso'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/unirse'
  And form field codigo = 'MONC01'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Panel del piso'
