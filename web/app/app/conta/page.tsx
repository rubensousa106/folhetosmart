"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Loader2,
  Save,
  Mail,
  KeyRound,
  Download,
  Trash2,
  LogOut,
  AlertCircle,
  CheckCircle2,
  Pencil,
} from "lucide-react";
import { users, privacy, ApiError, type UserMe } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { UserAvatar } from "@/components/UserAvatar";
import { DistritoCidadeFields } from "@/components/DistritoCidadeFields";

type Msg = { kind: "ok" | "err"; text: string } | null;
type Section = "nome" | "zona" | "email" | "password" | null;

function Feedback({ msg }: { msg: Msg }) {
  if (!msg) return null;
  const ok = msg.kind === "ok";
  return (
    <p
      className={`mt-3 flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${
        ok ? "bg-savings-bg text-brand-dark" : "bg-danger/10 text-danger"
      }`}
      role="alert"
    >
      {ok ? <CheckCircle2 className="h-4 w-4" /> : <AlertCircle className="h-4 w-4" />}
      {msg.text}
    </p>
  );
}

function humanize(e: unknown, fallback: string): string {
  return e instanceof ApiError ? e.message : fallback;
}

export default function ContaPage() {
  const { logout } = useAuth();
  const router = useRouter();

  const [me, setMe] = useState<UserMe | null>(null);
  const [loading, setLoading] = useState(true);

  // Edição "escondida" como na app: escolhe-se a secção a partir do avatar.
  const [section, setSection] = useState<Section>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  // Perfil
  const [name, setName] = useState("");
  const [district, setDistrict] = useState("");
  const [city, setCity] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileMsg, setProfileMsg] = useState<Msg>(null);

  // Email
  const [newEmail, setNewEmail] = useState("");
  const [emailPwd, setEmailPwd] = useState("");
  const [savingEmail, setSavingEmail] = useState(false);
  const [emailMsg, setEmailMsg] = useState<Msg>(null);

  // Palavra-passe
  const [curPwd, setCurPwd] = useState("");
  const [newPwd, setNewPwd] = useState("");
  const [savingPwd, setSavingPwd] = useState(false);
  const [pwdMsg, setPwdMsg] = useState<Msg>(null);

  // RGPD
  const [exporting, setExporting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [rgpdMsg, setRgpdMsg] = useState<Msg>(null);

  useEffect(() => {
    users
      .me()
      .then((u) => {
        setMe(u);
        setName(u.name ?? "");
        setDistrict(u.district ?? "");
        setCity(u.city ?? "");
        setNewEmail(u.email);
      })
      .catch(() => setProfileMsg({ kind: "err", text: "Não foi possível carregar a tua conta." }))
      .finally(() => setLoading(false));
  }, []);

  async function saveProfile(e: React.FormEvent) {
    e.preventDefault();
    setProfileMsg(null);
    // A zona é obrigatória — só se aplica quando é essa a secção a gravar (não
    // bloqueia gravar o nome só porque uma conta antiga ainda não tem zona).
    if (section === "zona" && (!district || !city)) {
      setProfileMsg({ kind: "err", text: "O distrito e a cidade são obrigatórios." });
      return;
    }
    setSavingProfile(true);
    try {
      // Reenvia os 3 campos: o PUT /me substitui-os todos (não apagar a zona).
      const u = await users.updateProfile(
        name.trim() || null,
        district.trim() || null,
        city.trim() || null,
      );
      setMe(u);
      setProfileMsg({ kind: "ok", text: "Perfil atualizado." });
    } catch (err) {
      setProfileMsg({ kind: "err", text: humanize(err, "Não foi possível guardar o perfil.") });
    } finally {
      setSavingProfile(false);
    }
  }

  async function changeEmail(e: React.FormEvent) {
    e.preventDefault();
    setSavingEmail(true);
    setEmailMsg(null);
    try {
      await users.changeEmail(emailPwd, newEmail.trim());
      setEmailPwd("");
      setMe((m) => (m ? { ...m, email: newEmail.trim() } : m));
      setEmailMsg({ kind: "ok", text: "Email atualizado." });
    } catch (err) {
      setEmailMsg({ kind: "err", text: humanize(err, "Não foi possível alterar o email.") });
    } finally {
      setSavingEmail(false);
    }
  }

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    setPwdMsg(null);
    if (newPwd.length < 8) {
      setPwdMsg({ kind: "err", text: "A nova palavra-passe tem de ter pelo menos 8 caracteres." });
      return;
    }
    setSavingPwd(true);
    try {
      await users.changePassword(curPwd, newPwd);
      setCurPwd("");
      setNewPwd("");
      setPwdMsg({ kind: "ok", text: "Palavra-passe atualizada." });
    } catch (err) {
      setPwdMsg({ kind: "err", text: humanize(err, "Não foi possível alterar a palavra-passe.") });
    } finally {
      setSavingPwd(false);
    }
  }

  async function exportData() {
    setExporting(true);
    setRgpdMsg(null);
    try {
      const json = await privacy.exportData();
      const blob = new Blob([json], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "folhetosmart-os-meus-dados.json";
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setRgpdMsg({ kind: "err", text: humanize(err, "Não foi possível exportar os dados.") });
    } finally {
      setExporting(false);
    }
  }

  async function deleteAccount() {
    setDeleting(true);
    setRgpdMsg(null);
    try {
      await privacy.deleteAccount();
      logout();
      router.replace("/entrar/");
    } catch (err) {
      setRgpdMsg({ kind: "err", text: humanize(err, "Não foi possível eliminar a conta.") });
      setDeleting(false);
    }
  }

  if (loading) {
    return (
      <div className="grid place-items-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-brand" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-ink">A minha conta</h1>

      {/* Perfil — avatar + escolher o que editar (campos escondidos, igual à app) */}
      <section className="rounded-2xl border border-outline/60 bg-white p-6">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setMenuOpen((o) => !o)}
            aria-label="Editar conta"
            className="shrink-0 rounded-full transition hover:opacity-90"
          >
            <UserAvatar seed={me?.email ?? ""} size={56} />
          </button>
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold text-ink">{name || "A minha conta"}</p>
            {me && <p className="truncate text-sm text-ink/60">{me.email}</p>}
          </div>
          <button
            type="button"
            onClick={() => setMenuOpen((o) => !o)}
            aria-label="Editar conta"
            className="rounded-lg p-2 text-ink/60 transition hover:bg-brand/10 hover:text-brand"
          >
            <Pencil className="h-5 w-5" />
          </button>
        </div>

        {menuOpen && (
          <div className="mt-3 grid gap-1 rounded-xl border border-outline/60 p-1 sm:grid-cols-4">
            {(
              [
                ["nome", "Nome"],
                ["zona", "Zona"],
                ["email", "Email"],
                ["password", "Palavra-passe"],
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                type="button"
                onClick={() => {
                  setSection(key);
                  setMenuOpen(false);
                }}
                className={`rounded-lg px-3 py-2 text-sm font-medium transition ${
                  section === key
                    ? "bg-brand/10 text-brand"
                    : "text-ink/80 hover:bg-brand/10 hover:text-brand"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        )}

        {section === null && (
          <p className="mt-4 text-sm text-ink/50">
            Toca no avatar para alterares o nome, a zona, o email ou a palavra-passe.
          </p>
        )}

        {section === "nome" && (
          <form onSubmit={saveProfile} className="mt-4 space-y-3">
            <div>
              <label htmlFor="name" className="mb-1 block text-sm font-medium text-ink">Nome</label>
              <input id="name" className="input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <button type="submit" className="btn-primary" disabled={savingProfile}>
              {savingProfile ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Guardar nome
            </button>
            <Feedback msg={profileMsg} />
          </form>
        )}

        {section === "zona" && (
          <form onSubmit={saveProfile} className="mt-4 space-y-3">
            <DistritoCidadeFields
              distrito={district}
              cidade={city}
              onDistritoChange={(v) => { setDistrict(v); setCity(""); }}
              onCidadeChange={setCity}
              required
            />
            <p className="text-xs text-ink/50">A zona define o folheto regional do Aldi.</p>
            <button type="submit" className="btn-primary" disabled={savingProfile}>
              {savingProfile ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Guardar zona
            </button>
            <Feedback msg={profileMsg} />
          </form>
        )}

        {section === "email" && (
          <form onSubmit={changeEmail} className="mt-4 space-y-3">
            <div>
              <label htmlFor="newEmail" className="mb-1 block text-sm font-medium text-ink">Novo email</label>
              <input id="newEmail" type="email" className="input" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} required />
            </div>
            <div>
              <label htmlFor="emailPwd" className="mb-1 block text-sm font-medium text-ink">Palavra-passe atual</label>
              <input id="emailPwd" type="password" autoComplete="current-password" className="input" value={emailPwd} onChange={(e) => setEmailPwd(e.target.value)} required />
            </div>
            <button type="submit" className="btn-primary" disabled={savingEmail || !emailPwd || newEmail === me?.email}>
              {savingEmail ? <Loader2 className="h-4 w-4 animate-spin" /> : <Mail className="h-4 w-4" />}
              Alterar email
            </button>
            <Feedback msg={emailMsg} />
          </form>
        )}

        {section === "password" && (
          <form onSubmit={changePassword} className="mt-4 space-y-3">
            <div>
              <label htmlFor="curPwd" className="mb-1 block text-sm font-medium text-ink">Palavra-passe atual</label>
              <input id="curPwd" type="password" autoComplete="current-password" className="input" value={curPwd} onChange={(e) => setCurPwd(e.target.value)} required />
            </div>
            <div>
              <label htmlFor="newPwd" className="mb-1 block text-sm font-medium text-ink">Nova palavra-passe (mín. 8)</label>
              <input id="newPwd" type="password" autoComplete="new-password" className="input" value={newPwd} onChange={(e) => setNewPwd(e.target.value)} required />
            </div>
            <button type="submit" className="btn-primary" disabled={savingPwd || !curPwd || newPwd.length < 8}>
              {savingPwd ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
              Alterar palavra-passe
            </button>
            <Feedback msg={pwdMsg} />
          </form>
        )}
      </section>

      {/* Privacidade e dados (RGPD) */}
      <section className="rounded-2xl border border-outline/60 bg-white p-6">
        <h2 className="font-semibold text-ink">Privacidade e dados</h2>
        <p className="mt-1 text-sm text-ink/60">
          Exporta ou elimina os teus dados (RGPD, Art. 17.º e 20.º).
        </p>
        <div className="mt-4 space-y-3">
          <button onClick={exportData} className="btn-outline w-full" disabled={exporting}>
            {exporting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
            Exportar os meus dados
          </button>

          {!confirmDelete ? (
            <button
              onClick={() => setConfirmDelete(true)}
              className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-danger px-4 py-2.5 font-semibold text-white transition hover:bg-danger/90"
            >
              <Trash2 className="h-4 w-4" /> Eliminar conta e dados
            </button>
          ) : (
            <div className="rounded-xl border border-danger/40 bg-danger/5 p-4">
              <p className="text-sm font-medium text-ink">
                Tens a certeza? Esta ação é permanente e elimina tudo.
              </p>
              <div className="mt-3 flex gap-3">
                <button onClick={deleteAccount} className="flex-1 rounded-lg bg-danger px-4 py-2 font-semibold text-white" disabled={deleting}>
                  {deleting ? "A eliminar…" : "Eliminar definitivamente"}
                </button>
                <button onClick={() => setConfirmDelete(false)} className="flex-1 rounded-lg border border-outline px-4 py-2 font-medium text-ink">
                  Cancelar
                </button>
              </div>
            </div>
          )}
          <Feedback msg={rgpdMsg} />
        </div>
      </section>

      {/* Sair */}
      <button
        onClick={() => {
          logout();
          router.replace("/entrar/");
        }}
        className="btn-outline w-full"
      >
        <LogOut className="h-4 w-4" /> Terminar sessão
      </button>
    </div>
  );
}
