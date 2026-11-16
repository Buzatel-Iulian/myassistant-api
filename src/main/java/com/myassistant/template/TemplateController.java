package com.myassistant.template;

import com.myassistant.shared.security.CurrentUser;
import com.myassistant.template.dto.CreateTemplateRequest;
import com.myassistant.template.dto.TemplateResponse;
import com.myassistant.template.dto.UpdateTemplateRequest;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

import static org.springframework.http.HttpStatus.NO_CONTENT;

// Every endpoint works on the logged-in user's templates only; another user's template is a 404.
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "No token, or the token is invalid or expired",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class TemplateController {
    private final TemplateService templateService;

    @ApiResponse(responseCode = "200", description = "My templates, sorted by name")
    @GetMapping
    List<TemplateResponse> findAll() {
        return templateService.findAll(CurrentUser.id());
    }

    @ApiResponse(responseCode = "200", description = "The template, with the version to send back on update")
    @ApiResponse(responseCode = "404", description = "No such template among mine",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    TemplateResponse findById(@PathVariable String id) {
        return templateService.findById(CurrentUser.id(), id);
    }

    @ApiResponse(responseCode = "201", description = "Created; the Location header points to the new template")
    @ApiResponse(responseCode = "400", description = "Invalid fields (fieldErrors), or the content uses {{placeholders}} "
            + "that are not declared as variables (unknownPlaceholders), or two variables share a name (duplicateVariables)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    ResponseEntity<TemplateResponse> create(@Valid @RequestBody CreateTemplateRequest request) {
        TemplateResponse created = templateService.create(CurrentUser.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @ApiResponse(responseCode = "200", description = "The updated template, with its new version")
    @ApiResponse(responseCode = "400", description = "Invalid fields, a missing version, or undeclared placeholders, "
            + "as on create",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such template among mine",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The version is stale: the template was saved in the meantime",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping("/{id}")
    TemplateResponse update(@PathVariable String id, @Valid @RequestBody UpdateTemplateRequest request) {
        return templateService.update(CurrentUser.id(), id, request);
    }

    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such template among mine",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Assistants still use the template; configurations lists their names",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    void delete(@PathVariable String id) {
        templateService.delete(CurrentUser.id(), id);
    }
}
