Feature: reglas de negocio de roles y habitaciones del piso

# Cada escenario hace su propio login para controlar el usuario activo.
# Orden de ejecución importante (estado DB compartido):
#   1. c (sin piso) crea 'Piso Admin Test'  → c queda como ADMIN
#   2. b (MIEMBRO)  intenta configurar hab.  → rechazado
#   3. a (ADMIN)    intenta tomar hab. 2 (b) → rechazado por ocupada
#   4. a            promueve a b             → b pasa a ADMIN
#   5. c            abandona su piso (solo)  → piso eliminado
# Tras este feature: a y b son ADMIN de Piso Moncloa; c no tiene piso.

Background:
  * url baseUrl

# ------------------------------------------------------------------
# 1. El creador de un piso queda automáticamente como ADMIN
# ------------------------------------------------------------------
Scenario: creador de piso obtiene rol ADMIN automáticamente
  # c no tiene piso todavía (import.sql no le asigna ninguno)
  * call read('helpers/login.feature') { username: 'c', password: 'aa' }
  Given path 'piso'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/crear'
  And form field nombre = 'Piso Admin Test'
  And form field direccion = 'Calle Admin 1, Madrid'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Panel del piso'
  # El rol Admin debe aparecer en la tabla de miembros para c
  And match response contains 'Admin'

# ------------------------------------------------------------------
# 2. Un MIEMBRO no puede configurar el número de habitaciones
# ------------------------------------------------------------------
Scenario: miembro sin rol ADMIN no puede configurar habitaciones
  * call read('helpers/login.feature') { username: 'b', password: 'aa' }
  Given path 'home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/habitaciones/configurar'
  And form field numHabitaciones = '5'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Solo los administradores'

# ------------------------------------------------------------------
# 3. No se puede asignar una habitación ya ocupada
# ------------------------------------------------------------------
Scenario: intentar asignar habitación ya ocupada devuelve error claro
  # 'b' tiene la habitación 2 (import.sql); 'a' intenta tomar la 2
  * call read('helpers/login.feature') { username: 'a', password: 'aa' }
  Given path 'home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/habitaciones/mi-habitacion'
  And form field numHabitacion = '2'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'ya está ocupada'

# ------------------------------------------------------------------
# 4. Un ADMIN puede promover a otro miembro
# ------------------------------------------------------------------
Scenario: ADMIN puede promover a un miembro a administrador
  * call read('helpers/login.feature') { username: 'a', password: 'aa' }
  Given path 'home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)
  # La membresía de 'b' tiene id=2 según import.sql
  Given path 'piso/promover-admin'
  And form field membresiaId = '2'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Panel del piso'
  # Tras la promoción, 'b' debe aparecer con rol Admin
  And match response contains 'Admin'

# ------------------------------------------------------------------
# 5. El último miembro activo abandona → el piso se elimina
# ------------------------------------------------------------------
Scenario: último miembro activo abandona y el piso es eliminado
  # c es el único miembro de 'Piso Admin Test' (creado en escenario 1)
  * call read('helpers/login.feature') { username: 'c', password: 'aa' }
  Given path 'home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'piso/abandonar'
  And form field _csrf = csrf
  When method post
  Then status 200
  # El mensaje de flash confirma la eliminación del piso
  And match response contains 'eliminado al no quedar miembros'
  And match response contains 'Crear un piso'
