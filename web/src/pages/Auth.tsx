import { useState } from "react";
import { Cloud, Server } from "lucide-react";
import { appBaseUrl, clearServerConfig, rememberMe, saveServerConfig, setRememberMe, supabase } from "../lib/supabase";

type Mode = "login" | "register" | "forgot";

export function AuthPage() {
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [remember, setRemember] = useState(rememberMe());
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ tone: "ok" | "err"; text: string } | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      if (mode === "login") {
        setRememberMe(remember);
        const { error } = await supabase.auth.signInWithPassword({ email, password });
        if (error) throw error;
      } else if (mode === "register") {
        if (password.length < 8) throw new Error("Kata laluan mesti sekurang-kurangnya 8 aksara.");
        const { data, error } = await supabase.auth.signUp({
          email,
          password,
          options: { emailRedirectTo: appBaseUrl(), data: { display_name: name } },
        });
        if (error) throw error;
        if (!data.session) setMsg({ tone: "ok", text: "Akaun dicipta. Semak e-mel anda dan klik pautan pengesahan." });
      } else {
        const { error } = await supabase.auth.resetPasswordForEmail(email, { redirectTo: appBaseUrl() });
        if (error) throw error;
        setMsg({ tone: "ok", text: "Jika e-mel ini berdaftar, pautan tetapan semula telah dihantar." });
      }
    } catch (err) {
      const t = (err as Error).message;
      setMsg({
        tone: "err",
        text: /Invalid login/i.test(t) ? "E-mel atau kata laluan salah." : /not confirmed/i.test(t) ? "E-mel belum disahkan. Semak peti masuk anda." : t,
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth">
      <div className="aurora" aria-hidden />
      <div className="auth-card glass">
        <div className="brand big">
          <div className="logo">
            <Cloud size={26} />
          </div>
          <span>CloudPix</span>
        </div>
        <p className="muted center">Storan awan peribadi 1TB untuk foto & video anda.</p>
        <div className="seg">
          <button className={mode === "login" ? "on" : ""} onClick={() => setMode("login")}>
            Log masuk
          </button>
          <button className={mode === "register" ? "on" : ""} onClick={() => setMode("register")}>
            Daftar
          </button>
        </div>
        <form className="stack" onSubmit={submit}>
          {mode === "register" && (
            <label className="field">
              <span>Nama</span>
              <input value={name} onChange={(e) => setName(e.target.value)} maxLength={80} autoComplete="name" />
            </label>
          )}
          <label className="field">
            <span>E-mel</span>
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
          </label>
          {mode !== "forgot" && (
            <label className="field">
              <span>Kata laluan</span>
              <input
                type="password"
                required
                minLength={mode === "register" ? 8 : undefined}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={mode === "login" ? "current-password" : "new-password"}
              />
            </label>
          )}
          {mode === "login" && (
            <div className="row between">
              <label className="check">
                <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} /> Ingat saya
              </label>
              <button type="button" className="link" onClick={() => setMode("forgot")}>
                Lupa kata laluan?
              </button>
            </div>
          )}
          {msg && <div className={`notice ${msg.tone}`}>{msg.text}</div>}
          <button className="btn primary block" disabled={busy}>
            {busy ? "Sila tunggu…" : mode === "login" ? "Log masuk" : mode === "register" ? "Cipta akaun" : "Hantar pautan"}
          </button>
          {mode === "forgot" && (
            <button type="button" className="link center" onClick={() => setMode("login")}>
              Kembali ke log masuk
            </button>
          )}
        </form>
        <button
          className="link center small muted"
          onClick={() => {
            clearServerConfig();
            location.reload();
          }}
        >
          <Server size={13} /> Tukar server
        </button>
      </div>
    </div>
  );
}

export function SetupPage() {
  const [url, setUrl] = useState("");
  const [key, setKey] = useState("");
  const [err, setErr] = useState("");
  return (
    <div className="auth">
      <div className="aurora" aria-hidden />
      <form
        className="auth-card glass stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (!/^https:\/\/[a-z0-9-]+\.supabase\.co\/?$/i.test(url.trim()) && !/^https:\/\//.test(url.trim())) {
            return setErr("URL mesti bermula dengan https://");
          }
          saveServerConfig({ url, anonKey: key });
          location.reload();
        }}
      >
        <div className="brand big">
          <div className="logo">
            <Server size={24} />
          </div>
          <span>Sambung Server</span>
        </div>
        <p className="muted small">
          Masukkan maklumat projek Supabase anda (Supabase → Project Settings → API Keys). Kedua-duanya selamat untuk diletak
          di sini — kunci rahsia tidak diperlukan.
        </p>
        <label className="field">
          <span>Project URL</span>
          <input required placeholder="https://xxxx.supabase.co" value={url} onChange={(e) => setUrl(e.target.value)} />
        </label>
        <label className="field">
          <span>Publishable / anon key</span>
          <input required placeholder="sb_publishable_… atau eyJ…" value={key} onChange={(e) => setKey(e.target.value)} />
        </label>
        {err && <div className="notice err">{err}</div>}
        <button className="btn primary block">Sambung</button>
      </form>
    </div>
  );
}

export function NewPasswordPage({ onDone }: { onDone: () => void }) {
  const [pw, setPw] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  return (
    <div className="auth">
      <div className="aurora" aria-hidden />
      <form
        className="auth-card glass stack"
        onSubmit={async (e) => {
          e.preventDefault();
          if (pw.length < 8) return setErr("Minimum 8 aksara.");
          setBusy(true);
          const { error } = await supabase.auth.updateUser({ password: pw });
          setBusy(false);
          if (error) return setErr(error.message);
          onDone();
        }}
      >
        <h2>Tetapkan kata laluan baru</h2>
        <label className="field">
          <span>Kata laluan baru</span>
          <input type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" />
        </label>
        {err && <div className="notice err">{err}</div>}
        <button className="btn primary block" disabled={busy}>
          Simpan
        </button>
      </form>
    </div>
  );
}
