Feature: flujos de dominio autocontenidos: crear, verificar y limpiar

  # ──────────────────────────────────────────────────────────────────────────
  # Los tres escenarios generan sus propios datos con nombres únicos y no
  # dependen de contenido preexistente en la BD.
  #
  # Escenarios 1 y 2 (Tarea, Gasto): crean el dato, verifican flash de éxito
  # y presencia visible en la UI. No existe endpoint de borrado para estas
  # entidades; la BD H2 se reinicia en cada ejecución del test, por lo que
  # los datos creados no persisten entre runs.
  #
  # Escenario 3 (BloqueHorario): implementa el ciclo completo
  #   CREAR → VERIFICAR → ELIMINAR
  # usando el único endpoint de borrado disponible en la aplicación:
  #   DELETE /calendario/bloque/{id}
  # ──────────────────────────────────────────────────────────────────────────

  Scenario: crear una tarea puntual y verificar que aparece en la lista de pendientes
    Given call read('login.feature@login_b')
    And driver baseUrl + '/tareas'
    Then waitFor('h1')
    And def nombre = script("'Tarea E2E ' + Date.now()")
    # Crea un form dinámico para evitar seleccionar el form de logout del nav,
    # que aparece primero en el DOM. Incluye el token CSRF de config.csrf.
    And def createScript = 'var f=document.createElement("form");f.method="POST";f.action="/tareas";[["nombre","' + nombre + '"],["tipo","PUNTUAL"],["fechaLimite","2026-12-31"],[config.csrf.name,config.csrf.value]].forEach(function(p){var i=document.createElement("input");i.name=p[0];i.value=p[1];f.appendChild(i);});document.body.appendChild(f);f.submit()'
    When submit().script(createScript)
    Then waitFor('.alert-success')
    And match html('.alert-success') contains 'Tarea creada correctamente'
    And match html('body') contains nombre

  Scenario: crear un gasto compartido y verificar que aparece en el historial
    Given call read('login.feature@login_a')
    And driver baseUrl + '/gastos'
    Then waitFor('#balance')
    And def concepto = script("'Gasto E2E ' + Date.now()")
    # La form del modal existe en el DOM aunque el modal esté cerrado;
    # se rellenan los campos y se envía programáticamente.
    And def createScript = 'document.querySelector("#concepto").value="' + concepto + '";document.querySelector("#importe").value="30.00";document.querySelector("#modal-nuevo-gasto form").submit()'
    When submit().script(createScript)
    Then waitFor('.alert-success')
    And match html('.alert-success') contains 'Gasto registrado correctamente'
    And match html('#historial') contains concepto

  Scenario: crear un bloque horario personal, verificarlo en el feed y eliminarlo
    # Ciclo completo: CREAR (POST /calendario/bloque) →
    #                 VERIFICAR (GET /calendario/feed) →
    #                 ELIMINAR (DELETE /calendario/bloque/{id}) →
    #                 CONFIRMAR ausencia en el feed
    Given call read('login.feature@login_b')
    And driver baseUrl + '/calendario'
    Then waitFor('#syncflat-calendar')
    And def desc = script("'Bloque E2E ' + Date.now()")

    # ── 1. CREAR: envío de formulario al endpoint MVC → redirect con flash ──
    And def createScript = 'var f=document.createElement("form");f.method="POST";f.action="/calendario/bloque";[["tipo","TRABAJO"],["descripcion","' + desc + '"],["inicio","2099-01-15T10:00"],["fin","2099-01-15T12:00"],["tipoRecurrencia","NINGUNA"],[config.csrf.name,config.csrf.value]].forEach(function(p){var i=document.createElement("input");i.name=p[0];i.value=p[1];f.appendChild(i);});document.body.appendChild(f);f.submit()'
    When submit().script(createScript)
    Then waitFor('.alert-success')
    And match html('.alert-success') contains 'Bloque horario añadido'

    # ── 2. VERIFICAR: el bloque aparece en el feed y obtenemos su ID ──
    And def feedScript = '(function(){var x=new XMLHttpRequest();x.open("GET","/calendario/feed?start=2099-01-14&end=2099-01-16",false);x.send(null);var ev=JSON.parse(x.responseText).data.filter(function(e){return e.extendedProps&&e.extendedProps.descripcion==="' + desc + '"});return ev.length>0?ev[0].extendedProps.bloqueId:null;})()'
    And def bloqueId = script(feedScript)
    Then match bloqueId != null

    # ── 3. ELIMINAR: DELETE al endpoint REST ──
    And def deleteScript = '(function(){var x=new XMLHttpRequest();x.open("DELETE","/calendario/bloque/' + bloqueId + '",false);x.setRequestHeader("X-CSRF-TOKEN",config.csrf.value);x.send(null);return x.status;})()'
    And def deleteStatus = script(deleteScript)
    Then match deleteStatus == 200

    # ── 4. CONFIRMAR: el bloque ya no aparece en el feed ──
    And def verifyScript = '(function(){var x=new XMLHttpRequest();x.open("GET","/calendario/feed?start=2099-01-14&end=2099-01-16",false);x.send(null);return JSON.parse(x.responseText).data.some(function(e){return e.extendedProps&&e.extendedProps.descripcion==="' + desc + '"});})()'
    And def stillExists = script(verifyScript)
    Then match stillExists == false
