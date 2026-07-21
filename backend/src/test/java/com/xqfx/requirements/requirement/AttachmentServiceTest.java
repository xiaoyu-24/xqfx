package com.xqfx.requirements.requirement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.lang.reflect.Proxy;
import org.springframework.core.task.TaskExecutor;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AttachmentServiceTest {

    @TempDir
    Path attachmentsRoot;

    @Test
    void storesSha256ChecksumForUploadedFile() {
        var requirements = mock(RequirementRepository.class);
        var attachments = mock(AttachmentRepository.class);
        when(requirements.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(mock(RequirementEntity.class)));
        when(attachments.save(any(AttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AttachmentService(requirements, attachments, attachmentsRoot.toString(), 0);

        var content = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var response = service.upload(1L, new MockMultipartFile("file", "内容.pdf", "application/pdf", content));

        assertEquals("0716f9264c9fe19f5d7455276107f3ddcc1d3497f63d60689a73558ae8a1bf5e", response.checksum());
    }

    @Test
    void exposesPreviewMetadataForUploadedAttachments() {
        var requirements = mock(RequirementRepository.class);
        var attachments = mock(AttachmentRepository.class);
        when(requirements.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(mock(RequirementEntity.class)));
        when(attachments.save(any(AttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AttachmentService(requirements, attachments, attachmentsRoot.toString(), 0);

        var response = service.upload(1L, new MockMultipartFile("file", "内容.pdf", "application/pdf", "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        Set<String> fields = Arrays.stream(response.getClass().getRecordComponents()).map(component -> component.getName()).collect(Collectors.toSet());

        assertTrue(fields.containsAll(Set.of("previewStatus", "previewAvailable", "previewContentType", "previewErrorMessage")));
    }

    @Test
    void exposesOfficePreviewRetryOperation() {
        assertDoesNotThrow(() -> AttachmentService.class.getDeclaredMethod("retryPreview", Long.class));
    }

    @Test
    void schedulesPendingHistoricalOfficePreviewWhenAttachmentsAreListed() {
        var requirements = mock(RequirementRepository.class);
        var attachments = mock(AttachmentRepository.class);
        var previewService = mock(AttachmentPreviewService.class);
        var attachment = mock(AttachmentEntity.class);
        when(requirements.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(mock(RequirementEntity.class)));
        when(attachments.findByRequirementIdAndDeletedFalseOrderByIdAsc(1L)).thenReturn(java.util.List.of(attachment));
        when(attachment.id()).thenReturn(5L);
        when(attachment.previewStatus()).thenReturn(AttachmentPreviewStatus.PENDING);
        var service = new AttachmentService(requirements, attachments, attachmentsRoot.toString(), 0,
                attachmentsRoot.resolve("previews").toString(), previewService);

        service.list(1L);

        verify(previewService).schedule(5L);
    }

    @Test
    void movesOfficeAttachmentThroughPreviewConversionStates() throws Exception {
        var attachment = new AttachmentEntity(mock(RequirementEntity.class), "说明.docx", "stored.docx", "stored.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 10, "checksum");

        assertEquals(AttachmentPreviewStatus.PENDING, attachment.previewStatus());
        var begin = AttachmentEntity.class.getDeclaredMethod("beginPreviewConversion");
        var complete = AttachmentEntity.class.getDeclaredMethod("completePreviewConversion", String.class);
        begin.invoke(attachment);
        assertEquals(AttachmentPreviewStatus.CONVERTING, attachment.previewStatus());
        complete.invoke(attachment, "checksum.pdf");

        assertEquals(AttachmentPreviewStatus.READY, attachment.previewStatus());
        assertEquals("application/pdf", attachment.previewContentType());
    }

    @Test
    void providesDedicatedOfficePreviewConversionService() {
        assertDoesNotThrow(() -> Class.forName("com.xqfx.requirements.requirement.AttachmentPreviewService"));
    }

    @Test
    void convertsOfficeAttachmentToCachedPdf() throws Exception {
        var attachments = mock(AttachmentRepository.class);
        var attachment = new AttachmentEntity(mock(RequirementEntity.class), "说明.docx", "stored.docx", "stored.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 10, "checksum");
        when(attachments.findByIdAndDeletedFalse(5L)).thenReturn(java.util.Optional.of(attachment));
        Files.writeString(attachmentsRoot.resolve("stored.docx"), "office");
        var previewRoot = attachmentsRoot.resolve("previews");
        var generatorType = Class.forName("com.xqfx.requirements.requirement.OfficePreviewGenerator");
        var generator = Proxy.newProxyInstance(generatorType.getClassLoader(), new Class<?>[]{generatorType}, (proxy, method, arguments) -> {
            Files.writeString((Path) arguments[1], "%PDF-1.7\n");
            return null;
        });
        TaskExecutor directExecutor = Runnable::run;
        var constructor = AttachmentPreviewService.class.getDeclaredConstructor(AttachmentRepository.class, Path.class, Path.class, generatorType, TaskExecutor.class);
        var service = constructor.newInstance(attachments, attachmentsRoot, previewRoot, generator, directExecutor);

        AttachmentPreviewService.class.getDeclaredMethod("schedule", Long.class).invoke(service, 5L);

        assertEquals(AttachmentPreviewStatus.READY, attachment.previewStatus());
        assertTrue(Files.isRegularFile(previewRoot.resolve("checksum.pdf")));
        verify(attachments, org.mockito.Mockito.atLeastOnce()).save(attachment);
    }

    @Test
    void queuesOnlyOnePreviewConversionPerAttachment() {
        var queued = new ArrayList<Runnable>();
        TaskExecutor capturingExecutor = queued::add;
        OfficePreviewGenerator generator = (source, target) -> { };
        var service = new AttachmentPreviewService(mock(AttachmentRepository.class), attachmentsRoot,
                attachmentsRoot.resolve("previews"), generator, capturingExecutor);

        service.schedule(5L);
        service.schedule(5L);

        assertEquals(1, queued.size());
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchItsDeclaredFormat() {
        var requirements = mock(RequirementRepository.class);
        var attachments = mock(AttachmentRepository.class);
        when(requirements.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(mock(RequirementEntity.class)));
        when(attachments.save(any(AttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AttachmentService(requirements, attachments, attachmentsRoot.toString(), 0);

        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.upload(1L, new MockMultipartFile("file", "伪造.png", "image/png", new byte[] {1, 2, 3})));

        assertEquals("附件内容与文件格式不匹配", exception.getMessage());
    }

    @Test
    void rejectsUploadWhenRemainingDiskSpaceIsInsufficient() {
        var requirements = mock(RequirementRepository.class);
        var attachments = mock(AttachmentRepository.class);
        when(requirements.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(mock(RequirementEntity.class)));
        var service = new AttachmentService(requirements, attachments, attachmentsRoot.toString(), Long.MAX_VALUE);

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.upload(1L, new MockMultipartFile("file", "内容.pdf", "application/pdf", "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))));

        assertEquals(507, exception.getStatusCode().value());
    }

    @Test
    void removesStoredFileWhenAttachmentRecordCannotBeSaved() throws Exception {
        var requirements = mock(RequirementRepository.class);
        var attachments = mock(AttachmentRepository.class);
        when(requirements.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(mock(RequirementEntity.class)));
        when(attachments.save(any(AttachmentEntity.class))).thenThrow(new DataIntegrityViolationException("保存失败"));
        var service = new AttachmentService(requirements, attachments, attachmentsRoot.toString(), 0);

        assertThrows(DataIntegrityViolationException.class,
                () -> service.upload(1L, new MockMultipartFile("file", "内容.pdf", "application/pdf", "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))));

        try (Stream<Path> files = Files.list(attachmentsRoot)) {
            assertTrue(files.findAny().isEmpty());
        }
    }

    @Test
    void removesStoredFileWhenAttachmentIsDeleted() throws Exception {
        var requirements = mock(RequirementRepository.class);
        var attachments = mock(AttachmentRepository.class);
        var attachment = mock(AttachmentEntity.class);
        when(attachments.findByIdAndDeletedFalse(5L)).thenReturn(java.util.Optional.of(attachment));
        when(attachment.storedName()).thenReturn("delete-me.pdf");
        Files.writeString(attachmentsRoot.resolve("delete-me.pdf"), "attachment");
        var service = new AttachmentService(requirements, attachments, attachmentsRoot.toString(), 0);

        service.delete(5L);

        assertTrue(Files.notExists(attachmentsRoot.resolve("delete-me.pdf")));
        verify(attachment).delete();
    }

    @Test
    void removesPreviewCacheWhenAttachmentIsDeleted() throws Exception {
        var requirements = mock(RequirementRepository.class);
        var attachments = mock(AttachmentRepository.class);
        var attachment = mock(AttachmentEntity.class);
        when(attachments.findByIdAndDeletedFalse(5L)).thenReturn(java.util.Optional.of(attachment));
        when(attachment.storedName()).thenReturn("delete-me.docx");
        when(attachment.previewRelativePath()).thenReturn("preview.pdf");
        Files.writeString(attachmentsRoot.resolve("delete-me.docx"), "attachment");
        var previewRoot = attachmentsRoot.resolve("previews");
        Files.createDirectories(previewRoot);
        Files.writeString(previewRoot.resolve("preview.pdf"), "%PDF-1.7\n");
        var service = new AttachmentService(requirements, attachments, attachmentsRoot.toString(), 0);

        service.delete(5L);

        assertTrue(Files.notExists(previewRoot.resolve("preview.pdf")));
    }
}
