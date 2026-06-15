-- Concede o papel ADMIN à conta do administrador do FolhetoSmart.
-- Idempotente: se a conta ainda não estiver registada, não afeta linhas
-- (o AdminBootstrap trata da criação/promoção via variáveis de ambiente).
UPDATE users
SET role = 'ADMIN'
WHERE email = 'rubensousa106@gmail.com';
