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
   var version=targetVersionId==null?null:versions.findById(targetVersionId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"版本不存在"));
   if(version!=null&&(system==null||!version.system().id().equals(system.id()))) throw new IllegalArgumentException("目标版本不属于所属系统");
   var saved=requirements.save(new RequirementEntity(requesterName,department,title,type,content,system,version,RequirementPeriod.of(start,end)));
   return RequirementResponse.from(saved);
 }
 @Transactional(readOnly=true) java.util.List<RequirementResponse> list(RequirementType type){return (type==null?requirements.findAll():requirements.findByType(type)).stream().map(RequirementResponse::from).toList();}
}
