package com.xqfx.requirements.aiconfig;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/ai-config")
class AiConfigController {

    private final AiConfigService service;

    AiConfigController(AiConfigService service) {
        this.service = service;
    }

    @GetMapping
    AiConfigResponse get() {
        return service.getConfig();
    }

    @PutMapping
    AiConfigResponse save(@RequestBody AiConfigSaveRequest request) {
        return service.saveConfig(request);
    }

    @PostMapping("/test")
    Map<String, Object> test() {
        try {
            boolean success = service.testConnection();
            return Map.of("success", success);
        } catch (AiConfigService.AiConnectionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
