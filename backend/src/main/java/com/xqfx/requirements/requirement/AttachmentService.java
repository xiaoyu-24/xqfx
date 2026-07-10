package com.xqfx.requirements.requirement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;

@Service
class AttachmentService {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx");
    private final RequirementRepository requirements; private final AttachmentRepository attachments; private final Path root;
    AttachmentService(RequirementRepository requirements, AttachmentRepository attachments, @Value("${app.attachments.root-directory:./uploads}") String rootDirectory) { this.requirements=requirements;this.attachments=attachments;this.root=Path.of(rootDirectory).toAbsolutePath().normalize(); }
    AttachmentResponse upload(Long requirementId, MultipartFile file) { var requirement=requirements.findByIdAndDeletedFalse(requirementId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"需求不存在")); var originalName=file.getOriginalFilename()==null?"":Path.of(file.getOriginalFilename()).getFileName().toString(); var extension=extension(originalName); if(!ALLOWED.contains(extension)) throw new IllegalArgumentException("不支持的附件格式"); try { Files.createDirectories(root); var storedName=UUID.randomUUID()+"."+extension; var target=root.resolve(storedName).normalize(); if(!target.startsWith(root)) throw new IllegalArgumentException("附件路径无效"); file.transferTo(target); return AttachmentResponse.from(attachments.save(new AttachmentEntity(requirement,originalName,storedName,storedName,file.getContentType()==null?"application/octet-stream":file.getContentType(),file.getSize()))); } catch(IOException exception) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"附件保存失败",exception); } }
    AttachmentFile download(Long id) { var attachment=attachments.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"附件不存在")); var file=root.resolve(attachment.storedName()).normalize(); if(!file.startsWith(root)||!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"附件文件不存在"); return new AttachmentFile(new FileSystemResource(file),attachment.originalName(),attachment.contentType()); }
    record AttachmentFile(Resource resource,String originalName,String contentType) { }
    private static String extension(String name) { var index=name.lastIndexOf('.'); return index<0?"":name.substring(index+1).toLowerCase(); }
}
