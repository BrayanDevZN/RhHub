package br.rhhub.application.web;

import br.rhhub.application.web.dto.Views;
import br.rhhub.service.VacancyService;
import br.rhhub.domain.model.ProfilePhoto;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
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

/** Rotas /api/vacancies: listar, ver, criar, editar e apagar vagas, e a foto da vaga. */
@RestController
@RequestMapping("/api/vacancies")
public class VacancyController {

    /**
     * Corpo de criar ou editar vaga: nome, descrição, requisitos, responsabilidades, etapas e prazo de expiração (ISO-8601).
     */
    public record VacancyRequest(String name, String description, String requirements, String responsibilities,
            List<String> stages, String expiresAt) {
    }

    private final VacancyService vacancies;
    private final WebSupport web;

    /**
     * Recebe os serviços de que as rotas precisam; o Spring os injeta.
     */
    public VacancyController(VacancyService vacancies, WebSupport web) {
        this.vacancies = vacancies;
        this.web = web;
    }

    /**
     * GET /api/vacancies. Lista as vagas. Só administradores veem o número de candidatos e quem criou.
     */
    @GetMapping
    Mono<List<Views.VacancyView>> list(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies.list()
                .map(v -> Views.vacancy(v, actor.isAdmin())).collectList());
    }

    /**
     * GET /api/vacancies/{id}. Mostra uma vaga.
     */
    @GetMapping("/{id}")
    Mono<Views.VacancyView> get(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies.get(WebSupport.parseId(id, "Id"))
                .map(v -> Views.vacancy(v, actor.isAdmin())));
    }

    /**
     * POST /api/vacancies. Cria uma vaga (só administradores, responde 201).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Mono<Views.VacancyView> create(@RequestBody VacancyRequest body, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies.create(actor, body.name(), body.description(), body.requirements(), body.responsibilities(), body.stages(), body.expiresAt())
                .map(v -> Views.vacancy(v, true)));
    }

    /**
     * PATCH /api/vacancies/{id}. Edita uma vaga (só administradores). Campos ausentes mantêm o valor atual.
     */
    @PatchMapping("/{id}")
    Mono<Views.VacancyView> update(@PathVariable String id, @RequestBody VacancyRequest body, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies
                .update(actor, WebSupport.parseId(id, "Id"), body.name(), body.description(), body.requirements(),
                        body.responsibilities(), body.stages(), body.expiresAt())
                .map(v -> Views.vacancy(v, true)));
    }

    /** multipart: file (JPEG, PNG ou WebP, até 2 MB). Só admin. */
    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<Map<String, Object>> uploadPhoto(@PathVariable String id, @RequestPart("file") FilePart file,
            ServerWebExchange exchange) {
        return web.actor(exchange)
                .flatMap(actor -> web.read(file, ProfilePhoto.MAX_BYTES, "A foto pode ter no máximo 2 MB.")
                        .flatMap(bytes -> vacancies.uploadPhoto(actor, WebSupport.parseId(id, "Id"), bytes)))
                .then(Mono.fromSupplier(() -> Map.<String, Object>of("status", true)));
    }

    /**
     * GET /api/vacancies/{id}/photo. Devolve a foto da vaga, com cache de 5 minutos no navegador (quem troca a foto força a atualização com ?v=).
     */
    @GetMapping("/{id}/photo")
    Mono<ResponseEntity<byte[]>> photo(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).then(Mono.defer(() -> vacancies.photo(WebSupport.parseId(id, "Id"))))
                .map(photo -> ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                        // muda pouco e a lista pede uma por vaga: 5 min em cache; quem troca a foto força com ?v=
                        .header("Cache-Control", "private, max-age=300").body(photo.content()));
    }

    /**
     * DELETE /api/vacancies/{id}/photo. Remove a foto da vaga (204).
     */
    @DeleteMapping("/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> deletePhoto(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies.removePhoto(actor, WebSupport.parseId(id, "Id")));
    }

    /**
     * DELETE /api/vacancies/{id}. Apaga a vaga e, em cascata, as candidaturas dela (204).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> vacancies.delete(actor, WebSupport.parseId(id, "Id")));
    }
}
