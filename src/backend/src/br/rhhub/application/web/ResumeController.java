package br.rhhub.application.web;

import br.rhhub.application.web.dto.Views;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.service.ResumeService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumes;
    private final WebSupport web;

    public ResumeController(ResumeService resumes, WebSupport web) {
        this.resumes = resumes;
        this.web = web;
    }

    /** multipart: vacancyId e file (PDF ou DOCX). Responde 201 e já deixa a análise na fila. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Mono<Views.ResumeView> submit(@RequestPart("vacancyId") String vacancyId, @RequestPart("file") FilePart file,
            ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> web
                .read(file, ResumeService.MAX_BYTES, "O currículo pode ter no máximo 5 MB.")
                .flatMap(bytes -> resumes.submit(actor, WebSupport.parseId(vacancyId, "Vaga"), file.filename(), bytes)))
                .map(Views::resume);
    }

    @GetMapping("/me")
    Mono<List<Views.ResumeView>> mine(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> resumes.listMine(actor).map(Views::resume).collectList());
    }

    /** Só admin. Filtros opcionais: status e vacancyId. */
    @GetMapping
    Mono<List<Views.ResumeView>> all(@RequestParam(required = false) String status,
            @RequestParam(required = false) String vacancyId, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> resumes
                .listAll(actor, parseStatus(status), vacancyId == null ? null : WebSupport.parseId(vacancyId, "Vaga"))
                .map(Views::resume).collectList());
    }

    @GetMapping("/{id}")
    Mono<Views.ResumeView> get(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> resumes.get(actor, WebSupport.parseId(id, "Id")))
                .map(Views::resume);
    }

    @GetMapping("/{id}/file")
    Mono<ResponseEntity<byte[]>> file(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> resumes.file(actor, WebSupport.parseId(id, "Id")))
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.contentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                        .body(file.content()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> resumes.delete(actor, WebSupport.parseId(id, "Id")));
    }

    private static ResumeStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ResumeStatus.from(raw);
        } catch (RuntimeException e) {
            throw new DomainException.Invalid("Status inválido: use pending, aproved ou recuse.");
        }
    }
}
