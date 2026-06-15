"""Prompts de matching semântico de produtos.

`SYSTEM_PROMPT` + `build_batch_user_message()` são o que o pipeline usa em
produção (processa até 20 produtos por chamada). `MATCHING_PROMPT` é a versão
de produto único, mantida para referência/depuração.
"""
from __future__ import annotations

import json
from typing import Sequence

from .models import CandidateProduct, RawProduct


# --- Versão de produto único (referência / debug) --------------------------
MATCHING_PROMPT = """
És um assistente especializado em normalização de produtos de supermercado.

Produto do folheto: "{raw_name}" — Preço: {price}€ — Supermercado: {supermarket}

Produtos canónicos existentes na base de dados (candidatos):
{candidates_json}

Tarefas:
1. Extrai do produto do folheto: marca, nome_base, gramagem, variante/sabor
2. Verifica se corresponde a algum candidato (mesmo produto, embalagem similar)
3. Responde APENAS em JSON válido:
{{
  "extracted": {{
    "brand": "Doritos",
    "base_name": "Doritos",
    "weight": "150g",
    "variant": "Chilli"
  }},
  "match": {{
    "product_id": "uuid-do-candidato-ou-null",
    "canonical_name": "doritos_150g",
    "display_name": "Doritos 150g",
    "confidence": 0.92,
    "reasoning": "Mesma marca, gramagem e gama de produto."
  }}
}}

Notas:
- Variantes de sabor do mesmo produto (Chilli vs Spicy) = mesmo produto canónico
- Gramagens diferentes (150g vs 200g) = produtos diferentes
- Confiança >= 0.85 apenas se tiveres certeza absoluta
"""


# --- Versão de batch (produção) --------------------------------------------
SYSTEM_PROMPT = """\
És um assistente especializado em normalização de produtos de supermercados \
portugueses (Pingo Doce, Continente, Lidl, Intermarché, Aldi).

O teu trabalho é decidir se um produto que saiu de um folheto corresponde a um \
"produto canónico" já existente na base de dados, para que preços de \
supermercados diferentes possam ser comparados lado a lado.

Regras de normalização:
- Variantes de sabor do MESMO produto e gramagem são o MESMO produto canónico.
  Ex.: "Doritos Chilli 150g" e "Doritos Tortilla Spicy 150g" -> mesmo produto.
- Gramagens/volumes DIFERENTES são produtos DIFERENTES.
  Ex.: "Iogurte 150g" e "Iogurte 200g" -> produtos diferentes.
- Marcas diferentes são produtos diferentes, mesmo que o género seja igual.
- Marca branca (Continente, Pingo Doce, Auchan, etc.) NÃO é igual a marca de \
fabricante, ainda que o produto seja semelhante.
- `canonical_name`: minúsculas, sem acentos, com underscores, incluindo a \
gramagem. Ex.: "doritos_150g", "leite_meio_gordo_mimosa_1l".
- `display_name`: nome legível para o utilizador. Ex.: "Doritos 150g".

Calibração da confiança (`confidence`, entre 0 e 1):
- >= 0.85 -> só quando tens a certreza de que é o mesmo SKU comparável.
- 0.60 a 0.84 -> provável, mas com dúvida (será revisto por uma pessoa).
- < 0.60 -> não corresponde a nenhum candidato; é um produto novo.
Quando não há candidato adequado, devolve "product_id": null (e propõe um \
canonical_name/display_name novos).

Responde SEMPRE e APENAS com JSON válido, sem texto antes ou depois.
"""


def build_batch_user_message(
    raw_products: Sequence[RawProduct],
    candidates: Sequence[CandidateProduct],
) -> str:
    """Constrói a mensagem de utilizador para um batch de produtos.

    Pede ao Claude um objeto `{"results": [...]}` com um item por produto,
    correlacionado pelo campo `index`.
    """
    candidates_json = json.dumps(
        [c.to_prompt_dict() for c in candidates],
        ensure_ascii=False,
        indent=2,
    )

    lines = []
    for i, rp in enumerate(raw_products):
        price = f"{rp.price:.2f}€" if rp.price is not None else "?"
        lines.append(f'  {i}: "{rp.raw_name}" — {price} — {rp.supermarket}')
    products_block = "\n".join(lines)

    schema_example = """\
{
  "results": [
    {
      "index": 0,
      "extracted": {"brand": "Doritos", "base_name": "Doritos",
                    "weight": "150g", "variant": "Chilli"},
      "match": {
        "product_id": "uuid-do-candidato-ou-null",
        "canonical_name": "doritos_150g",
        "display_name": "Doritos 150g",
        "confidence": 0.92,
        "reasoning": "Mesma marca e gramagem; variante de sabor diferente."
      }
    }
  ]
}"""

    return (
        "Produtos canónicos existentes (candidatos):\n"
        f"{candidates_json}\n\n"
        "Produtos do folheto a normalizar (índice: nome — preço — supermercado):\n"
        f"{products_block}\n\n"
        "Para CADA produto do folheto (mantém o mesmo `index`):\n"
        "1. Extrai marca, nome_base, gramagem e variante/sabor.\n"
        "2. Decide se corresponde a um candidato (preenche `product_id`) ou se "
        "é novo (`product_id`: null).\n"
        "3. Atribui um `confidence` calibrado.\n\n"
        "Responde APENAS com JSON exatamente nesta forma:\n"
        f"{schema_example}"
    )
