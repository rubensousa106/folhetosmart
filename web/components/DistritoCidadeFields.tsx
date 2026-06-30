"use client";

import { DISTRITOS_PT, CONCELHOS_POR_DISTRITO } from "@/lib/locations";

/**
 * Campos "Distrito" e "Cidade" como dropdowns dependentes (campos FECHADOS, igual
 * à app): escolhido o distrito, a cidade lista os concelhos desse distrito. O
 * chamador deve limpar a cidade ao mudar o distrito (ver onDistritoChange).
 */
export function DistritoCidadeFields({
  distrito,
  cidade,
  onDistritoChange,
  onCidadeChange,
  required = false,
  idPrefix = "",
}: {
  distrito: string;
  cidade: string;
  onDistritoChange: (v: string) => void;
  onCidadeChange: (v: string) => void;
  required?: boolean;
  idPrefix?: string;
}) {
  const cidades = distrito ? CONCELHOS_POR_DISTRITO[distrito] ?? [] : [];
  const distId = `${idPrefix}distrito`;
  const cidId = `${idPrefix}cidade`;

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <div>
        <label htmlFor={distId} className="mb-1 block text-sm font-medium text-ink">
          Distrito{required && " *"}
        </label>
        <select
          id={distId}
          className="input"
          value={distrito}
          required={required}
          onChange={(e) => onDistritoChange(e.target.value)}
        >
          <option value="" disabled>
            Escolhe o distrito
          </option>
          {DISTRITOS_PT.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor={cidId} className="mb-1 block text-sm font-medium text-ink">
          Cidade{required && " *"}
        </label>
        <select
          id={cidId}
          className="input"
          value={cidade}
          required={required}
          disabled={cidades.length === 0}
          onChange={(e) => onCidadeChange(e.target.value)}
        >
          <option value="" disabled>
            {distrito ? "Escolhe a cidade" : "Escolhe primeiro o distrito"}
          </option>
          {cidades.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
