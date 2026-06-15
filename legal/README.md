# CHECKLIST PRÉ-SUBMISSÃO À PLAY STORE

─────────────────────────────────────

- [ ] Alojar `privacy_policy.md` em https://folhetosmart.pt/privacidade
- [ ] Alojar `terms_of_service.md` em https://folhetosmart.pt/termos
- [ ] Atualizar links na app (Onboarding + Definições) com os URLs reais
- [ ] Preencher nome legal do responsável na Política de Privacidade
- [ ] Preencher formulário Data Safety na Play Console com base em
      `android/store-listing/data_safety.md`
- [ ] Gerar keystore de produção e guardar em local seguro (não no repo)
- [ ] Testar fluxo completo de "Eliminar conta" antes de submeter
- [ ] Confirmar que HTTPS está ativo em produção (rejeita HTTP —
      `FOLHETO_REQUIRE_HTTPS=true`)
