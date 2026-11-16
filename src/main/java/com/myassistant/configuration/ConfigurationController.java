package com.myassistant.configuration;

import com.myassistant.configuration.dto.ConfigurationResponse;
import com.myassistant.configuration.dto.CreateConfigurationRequest;
import com.myassistant.configuration.dto.UpdateConfigurationRequest;
import com.myassistant.shared.security.CurrentUser;
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

// Every endpoint works on the logged-in user's configurations only; another user's configuration is a 404.
@RestController
@RequestMapping("/api/configurations")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "No token, or the token is invalid or expired",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class ConfigurationController {
    private final ConfigurationService configurationService;

    @ApiResponse(responseCode = "200", description = "My assistants, favorites first, then by name; each with its template")
    @GetMapping
    List<ConfigurationResponse> findAll() {
        return configurationService.findAll(CurrentUser.id());
    }

    @ApiResponse(responseCode = "200", description = "The assistant with its template embedded and the variable values "
            + "resolved against the template's defaults: everything needed to start a chat")
    @ApiResponse(responseCode = "404", description = "No such assistant among mine",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    ConfigurationResponse findById(@PathVariable String id) {
        return configurationService.findById(CurrentUser.id(), id);
    }

    @ApiResponse(responseCode = "201", description = "Created; the Location header points to the new assistant")
    @ApiResponse(responseCode = "400", description = "Invalid fields or settings (fieldErrors), or variable values the "
            + "template does not declare (unknownVariables)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "templateId is not one of my templates",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    ResponseEntity<ConfigurationResponse> create(@Valid @RequestBody CreateConfigurationRequest request) {
        return created(configurationService.create(CurrentUser.id(), request));
    }

    @ApiResponse(responseCode = "200", description = "The updated assistant, with its new version")
    @ApiResponse(responseCode = "400", description = "Invalid fields, a missing version, or unknown variables, as on create",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such assistant among mine, or templateId is not one of my templates",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The version is stale: the assistant was saved in the meantime",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping("/{id}")
    ConfigurationResponse update(@PathVariable String id, @Valid @RequestBody UpdateConfigurationRequest request) {
        return configurationService.update(CurrentUser.id(), id, request);
    }

    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such assistant among mine",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    void delete(@PathVariable String id) {
        configurationService.delete(CurrentUser.id(), id);
    }

    @ApiResponse(responseCode = "201", description = "A copy named \"… (copy)\", not a favorite; Location points to it")
    @ApiResponse(responseCode = "404", description = "No such assistant among mine",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{id}/duplicate")
    ResponseEntity<ConfigurationResponse> duplicate(@PathVariable String id) {
        return created(configurationService.duplicate(CurrentUser.id(), id));
    }

    private static ResponseEntity<ConfigurationResponse> created(ConfigurationResponse configuration) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/configurations/{id}")
                .buildAndExpand(configuration.id())
                .toUri();
        return ResponseEntity.created(location).body(configuration);
    }
}
