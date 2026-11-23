import { useEffect, useRef, useState } from "react";
import { Film, ImageIcon } from "lucide-react";
import { hasThumb, type Media } from "../lib/types";
import { mediaUrl, thumbUrl } from "../lib/urls";

const BROWSER_IMG = /^image\/(jpeg|png|webp|gif)$/;

/** Thumbnail lazy-load: hanya minta URL bila tile kelihatan. */
export function Thumb({ m }: { m: Media }) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver((e) => e[0].isIntersecting && setVisible(true), { rootMargin: "400px" });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  useEffect(() => {
    if (!visible) return;
    let alive = true;
    const load = async () => {
      let u: string | null = null;
      if (hasThumb(m)) u = await thumbUrl(m.id);
      else if (m.kind === "image" && BROWSER_IMG.test(m.mime_type) && m.size_bytes < 12 * 1024 * 1024) {
        u = await mediaUrl(m.id).catch(() => null);
      }
      if (alive) setSrc(u);
    };
    void load();
    return () => {
      alive = false;
    };
  }, [visible, m]);

  return (
    <div ref={ref} className="thumb">
      {src && !failed ? (
        <img src={src} alt={m.filename} loading="lazy" decoding="async" onError={() => setFailed(true)} draggable={false} />
      ) : (
        <div className="thumb-ph">{m.kind === "video" ? <Film size={26} /> : <ImageIcon size={26} />}</div>
      )}
    </div>
  );
}
