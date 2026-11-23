import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { Cloud, Download } from "lucide-react";
import { formatBytes, formatDuration } from "../lib/format";
import { config } from "../lib/supabase";

interface Shared {
  filename: string;
  kind: "image" | "video";
  mimeType: string;
  sizeBytes: number;
  width: number | null;
  height: number | null;
  durationMs: number | null;
  url: string;
  thumbUrl: string | null;
  linkExpiresAt: string;
}

export function SharePublic() {
  const { token } = useParams();
  const [params] = useSearchParams();
  const [data, setData] = useState<Shared | null>(null);
  const [err, setErr] = useState("");

  useEffect(() => {
    // Pelawat tidak semestinya ada konfigurasi server; pautan membawa alamat server (?s=)
    const host = params.get("s");
    const base = host && /^[a-z0-9-]+\.supabase\.co$/i.test(host) ? `https://${host}` : config?.url;
    if (!base) return setErr("Pautan tidak lengkap.");
    fetch(`${base}/functions/v1/share-public?token=${encodeURIComponent(token ?? "")}`)
      .then(async (r) => {
        const j = await r.json();
        if (!r.ok) throw new Error(j?.error?.message ?? "Pautan tidak sah.");
        setData(j);
      })
      .catch((e) => setErr(e.message));
  }, [token, params]);

  const canShow = data && (data.kind === "video" || /^image\/(jpeg|png|webp|gif)$/.test(data.mimeType));

  return (
    <div className="share-page">
      <div className="aurora" aria-hidden />
      <header className="brand">
        <div className="logo">
          <Cloud size={20} />
        </div>
        <span>CloudPix</span>
      </header>
      {err && <div className="notice err">{err}</div>}
      {!data && !err && <div className="spinner" />}
      {data && (
        <div className="card glass share-card">
          <div className="share-media">
            {data.kind === "video" ? (
              <video src={data.url} controls playsInline poster={data.thumbUrl ?? undefined} />
            ) : canShow ? (
              <img src={data.url} alt={data.filename} />
            ) : data.thumbUrl ? (
              <img src={data.thumbUrl} alt={data.filename} />
            ) : (
              <p className="muted">Pratonton tidak tersedia.</p>
            )}
          </div>
          <div className="row between wrap">
            <div className="stack tight">
              <b>{data.filename}</b>
              <small className="muted">
                {formatBytes(data.sizeBytes)}
                {data.width ? ` · ${data.width}×${data.height}` : ""}
                {data.durationMs ? ` · ${formatDuration(data.durationMs)}` : ""} · luput{" "}
                {new Date(data.linkExpiresAt).toLocaleString("ms-MY")}
              </small>
            </div>
            <a className="btn primary" href={data.url} download={data.filename} rel="noopener">
              <Download size={16} /> Muat turun
            </a>
          </div>
        </div>
      )}
    </div>
  );
}
