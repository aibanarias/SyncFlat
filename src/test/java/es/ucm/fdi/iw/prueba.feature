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

# Escenario 9: crear un gasto y verificar que aparece en el listado
# Prueba de negocio: el POST persiste en H2 y el GET siguiente lo muestra
Scenario: crear gasto y comprobar que aparece en el listado
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

    # Obtener CSRF del formulario de gastos (token post-autenticación)
    Given path 'modulos/gastos'
    When method get
    Then status 200
    * def csrfGasto = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    # Crear gasto vía POST — debe redirigir a GET /modulos/gastos con el nuevo gasto
    Given path 'modulos/gastos'
    And form field concepto = 'Gasto test Karate'
    And form field importe = '60.00'
    And form field _csrf = csrfGasto
    When method post
    Then status 200
    And match response contains 'Gasto test Karate'

# Escenario 10: crear una tarea y verificar que aparece en el listado
# Prueba de negocio: el POST persiste la tarea en H2 y el GET la lista
Scenario: crear tarea y comprobar que aparece en el listado
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

    # Obtener CSRF del formulario de tareas
    Given path 'modulos/tareas'
    When method get
    Then status 200
    * def csrfTarea = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

    # Crear tarea vía POST — debe redirigir a GET /modulos/tareas con la nueva tarea
    Given path 'modulos/tareas'
    And form field nombre = 'Tarea test Karate'
    And form field descripcion = 'Tarea creada por prueba automatizada'
    And form field tipo = 'PUNTUAL'
    And form field fechaLimite = '2026-12-31'
    And form field _csrf = csrfTarea
    When method post
    Then status 200
    And match response contains 'Tarea test Karate'
