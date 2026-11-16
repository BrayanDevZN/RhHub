package br.rhhub.application.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/health: responde ok, para a plataforma saber que a API está de pé. */
@RestController
public class HealthController {

    /**
     * GET /api/health. Responde {"status": "ok"}. Rota pública, usada pela plataforma e pelo Docker para saber se a API está de pé.
     */
    @GetMapping("/api/health")
    Map<String, Object> health() {
        return Map.of("status", "ok");
    }
}
