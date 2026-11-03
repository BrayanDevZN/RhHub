package br.rhhub.application.web;

import br.rhhub.application.web.dto.Views;
import br.rhhub.service.VacancyService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/vacancies")
public class VacancyController {

    public record VacancyRequest(String name, String description) {
    }

    private final VacancyService vacancies;
    private final WebSupport web;

    public VacancyController(VacancyService vacancies, WebSupport web) {
        this.vacancies = vacancies;
        this.web = web;
    }

    @GetMapping
    Mono<List<Views.VacancyView>> list(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies.list()
                .map(v -> Views.vacancy(v, actor.isAdmin())).collectList());
    }

    @GetMapping("/{id}")
    Mono<Views.VacancyView> get(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies.get(WebSupport.parseId(id, "Id"))
                .map(v -> Views.vacancy(v, actor.isAdmin())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Mono<Views.VacancyView> create(@RequestBody VacancyRequest body, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies.create(actor, body.name(), body.description())
                .map(v -> Views.vacancy(v, true)));
    }

    @PatchMapping("/{id}")
    Mono<Views.VacancyView> update(@PathVariable String id, @RequestBody VacancyRequest body, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies
                .update(actor, WebSupport.parseId(id, "Id"), body.name(), body.description())
                .map(v -> Views.vacancy(v, true)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies.delete(actor, WebSupport.parseId(id, "Id")));
    }
}
