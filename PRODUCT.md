# Product

## Register

brand

> Nota: o registo `brand` refere-se à superfície principal de design — o site público
> (`web/app/(public)`), que é o funil de aquisição. As áreas autenticadas
> (`web/app/app`, app Android) são registo `product` quando trabalhadas.

## Users

Famílias e consumidores portugueses que fazem compras semanais de supermercado
(Lidl, Continente, Pingo Doce, Intermarché, Aldi) e querem pagar menos sem perder
tempo a folhear 5 folhetos. Chegam ao site por pesquisa (SEO de folhetos/promoções)
ou pela app Android. Contexto: telemóvel ou desktop, decisão rápida, ceticismo
face a "esquemas" — precisam de confiar que é grátis e útil.

## Product Purpose

O FolhetoSmart lê os folhetos semanais dos 5 supermercados e mostra, produto a
produto, onde está o preço mais baixo, com lista de compras sincronizada entre
web e app. O site público existe para UMA coisa: **converter visitantes em contas
registadas** (o valor completo — comparação total, lista guardada — está atrás de
login). Sucesso = registos.

## Brand Personality

Confiável, prático, poupado. Voz portuguesa (pt-PT), direta e concreta — fala de
euros poupados, não de tecnologia. Emoções-alvo: confiança ("isto é sério e
grátis") e curiosidade ("quanto pouparia eu?").

## Anti-references

- **Não vender os supermercados**: as páginas de supermercado são informativas
  (SEO), nunca promocionais das cadeias. O herói é a poupança do utilizador.
- **Não voltar ao carrinho de compras** como símbolo da marca — o logótipo é
  "etiquetas a comparar" (decisão de 2026-07-01). 🛒 só como ícone funcional de lista.
- Sem estética "cupões/desconto agressivo" (vermelhos berrantes, starbursts,
  contadores de urgência falsos) — mata a confiança.
- Sem prometer datas de iOS ("brevemente", nunca "em X meses").

## Design Principles

1. **Tudo leva ao registo** — cada secção do site público termina num caminho
   para /registar/ (benefício + curiosidade como isco; amostras gratuitas do valor).
2. **"100% gratuito" é o benefício-âncora** — repetir sem vergonha; remove a
   objeção principal.
3. **Mostra o produto, não o discurso** — demonstrações concretas do comparador
   (preços, poupança em €) convencem mais do que adjetivos.
4. **Uma marca, duas plataformas** — paleta verde e identidade idênticas na web
   e na app Android (tailwind.config.ts ↔ Color.kt); promoção cruzada sempre.
5. **Honestidade visual** — dados reais quando existem, exemplos claramente
   ilustrativos quando não; validades visíveis.

## Accessibility & Inclusion

WCAG 2.1 AA como base: contraste ≥4.5:1 no texto corrente, foco visível nos
botões/inputs (já em globals.css), `prefers-reduced-motion` respeitado em todas
as animações. Público generalista — linguagem simples, sem jargão.
