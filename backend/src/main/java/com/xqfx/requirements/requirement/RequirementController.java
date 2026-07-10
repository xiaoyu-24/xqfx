package com.xqfx.requirements.requirement;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*; import java.time.LocalDate;
@RestController @RequestMapping("/api/requirements") class RequirementController {
 private final RequirementService service; RequirementController(RequirementService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) RequirementResponse create(@Valid @RequestBody CreateRequirementRequest r){return service.create(r.requesterName(),r.department(),r.title(),r.type(),r.content(),r.systemId(),r.targetVersionId(),r.periodStartDate(),r.periodEndDate());}
 @GetMapping java.util.List<RequirementResponse> list(@RequestParam(required=false) RequirementType type,@RequestParam(required=false) Long systemId){return service.list(type,systemId);}
 @PatchMapping("/{id}/status") RequirementResponse updateStatus(@PathVariable Long id,@Valid @RequestBody UpdateStatusRequest request){return service.updateStatus(id,request.status());}
 record UpdateStatusRequest(@jakarta.validation.constraints.NotNull RequirementStatus status){}
 record CreateRequirementRequest(@NotBlank String requesterName,@NotBlank String department,@NotBlank String title,RequirementType type,@NotBlank String content,Long systemId,Long targetVersionId,LocalDate periodStartDate,LocalDate periodEndDate){}
}
