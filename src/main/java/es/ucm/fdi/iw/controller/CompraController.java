package es.ucm.fdi.iw.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import es.ucm.fdi.iw.dto.ApiResponse;
import es.ucm.fdi.iw.dto.ItemForm;
import es.ucm.fdi.iw.dto.ListaForm;
import es.ucm.fdi.iw.dto.ProductoForm;
import es.ucm.fdi.iw.dto.RegistrarCompraForm;
import es.ucm.fdi.iw.model.Piso;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.service.CompraService;
import es.ucm.fdi.iw.service.PisoService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Controlador del módulo de Compra.
 * <p>
 * Cubre el ciclo completo: catálogo → lista activa → registro de compra.
 * Toda la lógica de negocio vive en {@link CompraService}.
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/compra")
public class CompraController extends BaseController {

    private static final Logger log = LogManager.getLogger(CompraController.class);

    private final PisoService pisoService;
    private final CompraService compraService;

    /**
     * Vista principal de la compra.
     * Separa las listas activas de las completadas y carga el historial de compras.
     */
    @GetMapping
    public String compra(Model model, HttpSession session) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso != null) {
            model.addAttribute("piso",              piso);
            model.addAttribute("listasActivas",     compraService.obtenerListasActivas(piso.getId()));
            model.addAttribute("items",             compraService.obtenerItems(piso.getId()));
            model.addAttribute("productos",         compraService.obtenerProductos(piso.getId()));
            model.addAttribute("historialCompras",  compraService.obtenerHistorialCompras(piso.getId()));
        }
        return "compra";
    }

    @GetMapping("/gestion")
    public String gestionCompra(Model model, HttpSession session) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso == null) return "redirect:/login";
        model.addAttribute("piso",      piso);
        model.addAttribute("listas",    compraService.obtenerListas(piso.getId()));
        model.addAttribute("productos", compraService.obtenerProductosOrdenados(piso.getId()));
        model.addAttribute("miembros",  compraService.obtenerMiembrosActivos(piso.getId()));
        return "compra-gestion";
    }

    /** Cualquier miembro del piso puede crear una nueva lista de la compra. */
    @PostMapping("/lista")
    public String crearLista(@Valid @ModelAttribute ListaForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/compra/gestion";
        }
        compraService.crearLista(form, piso);
        ra.addFlashAttribute("flashSuccess", "Lista creada correctamente.");
        return "redirect:/compra/gestion";
    }

    @PostMapping("/producto")
    public String crearProducto(@Valid @ModelAttribute ProductoForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/compra/gestion";
        }
        compraService.crearProducto(form, piso);
        ra.addFlashAttribute("flashSuccess", "Producto añadido al catálogo.");
        return "redirect:/compra/gestion";
    }

    @PostMapping("/item")
    public String crearItem(@Valid @ModelAttribute ItemForm form, BindingResult errors,
            HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/compra";
        }
        try {
            compraService.crearItem(form, u.getId(), piso.getId());
            ra.addFlashAttribute("flashSuccess", "Producto añadido a la lista.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("crearItem: {}", e.getMessage());
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/compra";
    }

    /**
     * Alterna el estado comprado/pendiente de un ítem.
     * Devuelve {@code { ok, message, data: { comprado } }}.
     * Las excepciones de negocio son gestionadas por {@link GlobalExceptionHandler}.
     */
    @PostMapping("/item/{id}/toggle")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> toggleItem(
            @PathVariable long id, HttpSession session) {
        Piso piso = pisoService.resolverPiso(session);
        if (piso == null)
            return ResponseEntity.status(401).body(ApiResponse.error("no autorizado"));
        boolean comprado = compraService.toggleItem(id, piso.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("comprado", comprado)));
    }

    /**
     * Registra una compra física cerrando la lista asociada.
     * Si {@code crearGasto} está marcado, genera un gasto compartido.
     * Redirige a la vista principal con un mensaje flash de éxito o error.
     */
    @PostMapping("/registrar")
    public String registrarCompra(@Valid @ModelAttribute RegistrarCompraForm form,
            BindingResult errors, HttpSession session, RedirectAttributes ra) {
        User u = (User) session.getAttribute("u");
        Piso piso = pisoService.resolverPiso(session);
        if (u == null || piso == null) return "redirect:/login";
        if (errors.hasErrors()) {
            ra.addFlashAttribute("flashError", errors.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/compra";
        }
        try {
            compraService.registrarCompra(form, u, piso);
            ra.addFlashAttribute("flashSuccess", "Compra registrada correctamente."
                + (form.isCrearGasto() ? " Se ha generado un gasto compartido." : ""));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("registrarCompra: {}", e.getMessage());
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/compra";
    }
}
