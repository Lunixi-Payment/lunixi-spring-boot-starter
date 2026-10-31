package com.lunixi.example;

import com.lunixi.sdk.payment.CheckoutSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class CheckoutController {
    private final OrderRepository orders;
    private final CheckoutFlow flow;
    public CheckoutController(OrderRepository orders, CheckoutFlow flow) { this.orders = orders; this.flow = flow; }
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping({"/", "/orders"}) public String orders(Authentication customer, Model model) { model.addAttribute("orders", orders.ownedOrders(customer.getName())); return "orders"; }
    @PostMapping("/payments/{id}") public ModelAndView checkout(@PathVariable("id") long id, Authentication customer, HttpServletResponse response) {
        NativeOrder nativeOrder = orders.owned(id, customer.getName());
        CheckoutSession session;
        try { session = flow.start(id, customer.getName()); }
        catch (ResponseStatusException denied) { throw denied; }
        catch (RuntimeException unknown) { response.setStatus(409); return new ModelAndView("review"); }
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Referrer-Policy", "no-referrer");
        if ("hosted".equals(nativeOrder.mode())) {
            String hostedUrl;
            try { hostedUrl = session.hostedUrl(); }
            catch (RuntimeException unavailable) { response.setStatus(409); return new ModelAndView("review"); }
            RedirectView redirect = new RedirectView(hostedUrl, false); redirect.setStatusCode(HttpStatus.SEE_OTHER);
            return new ModelAndView(redirect);
        }
        return new ModelAndView("checkout", java.util.Map.of("browserToken", session.token()));
    }
    @GetMapping("/payments/{id}/return") public String returned(@PathVariable("id") long id, Authentication customer, Model model) {
        // Deliberately no status, amount or paymentId request parameter; browser return is read-only.
        model.addAttribute("orders", java.util.List.of(orders.owned(id, customer.getName()))); return "orders";
    }
}
