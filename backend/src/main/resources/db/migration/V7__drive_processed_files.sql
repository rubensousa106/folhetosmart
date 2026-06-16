-- Controlo dos ficheiros do Google Drive já processados automaticamente pelo
-- scheduler_drive.py — evita reprocessar o mesmo PDF a cada verificação.
CREATE TABLE IF NOT EXISTS drive_processed_files (
    id               SERIAL PRIMARY KEY,
    drive_file_id    VARCHAR(100) UNIQUE NOT NULL,
    filename         VARCHAR(300) NOT NULL,
    supermarket_slug VARCHAR(50),
    processed_at     TIMESTAMPTZ DEFAULT now()
);
