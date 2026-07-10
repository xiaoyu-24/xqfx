package com.xqfx.requirements.requirement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
