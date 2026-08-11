INSERT INTO users (
    username,
    password_hash,
    display_name,
    department_id,
    role,
    disabled,
    must_change_password,
    failed_login_attempts,
    locked_until,
    record_version,
    created_at,
    updated_at
)
SELECT
    imported.username,
    imported.password_hash,
    imported.display_name,
    NULL,
    'ADMIN',
    FALSE,
    TRUE,
    0,
    NULL,
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM (
    SELECT
        'admin' AS username,
        '$2a$10$sZE4DU54mYc4RT/JS40RJueBUIthOTHlwC5Z300E/uznFFT7vN8li' AS password_hash,
        '管理员' AS display_name
) AS imported
LEFT JOIN users AS existing ON existing.username = imported.username
WHERE existing.id IS NULL;
