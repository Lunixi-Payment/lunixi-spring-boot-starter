package com.lunixi.example;

import java.io.IOException;
import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bounded informational POST-to-GET bridge. It never loads a customer/order or interprets callback fields. */
@RestController
public final class ReturnAdapterController {
    @PostMapping("/payments/{id:[1-9][0-9]*}/return")
    public ResponseEntity<Void> receive(@PathVariable("id") long id, HttpServletRequest request) throws IOException {
        if (id < 1) { return ResponseEntity.notFound().build(); }
        if (request.getContentLengthLong() > 65_536 || request.getInputStream().readNBytes(65_537).length > 65_536) { return ResponseEntity.status(413).build(); }
        // Cross-site POST may omit SameSite=Lax cookies. GET restores normal session/ownership checks.
        URI target = URI.create(request.getContextPath() + "/payments/" + id + "/return");
        return ResponseEntity.status(303).location(target).header("Cache-Control", "no-store").build();
    }
}
