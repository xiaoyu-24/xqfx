package com.xqfx.requirements.requirement;
import com.xqfx.requirements.system.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;

@Service
class RequirementService {
 private final RequirementRepository requirements; private final SystemRepository systems; private final SystemVersionRepository versions;
 RequirementService(RequirementRepository requirements,SystemRepository systems,SystemVersionRepository versions){this.requirements=requirements;this.systems=systems;this.versions=versions;}
 @Transactional RequirementResponse create(String requesterName,String department,String title,RequirementType type,String content,Long systemId,Long targetVersionId,LocalDate start,LocalDate end){
   var system=systemId==null?null:systems.findById(systemId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"系统不存在"));
   if(system!=null&&!system.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT,"系统已停用，不能新建需求");
   var version=targetVersionId==null?null:versions.findById(targetVersionId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"版本不存在"));
   if(version!=null&&!version.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT,"版本已停用，不能作为目标版本");
   if(version!=null&&(system==null||!version.system().id().equals(system.id()))) throw new IllegalArgumentException("目标版本不属于所属系统");
   var saved=requirements.save(new RequirementEntity(requesterName,department,title,type,content,system,version,RequirementPeriod.of(start,end)));
   return RequirementResponse.from(saved);
 }
 @Transactional RequirementResponse createDraft(String requesterName,String department,String title,RequirementType type,String content,Long systemId,Long targetVersionId,LocalDate start,LocalDate end){var system=systemId==null?null:systems.findById(systemId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"系统不存在"));var version=targetVersionId==null?null:versions.findById(targetVersionId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"版本不存在"));if(version!=null&&(system==null||!version.system().id().equals(system.id())))throw new IllegalArgumentException("目标版本不属于所属系统");return RequirementResponse.from(requirements.save(RequirementEntity.draft(requesterName,department,title,type,content,system,version,RequirementPeriod.of(start,end))));}
 @Transactional(readOnly=true) java.util.List<RequirementResponse> list(RequirementType type,Long systemId,RequirementSaveType saveType){var items=saveType!=null?requirements.findBySaveTypeAndDeletedFalse(saveType):(systemId!=null?requirements.findBySystemIdAndDeletedFalse(systemId):(type!=null?requirements.findByTypeAndDeletedFalse(type):requirements.findAllByDeletedFalse()));return items.stream().map(RequirementResponse::from).toList();}
 @Transactional(readOnly=true) RequirementResponse get(Long id){return RequirementResponse.from(findActive(id));}
 @Transactional RequirementResponse updateDraft(Long id,String requesterName,String department,String title,RequirementType type,String content,Long systemId,Long targetVersionId,LocalDate start,LocalDate end){var requirement=findActive(id);if(!requirement.isDraft())throw new IllegalArgumentException("只有草稿可以暂存更新");var system=systemId==null?null:systems.findById(systemId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"系统不存在"));var version=targetVersionId==null?null:versions.findById(targetVersionId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"版本不存在"));if(version!=null&&(system==null||!version.system().id().equals(system.id())))throw new IllegalArgumentException("目标版本不属于所属系统");requirement.updateDraft(requesterName,department,title,type,content,system,version,RequirementPeriod.of(start,end));return RequirementResponse.from(requirement);}
 @Transactional RequirementResponse update(Long id,String requesterName,String department,String title,RequirementType type,String content,Long systemId,Long targetVersionId,LocalDate start,LocalDate end,RequirementStatus status){var requirement=findActive(id);var system=systemId==null?null:systems.findById(systemId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"系统不存在"));var version=targetVersionId==null?null:versions.findById(targetVersionId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"版本不存在"));if(version!=null&&(system==null||!version.system().id().equals(system.id())))throw new IllegalArgumentException("目标版本不属于所属系统");requirement.update(requesterName,department,title,type,content,system,version,RequirementPeriod.of(start,end),status);return RequirementResponse.from(requirement);}
 @Transactional RequirementResponse updateStatus(Long id,RequirementStatus status){var requirement=findActive(id);requirement.updateStatus(status);return RequirementResponse.from(requirement);}
 @Transactional void delete(Long id){findActive(id).delete();}
 private RequirementEntity findActive(Long id){return requirements.findByIdAndDeletedFalse(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"需求不存在"));}
}
