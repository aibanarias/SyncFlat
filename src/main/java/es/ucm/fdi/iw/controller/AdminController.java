package es.ucm.fdi.iw.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.Transferable;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 * Controlador de administración.
 * <p>
 * Agrupa las operaciones reservadas al rol ADMIN: listado de usuarios
 * y activación/desactivación de cuentas.
 * El acceso está restringido por {@link es.ucm.fdi.iw.SecurityConfig}.
 */
@Controller
@RequestMapping("admin")
public class AdminController extends BaseController {

  @Autowired
  private EntityManager entityManager;

  private static final Logger log = LogManager.getLogger(AdminController.class);

  @GetMapping({"", "/"})
  public String index(Model model) {
    log.info("Admin acaba de entrar");
    model.addAttribute("users",
        entityManager.createQuery("select u from User u").getResultList());
    return "admin";
  }

  @PostMapping("/toggle/{id}")
  @Transactional
  @ResponseBody
  public String toggleUser(@PathVariable long id, Model model) {
    log.info("Admin cambia estado de " + id);
    User target = entityManager.find(User.class, id);
    target.setEnabled(!target.isEnabled());
    return "{\"enabled\":" + target.isEnabled() + "}";
  }

  /**
   * Devuelve los últimos mensajes del sistema en formato JSON.
   * El parámetro {@code setFirstResult} permite paginar cambiando el offset.
   */
  @GetMapping(path = "all-messages", produces = "application/json")
  @Transactional
  @ResponseBody
  public List<Message.Transfer> retrieveMessages(HttpSession session) {
    TypedQuery<Message> query = entityManager.createQuery("select m from Message m", Message.class);
    query.setMaxResults(5);
    query.setFirstResult(0);
    return query.getResultList().stream().map(Transferable::toTransfer)
        .collect(Collectors.toList());
  }

}
