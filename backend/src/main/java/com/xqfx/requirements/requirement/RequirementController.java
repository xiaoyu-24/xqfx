package com.xqfx.requirements.requirement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/requirements")
class RequirementController {

    private final RequirementService service;
    private final AttachmentService attachments;

    RequirementController(RequirementService service, AttachmentService attachments) {
        this.service = service;
        this.attachments = attachments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RequirementResponse create(@Valid @RequestBody CreateRequirementRequest request) {
        return service.create(request.requesterName(), request.departmentId(), request.title(), request.typeId(),
                request.content(), request.systemId(), request.targetVersionId(), request.periodStartDate(),
                request.periodEndDate(), request.newSystem() == null ? null : request.newSystem().name(),
                request.newSystem() == null ? null : request.newSystem().ownerName(),
                request.newSystem() == null ? null : request.newSystem().collaborators());
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    RequirementResponse createDraft(@RequestBody DraftRequirementRequest request) {
        return service.createDraft(request.requesterName(), request.departmentId(), request.title(), request.typeId(),
                request.content(), request.systemId(), request.targetVersionId(), request.periodStartDate(),
                request.periodEndDate());
    }

    @GetMapping
    List<RequirementResponse> list(@RequestParam(required = false) Long typeId,
                                   @RequestParam(required = false) Long systemId,
                                   @RequestParam(required = false) RequirementSaveType saveType) {
        return service.list(typeId, systemId, saveType);
    }

    @GetMapping("/page")
    RequirementPageResponse page(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) Long systemId,
                                 @RequestParam(defaultValue = "false") boolean unassignedSystem,
                                 @RequestParam(required = false) Long targetVersionId,
                                 @RequestParam(required = false) Long departmentId,
                                 @RequestParam(required = false) String requesterName,
                                 @RequestParam(required = false) Long typeId,
                                 @RequestParam(required = false) RequirementStatus status,
                                 @RequestParam(required = false) RequirementSaveType saveType,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) LocalDate submittedFrom,
                                 @RequestParam(required = false) LocalDate submittedTo,
                                 @RequestParam(required = false) LocalDate periodOverlapStart,
                                 @RequestParam(required = false) LocalDate periodOverlapEnd) {
        return service.page(page, size, systemId, unassignedSystem, targetVersionId, departmentId, requesterName,
                typeId, status, saveType, keyword, submittedFrom, submittedTo, periodOverlapStart, periodOverlapEnd);
    }

    @GetMapping("/management")
    RequirementPageResponse management(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.managementPage(page, size);
    }

    @GetMapping("/{id}")
    RequirementResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/attachments")
    List<AttachmentResponse> attachments(@PathVariable Long id) {
        return attachments.list(id);
    }

    @PutMapping("/{id}/draft")
    RequirementResponse updateDraft(@PathVariable Long id, @RequestBody DraftRequirementRequest request) {
        return service.updateDraft(id, request.requesterName(), request.departmentId(), request.title(), request.typeId(),
                request.content(), request.systemId(), request.targetVersionId(), request.periodStartDate(),
                request.periodEndDate(), request.recordVersion());
    }

    @PutMapping("/{id}")
    RequirementResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequirementRequest request) {
        return service.update(id, request.requesterName(), request.departmentId(), request.title(), request.typeId(),
                request.content(), request.systemId(), request.targetVersionId(), request.periodStartDate(),
                request.periodEndDate(), request.status(), request.recordVersion());
    }

    @PatchMapping("/{id}/status")
    RequirementResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return service.updateStatus(id, request.status(), request.recordVersion());
    }

    @PatchMapping("/{id}/processing")
    RequirementResponse updateProcessing(@PathVariable Long id, @Valid @RequestBody UpdateProcessingRequest request) {
        return service.updateProcessing(id, request.status(), request.completedAt(), request.handledBy(),
                request.completionDescription(), request.recordVersion());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    AttachmentResponse upload(@PathVariable Long id,
                              @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return attachments.upload(id, file);
    }

    record UpdateStatusRequest(@NotNull RequirementStatus status, @NotNull Long recordVersion) {
    }

    record UpdateProcessingRequest(@NotNull RequirementStatus status, LocalDateTime completedAt,
                                   String handledBy, String completionDescription, @NotNull Long recordVersion) {
    }

    record UpdateRequirementRequest(@NotBlank String requesterName, @NotNull Long departmentId,
                                    @NotBlank String title, @NotNull Long typeId, @NotBlank String content,
                                    Long systemId, Long targetVersionId, LocalDate periodStartDate,
                                    LocalDate periodEndDate, RequirementStatus status, @NotNull Long recordVersion) {
    }

    record CreateRequirementRequest(@NotBlank String requesterName, @NotNull Long departmentId,
                                    @NotBlank String title, @NotNull Long typeId, @NotBlank String content,
                                    Long systemId, Long targetVersionId, LocalDate periodStartDate,
                                    LocalDate periodEndDate, NewSystemRequest newSystem) {
    }

    record DraftRequirementRequest(String requesterName, Long departmentId, String title, Long typeId,
                                   String content, Long systemId, Long targetVersionId, LocalDate periodStartDate,
                                   LocalDate periodEndDate, Long recordVersion) {
    }

    record NewSystemRequest(String name, String ownerName, List<String> collaborators) {
        NewSystemRequest {
            collaborators = collaborators == null ? List.of() : List.copyOf(collaborators);
        }
    }
}
