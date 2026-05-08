Feature: sucesión automática de ADMIN y blindaje de promoción

# Este feature debe ejecutarse el último (prefijo 'z') porque sus escenarios
# son destructivos: a y b abandonan Piso Moncloa de forma secuencial.
#
# Estado inicial esperado (acumulado de features previos):
#   a → ADMIN  membresía id=1  (import.sql)
#   b → ADMIN  membresía id=2  (promovido en habitaciones_admin.feature)
#   c → MIEMBRO membresía id=? (unido en piso.feature)
#
# Cada escenario hace su propio login; las sesiones no se comparten.
# El estado de la BD SÍ se comparte: cada escenario parte del resultado del anterior.

Background:
  * url baseUrl

# ------------------------------------------------------------------
# 1. MIEMBRO no puede promover a nadie → falla con mensaje claro
# ------------------------------------------------------------------
Scenario: usuario MIEMBRO intenta promover a otro miembro y es rechazado
  * call read('helpers/login.feature') { username: 'c', password: 'aa' }
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  # c (MIEMBRO) intenta promover la membresía id=1 (usuario a, que es ADMIN)
  Given path 'modulos/piso/promover-admin'
  And form field membresiaId = '1'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'Solo los administradores'

# ------------------------------------------------------------------
# 2. ADMIN intenta promover membresía con id inexistente → falla
# ------------------------------------------------------------------
Scenario: ADMIN intenta promover membresía inexistente y es rechazado
  * call read('helpers/login.feature') { username: 'a', password: 'aa' }
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/piso/promover-admin'
  And form field membresiaId = '999999'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'no encontrada'

# ------------------------------------------------------------------
# 3. ADMIN abandona cuando existe otro ADMIN → abandono simple, sin reasignación
# ------------------------------------------------------------------
Scenario: ADMIN abandona con otro ADMIN activo y el piso no reasigna nada
  # a abandona; b sigue siendo ADMIN → resultado: ABANDONADO (no REASIGNACION)
  * call read('helpers/login.feature') { username: 'a', password: 'aa' }
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/piso/abandonar'
  And form field _csrf = csrf
  When method post
  Then status 200
  # El flash de ABANDONADO dice "Puedes crear o unirte a otro"
  # El flash de ABANDONADO_REASIGNACION diría "nuevo administrador automáticamente"
  And match response contains 'Puedes crear'
  And match response !contains 'nuevo administrador'
  And match response contains 'Crear un piso'

# ------------------------------------------------------------------
# 4. ADMIN intenta promover una membresía ya inactiva → falla
# ------------------------------------------------------------------
Scenario: ADMIN intenta promover membresía con fechaSalida ya fijada y es rechazado
  # La membresía de 'a' (id=1) quedó inactiva en el escenario anterior
  * call read('helpers/login.feature') { username: 'b', password: 'aa' }
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/piso/promover-admin'
  And form field membresiaId = '1'
  And form field _csrf = csrf
  When method post
  Then status 200
  And match response contains 'ya inactiva'

# ------------------------------------------------------------------
# 5. Único ADMIN abandona → se reasigna automáticamente al miembro más antiguo
# ------------------------------------------------------------------
Scenario: único ADMIN abandona y el sistema promueve automáticamente al miembro más antiguo
  # b es ahora el único ADMIN (a abandonó); c es MIEMBRO
  # Al abandonar b, c queda como único miembro → c es promovido a ADMIN
  * call read('helpers/login.feature') { username: 'b', password: 'aa' }
  Given path 'modulos/home'
  When method get
  Then status 200
  * def csrf = karate.extract(response, 'name="_csrf" value="([^"]*)"', 1)

  Given path 'modulos/piso/abandonar'
  And form field _csrf = csrf
  When method post
  Then status 200
  # El flash de ABANDONADO_REASIGNACION confirma la promoción automática
  And match response contains 'nuevo administrador automáticamente'
  And match response contains 'Crear un piso'

# ------------------------------------------------------------------
# 6. Verificar que la reasignación fue al miembro correcto (c)
# ------------------------------------------------------------------
Scenario: verificar que el miembro promovido automáticamente tiene ahora rol ADMIN
  # c debería ser ADMIN tras el escenario anterior
  * call read('helpers/login.feature') { username: 'c', password: 'aa' }
  Given path 'modulos/home'
  When method get
  Then status 200
  And match response contains 'Panel del piso'
  # c debe aparecer con rol Admin en la tabla de miembros
  And match response contains 'Admin'
  # El piso sigue existiendo (c no lo ha abandonado)
  And match response contains 'Piso Moncloa'
