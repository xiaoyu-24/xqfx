package com.xqfx.requirements.requirement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;
import com.xqfx.requirements.user.UserEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;

@Service
class AttachmentService {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx");
    private final RequirementRepository requirements; private final AttachmentRepository attachments; private final Path root; private final Path previewRoot; private final long minimumFreeSpaceBytes; private final AttachmentPreviewService previewService;
    @Autowired AttachmentService(RequirementRepository requirements, AttachmentRepository attachments, @Value("${app.attachments.root-directory:./uploads}") String rootDirectory, @Value("${app.attachments.minimum-free-space-bytes:0}") long minimumFreeSpaceBytes, @Value("${app.attachments.preview-directory:./previews}") String previewDirectory, AttachmentPreviewService previewService) { this(requirements,attachments,rootDirectory,minimumFreeSpaceBytes,Path.of(previewDirectory),previewService); }
    AttachmentService(RequirementRepository requirements, AttachmentRepository attachments, String rootDirectory, long minimumFreeSpaceBytes) { this(requirements,attachments,rootDirectory,minimumFreeSpaceBytes,Path.of(rootDirectory).resolve("previews"),null); }
    private AttachmentService(RequirementRepository requirements, AttachmentRepository attachments, String rootDirectory, long minimumFreeSpaceBytes, Path previewDirectory, AttachmentPreviewService previewService) { this.requirements=requirements;this.attachments=attachments;this.root=Path.of(rootDirectory).toAbsolutePath().normalize();this.previewRoot=previewDirectory.toAbsolutePath().normalize();this.minimumFreeSpaceBytes=minimumFreeSpaceBytes;this.previewService=previewService; }
    AttachmentResponse upload(Long requirementId, UserEntity actor, MultipartFile file) {
        var requirement=requirements.findByIdAndDeletedFalse(requirementId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"需求不存在"));
        assertCanEdit(requirement, actor);
        var originalName=file.getOriginalFilename()==null?"":Path.of(file.getOriginalFilename()).getFileName().toString();
        var extension=extension(originalName);
        var contentType=file.getContentType()==null?"application/octet-stream":file.getContentType();
        if(!ALLOWED.contains(extension) || !matchesContentType(extension, contentType)) throw new IllegalArgumentException("不支持的附件格式");
        Path temporary = null;
        Path target = null;
        boolean saved = false;
        try {
            Files.createDirectories(root);
            if (Files.getFileStore(root).getUsableSpace() - minimumFreeSpaceBytes < file.getSize()) throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE,"附件目录剩余空间不足");
            temporary=Files.createTempFile(root,"upload-",".tmp");
            var checksum=copyAndChecksum(file.getInputStream(),temporary);
            if (!hasValidFileSignature(extension, temporary)) throw new IllegalArgumentException("附件内容与文件格式不匹配");
            var storedName=UUID.randomUUID()+"."+extension;
            target=root.resolve(storedName).normalize();
            if(!target.startsWith(root)) throw new IllegalArgumentException("附件路径无效");
            try {
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary,target);
            }
            temporary=null;
            var attachment = attachments.save(new AttachmentEntity(requirement,originalName,storedName,storedName,contentType,file.getSize(),checksum));
            saved = true;
            if(previewService!=null&&attachment.id()!=null&&attachment.previewStatus()==AttachmentPreviewStatus.PENDING) previewService.schedule(attachment.id());
            return AttachmentResponse.from(attachment);
        } catch (ResponseStatusException | IllegalArgumentException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"附件保存失败",exception);
        } finally {
            deleteQuietly(temporary);
            if (!saved) deleteQuietly(target);
        }
    }
    @Transactional(readOnly=true)
    java.util.List<AttachmentResponse> list(Long requirementId, UserEntity actor) {
        var requirement = requirements.findByIdAndDeletedFalse(requirementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"需求不存在"));
        assertCanView(requirement, actor);
        var activeAttachments = attachments.findByRequirementIdAndDeletedFalseOrderByIdAsc(requirementId);
        if (previewService != null) {
            activeAttachments.stream()
                    .filter(attachment -> attachment.previewStatus() == AttachmentPreviewStatus.PENDING)
                    .map(AttachmentEntity::id)
                    .filter(java.util.Objects::nonNull)
                    .forEach(previewService::schedule);
        }
        return activeAttachments.stream().map(AttachmentResponse::from).toList();
    }
    AttachmentFile download(Long id, UserEntity actor) { var attachment=findActive(id); assertCanView(attachment.requirement(), actor); var file=root.resolve(attachment.storedName()).normalize(); if(!file.startsWith(root)||!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"附件文件不存在"); return new AttachmentFile(new FileSystemResource(file),attachment.originalName(),attachment.contentType()); }
    AttachmentFile preview(Long id, UserEntity actor) { var attachment=findActive(id); assertCanView(attachment.requirement(), actor); Path file; if(attachment.previewStatus()==AttachmentPreviewStatus.DIRECT) file=root.resolve(attachment.storedName()).normalize(); else if(attachment.previewStatus()==AttachmentPreviewStatus.READY&&attachment.previewRelativePath()!=null) file=previewRoot.resolve(attachment.previewRelativePath()).normalize(); else throw new ResponseStatusException(HttpStatus.CONFLICT,"附件预览尚未生成"); var expectedRoot=attachment.previewStatus()==AttachmentPreviewStatus.DIRECT?root:previewRoot; if(!file.startsWith(expectedRoot)||!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"附件预览文件不存在"); return new AttachmentFile(new FileSystemResource(file),attachment.originalName(),attachment.previewContentType()); }
    AttachmentResponse retryPreview(Long id, UserEntity actor) { var attachment=findActive(id); assertCanEdit(attachment.requirement(), actor); if(attachment.previewStatus()!=AttachmentPreviewStatus.DIRECT){if(previewService!=null)previewService.requestRetry(id);else{attachment.requestPreviewRetry();attachments.save(attachment);}} return AttachmentResponse.from(findActive(id)); }
    @Transactional void delete(Long id, UserEntity actor) { var attachment=findActive(id); assertCanEdit(attachment.requirement(), actor); deleteQuietly(root.resolve(attachment.storedName()).normalize()); if(attachment.previewRelativePath()!=null){var preview=previewRoot.resolve(attachment.previewRelativePath()).normalize();if(preview.startsWith(previewRoot))deleteQuietly(preview);} attachment.delete(); }
    private AttachmentEntity findActive(Long id) { return attachments.findByIdAndDeletedFalse(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"附件不存在")); }
    private static void assertCanView(RequirementEntity requirement, UserEntity actor) { if (!actor.isHandler() && (requirement.requesterUser() == null || !requirement.requesterUser().id().equals(actor.id()))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该需求附件"); }
    private static void assertCanEdit(RequirementEntity requirement, UserEntity actor) {
        assertCanView(requirement, actor);
        if (!actor.isHandler() && !requirement.isDraft()
                && requirement.status() != RequirementStatus.PENDING_EVALUATION
                && requirement.status() != RequirementStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "普通用户只能编辑待评估或已确认的需求");
        }
    }
    record AttachmentFile(Resource resource,String originalName,String contentType) { }
    private static String extension(String name) { var index=name.lastIndexOf('.'); return index<0?"":name.substring(index+1).toLowerCase(); }
    private static boolean matchesContentType(String extension, String contentType) { return switch (extension) { case "jpg", "jpeg" -> contentType.equals("image/jpeg"); case "png" -> contentType.equals("image/png"); case "gif" -> contentType.equals("image/gif"); case "webp" -> contentType.equals("image/webp"); case "pdf" -> contentType.equals("application/pdf"); case "doc" -> contentType.equals("application/msword"); case "docx" -> contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"); case "xls" -> contentType.equals("application/vnd.ms-excel"); case "xlsx" -> contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); default -> false; }; }
    private static boolean hasValidFileSignature(String extension, Path file) {
        try (var input = Files.newInputStream(file)) {
            var header = input.readNBytes(12);
            return switch (extension) {
                case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
                case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
                case "gif" -> startsWith(header, "GIF87a") || startsWith(header, "GIF89a");
                case "webp" -> startsWith(header, "RIFF") && startsWith(header, 8, "WEBP");
                case "pdf" -> startsWith(header, "%PDF-");
                case "doc", "xls" -> startsWith(header, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
                case "docx" -> hasZipDirectory(file, "word/");
                case "xlsx" -> hasZipDirectory(file, "xl/");
                default -> false;
            };
        } catch (IOException exception) {
            return false;
        }
    }
    private static boolean hasZipDirectory(Path file, String directory) {
        try (var zip = new ZipFile(file.toFile())) {
            return zip.stream().anyMatch(entry -> entry.getName().startsWith(directory));
        } catch (IOException exception) {
            return false;
        }
    }
    private static boolean startsWith(byte[] value, int... signature) { if (value.length < signature.length) return false; for (int index = 0; index < signature.length; index++) if ((value[index] & 0xFF) != signature[index]) return false; return true; }
    private static boolean startsWith(byte[] value, String signature) { return startsWith(value, 0, signature); }
    private static boolean startsWith(byte[] value, int offset, String signature) { var bytes = signature.getBytes(java.nio.charset.StandardCharsets.US_ASCII); if (value.length < offset + bytes.length) return false; for (int index = 0; index < bytes.length; index++) if (value[offset + index] != bytes[index]) return false; return true; }
    private static String copyAndChecksum(InputStream input, Path temporary) throws IOException { try (input; var output=Files.newOutputStream(temporary)) { var digest=MessageDigest.getInstance("SHA-256"); var buffer=new byte[8192]; for(int read;(read=input.read(buffer))!=-1;) { output.write(buffer,0,read); digest.update(buffer,0,read); } return java.util.HexFormat.of().formatHex(digest.digest()); } catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 不可用",exception); } }
    private static void deleteQuietly(Path path) { if(path==null)return; try { Files.deleteIfExists(path); } catch(IOException ignored) { } }
}
