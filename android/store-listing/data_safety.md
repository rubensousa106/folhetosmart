# Data Safety — formulário da Google Play Console

Documento de apoio ao preenchimento da secção **Data Safety** (obrigatória
desde 2022). Responder no formulário exatamente como descrito abaixo.

## Dados recolhidos e partilhados

| Dado | Recolhido | Partilhado com terceiros | Encriptado em trânsito | Finalidade |
|---|---|---|---|---|
| Email | Sim | Não | Sim (HTTPS/TLS) | Autenticação da conta e gestão de alertas |
| Distrito e cidade | Sim | Não | Sim (HTTPS/TLS) | Apenas para preços regionais do Aldi (escolhida manualmente no onboarding — sem permissão de localização) |
| Lista de compras e alertas | Sim | Não | Sim (HTTPS/TLS) | Funcionalidade principal: otimização da lista e alertas de preço |
| FCM token | Sim | Não | Sim (HTTPS/TLS) | Apenas para entrega de notificações push de alertas |

Notas para o formulário:
- Todos os dados acima são **opcionais** do ponto de vista do utilizador — a
  app funciona sem conta (comparação e lista local); os dados só são
  recolhidos quando o utilizador cria conta.
- Nenhum dado é vendido nem usado para publicidade.

## Dados NÃO recolhidos

- Localização GPS (a cidade é escolhida manualmente — sem permissão de sistema)
- Dados financeiros (cartões, pagamentos)
- Contactos ou ficheiros do dispositivo
- Fotografias, microfone, câmara
- Identificadores de publicidade

## Práticas de segurança a declarar

- **Dados encriptados em trânsito**: sim — HTTPS/TLS obrigatório em todos os
  endpoints; HSTS ativo no servidor.
- **Mecanismo de eliminação**: sim — o utilizador pode pedir a eliminação da
  conta e de todos os dados diretamente na app (Definições → "Eliminar conta
  e dados"), sem contactar suporte. A eliminação é imediata e irreversível.
- **Exportação de dados**: o utilizador pode exportar todos os seus dados em
  JSON na app (Definições → "Exportar os meus dados").
- Passwords guardadas com bcrypt (custo 12); tokens de sessão com expiração.

## Links exigidos pela Play Console

- Política de Privacidade: https://folhetosmart.pt/privacidade
- Termos de Utilização: https://folhetosmart.pt/termos
