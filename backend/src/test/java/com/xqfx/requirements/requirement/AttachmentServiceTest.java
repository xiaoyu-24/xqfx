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

        var response = service.upload(1L, new MockMultipartFile("file", "内容.pdf", "application/pdf", new byte[] {1, 2, 3}));

        assertEquals("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81", response.checksum());
    }

    @Test
    void rejectsUploadWhenRemainingDiskSpaceIsInsufficient() {
        var requirements = mock(RequirementRepository.class);
        var attachments = mock(AttachmentRepository.class);
        when(requirements.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(mock(RequirementEntity.class)));
        var service = new AttachmentService(requirements, attachments, attachmentsRoot.toString(), Long.MAX_VALUE);

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.upload(1L, new MockMultipartFile("file", "内容.pdf", "application/pdf", new byte[] {1})));

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
                () -> service.upload(1L, new MockMultipartFile("file", "内容.pdf", "application/pdf", new byte[] {1, 2, 3})));

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
