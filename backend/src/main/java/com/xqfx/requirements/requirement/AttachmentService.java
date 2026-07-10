package com.xqfx.requirements.requirement;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;

@Service
class AttachmentService {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx");
    private final RequirementRepository requirements; private final AttachmentRepository attachments; private final Path root; private final long minimumFreeSpaceBytes;
    AttachmentService(RequirementRepository requirements, AttachmentRepository attachments, @Value("${app.attachments.root-directory:./uploads}") String rootDirectory, @Value("${app.attachments.minimum-free-space-bytes:0}") long minimumFreeSpaceBytes) { this.requirements=requirements;this.attachments=attachments;this.root=Path.of(rootDirectory).toAbsolutePath().normalize();this.minimumFreeSpaceBytes=minimumFreeSpaceBytes; }
    AttachmentResponse upload(Long requirementId, MultipartFile file) {
        var requirement=requirements.findByIdAndDeletedFalse(requirementId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"需求不存在"));
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
    @Transactional(readOnly=true) java.util.List<AttachmentResponse> list(Long requirementId) { requirements.findByIdAndDeletedFalse(requirementId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"需求不存在")); return attachments.findByRequirementIdAndDeletedFalseOrderByIdAsc(requirementId).stream().map(AttachmentResponse::from).toList(); }
    AttachmentFile download(Long id) { var attachment=findActive(id); var file=root.resolve(attachment.storedName()).normalize(); if(!file.startsWith(root)||!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"附件文件不存在"); return new AttachmentFile(new FileSystemResource(file),attachment.originalName(),attachment.contentType()); }
    @Transactional void delete(Long id) { var attachment=findActive(id); deleteQuietly(root.resolve(attachment.storedName()).normalize()); attachment.delete(); }
    private AttachmentEntity findActive(Long id) { return attachments.findByIdAndDeletedFalse(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"附件不存在")); }
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
