package com.xqfx.requirements.requirement;

record AttachmentResponse(Long id, String originalName, String contentType, long sizeBytes) {
    static AttachmentResponse from(AttachmentEntity attachment) { return new AttachmentResponse(attachment.id(), attachment.originalName(), attachment.contentType(), attachment.sizeBytes()); }
}
