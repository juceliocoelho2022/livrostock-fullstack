-- LivroStock v0.7: cadastro de livros exclusivamente digitais, sem ISBN inventado.
-- Migration aditiva; livros e exemplares existentes permanecem intactos.
ALTER TABLE livros ALTER COLUMN isbn DROP NOT NULL;
ALTER TABLE livros ADD COLUMN somente_digital BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE livros ADD COLUMN origem_drive_id VARCHAR(128);
CREATE UNIQUE INDEX ux_livros_origem_drive_id ON livros (origem_drive_id)
    WHERE origem_drive_id IS NOT NULL;
