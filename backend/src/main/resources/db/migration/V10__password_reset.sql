-- Recuperação de palavra-passe por email.
-- Quando preenchido, a palavra-passe atual do utilizador é TEMPORÁRIA (gerada
-- aleatoriamente e enviada por email) e deixa de ser aceite após esta data. A app
-- obriga a definir uma nova; ao trocar a palavra-passe, o backend repõe esta coluna
-- a NULL (a palavra-passe passa a permanente).
ALTER TABLE users ADD COLUMN temp_password_expires_at timestamptz;
