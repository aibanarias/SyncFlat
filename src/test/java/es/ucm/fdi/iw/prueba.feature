Feature: SyncFlat - flujos representativos de la aplicación

Background:
# Configuración común para todos los escenarios
* url baseUrl

# Escenario 1: el usuario puede abrir la pantalla de login y obtener el token CSRF
Scenario: abrir login y capturar csrf
    Given path 'login'
    When method get
    Then status 200
    And match response contains '<form'
    And match response contains '_csrf'
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)
    * match csrf != null

# Escenario 2: un usuario válido inicia sesión y llega al panel principal del piso
Scenario: login correcto y acceso al home
    Given path 'login'
    When method get
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path 'login'
    And form field username = 'a'
    And form field password = 'aa'
    And form field _csrf = csrf
    When method post
    Then status 200
    And match response contains 'SyncFlat'

# Escenario 3: tras autenticarse, el usuario puede ver el dashboard con información del piso
Scenario: usuario autenticado ve resumen del piso en home
    Given path 'login'
    When method get
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path 'login'
    And form field username = 'a'
    And form field password = 'aa'
    And form field _csrf = csrf
    When method post
    Then status 200

    Given path 'modulos/home'
    When method get
    Then status 200
    And match response contains 'eventos'
    And match response contains 'tareas'
    And match response contains 'Alertas'

# Escenario 4: un usuario puede consultar los gastos compartidos del piso
Scenario: usuario ve listado de gastos compartidos
    Given path 'login'
    When method get
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path 'login'
    And form field username = 'a'
    And form field password = 'aa'
    And form field _csrf = csrf
    When method post
    Then status 200

    Given path 'modulos/gastos'
    When method get
    Then status 200
    And match response contains 'Gastos'

# Escenario 5: un usuario puede consultar la lista de la compra del piso
Scenario: usuario ve lista de la compra
    Given path 'login'
    When method get
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path 'login'
    And form field username = 'a'
    And form field password = 'aa'
    And form field _csrf = csrf
    When method post
    Then status 200

    Given path 'modulos/compra'
    When method get
    Then status 200
    And match response contains 'Lista de la compra'

# Escenario 6: un usuario puede consultar las tareas asignadas en el piso
Scenario: usuario ve tareas del piso
    Given path 'login'
    When method get
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path 'login'
    And form field username = 'a'
    And form field password = 'aa'
    And form field _csrf = csrf
    When method post
    Then status 200

    Given path 'modulos/tareas'
    When method get
    Then status 200
    And match response contains 'Tareas'

# Escenario 7: un administrador puede acceder al panel de administración
# El usuario 'a' tiene roles ADMIN,USER según import.sql
Scenario: admin accede al panel de administracion
    Given path 'login'
    When method get
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path 'login'
    And form field username = 'a'
    And form field password = 'aa'
    And form field _csrf = csrf
    When method post
    Then status 200

    Given path 'admin/'
    When method get
    Then status 200
    And match response contains 'Usuarios'

# Escenario 8: un usuario normal no debería poder acceder a administracion
# El usuario 'b' solo tiene rol USER según import.sql
Scenario: usuario normal no accede a administracion
    Given path 'login'
    When method get
    Then status 200
    * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    Given path 'login'
    And form field username = 'b'
    And form field password = 'aa'
    And form field _csrf = csrf
    When method post
    Then status 200

    Given path 'admin/'
    When method get
    Then status 403
