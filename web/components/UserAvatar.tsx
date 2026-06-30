import { avatarFor } from "@/lib/avatar";

/** Círculo colorido com o animal do utilizador (igual ao da app Android). */
export function UserAvatar({
  seed,
  size = 56,
  className = "",
}: {
  seed: string;
  size?: number;
  className?: string;
}) {
  const { animal, color } = avatarFor(seed || "folhetosmart");
  return (
    <span
      className={`inline-grid place-items-center rounded-full ${className}`}
      style={{
        width: size,
        height: size,
        backgroundColor: color,
        fontSize: size * 0.5,
        lineHeight: 1,
      }}
      role="img"
      aria-label="Avatar"
    >
      {animal}
    </span>
  );
}
