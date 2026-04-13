package es.ucm.fdi.iw.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.ui.Model;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.ucm.fdi.iw.model.Topic;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.model.User.Role;
import io.karatelabs.js.Context;
import io.karatelabs.js.Interpreter;
import io.karatelabs.js.Node;
import io.karatelabs.js.Parser;
import io.karatelabs.js.Source;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 * API REST de la aplicación.
 * <p>
 * Endpoints de uso general: mensajería STOMP, consultas de estado y
 * ejecución de scripts JS con karate-js. El prefijo {@code /api/**} es
 * público por configuración, por lo que cada endpoint debe protegerse
 * individualmente si lo requiere.
 */
@RestController
@RequestMapping("api")
public class ApiController {

  @Autowired
  private EntityManager entityManager;

  private static final Logger log = LogManager.getLogger(ApiController.class);

  /** Endpoint de diagnóstico: devuelve el mensaje recibido como campo JSON. */
  @GetMapping("/status/{message}")
  public Map<String, String> check(@PathVariable String message) {
    return Map.of("coder", message);
  }

  /** Devuelve el número total de usuarios registrados en la base de datos. */
  @GetMapping("/users/count")
  public Map<String, Long> usersCount() {
    return Map.of("count",
        (Long) entityManager.createQuery("SELECT COUNT(u) FROM User u").getSingleResult());
  }

  /**
   * Carga un fichero del classpath, incluyendo el caso en que esté dentro de un JAR.
   *
   * @param path ruta relativa a {@code target/classes}
   * @return fichero localizado
   */
  private File loadFromClasspath(String path) {
      try {
          return ResourceUtils.getFile("classpath:"+path);
      } catch (FileNotFoundException e) {
          throw new RuntimeException("Could not load file from classpath: "+path, e);
      }
  }

  /**
   * Evalúa código JavaScript mediante karate-js, con variables opcionales inyectadas en el contexto.
   *
   * @param source código JS a ejecutar
   * @param vars   variables que se declaran en el contexto antes de la evaluación
   * @return resultado de la evaluación
   */
  private Object eval(String source, Map<String, Object> vars) {
    Parser parser = new Parser(new Source(source));
    Node node = parser.parse();
    Context context = Context.root();
    if (vars != null) {
        vars.forEach((k, v) -> context.declare(k, v));
    }
    return Interpreter.eval(node, context);
  }

  /**
   * Endpoint de prueba que carga {@code static/js/js-eval.js} del classpath
   * y lo ejecuta con karate-js, devolviendo el resultado.
   */
  @GetMapping(value = "/js", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String,String> testJs() throws Exception{
    String start = Files.readString(
      loadFromClasspath("static/js/js-eval.js").toPath());
    String source = start + "\n" + "f(v);";

    Object result = eval(source, Map.of(
      "v", 10, 
      "exampleExternalVar", "patata"));
    return Map.of("result", result.toString());
  }

  @Autowired
  private SimpMessagingTemplate messagingTemplate;

  /**
   * Publica un mensaje en un tópico STOMP y lo persiste en base de datos.
   * Solo pueden enviar mensajes los miembros del tópico o los administradores.
   *
   * @param name clave del tópico destino
   * @param o    cuerpo JSON con el campo {@code message}
   * @throws JsonProcessingException si la serialización JSON falla
   */
  @PostMapping("/topic/{name}")
  @ResponseBody
  @Transactional
  public Map<String,String> postMsg(@PathVariable String name,
      @RequestBody JsonNode o, Model model, HttpSession session,
      HttpServletResponse response)
      throws JsonProcessingException {

    String text = o.get("message").asText();
    User sender = entityManager.find(
        User.class, ((User) session.getAttribute("u")).getId());
    Topic target = entityManager.createNamedQuery("Topic.byKey", Topic.class)
        .setParameter("key", name).getSingleResult();

    if (! sender.hasRole(Role.ADMIN) && ! target.getMembers().contains(sender)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return Map.of("error", "user not in group");
    }

    Message m = new Message();
    m.setRecipient(null);
    m.setSender(sender);
    m.setTopic(target);
    m.setDateSent(LocalDateTime.now());
    m.setText(text);
    entityManager.persist(m);
    entityManager.flush();

    String json = new ObjectMapper().writeValueAsString(m.toTransfer());
    log.info("Sending a message to  group {} with contents '{}'", target.getName(), json);
    messagingTemplate.convertAndSend("/topic/" + name, json);
    return Map.of("result", "message sent");
  }

  /**
   * Devuelve todos los mensajes de un tópico en formato JSON.
   * Solo accesible para miembros del tópico o administradores.
   *
   * @param name clave del tópico
   * @throws JsonProcessingException si la serialización JSON falla
   */
  @GetMapping("/topic/{name}")
  @ResponseBody
  @Transactional
  public Map<String,String> getMessages(@PathVariable String name, HttpSession session,
        HttpServletResponse response)
      throws JsonProcessingException {

      User requester = entityManager.find(
          User.class, ((User) session.getAttribute("u")).getId());
      Topic target = entityManager.createNamedQuery("Topic.byKey", Topic.class)
          .setParameter("key", name).getSingleResult();

      if (! requester.hasRole(Role.ADMIN) && ! target.getMembers().contains(requester)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return Map.of("error", "user not in group");
      }
      return Map.of("messages", new ObjectMapper().writeValueAsString(
        target.getMessages().stream()
          .map(Message::toTransfer).toArray()
      ));
  }
}
