import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { supabase } from "./supabase";
import type { Album, Media, MediaFilter, StorageStats } from "./types";

export const PAGE_SIZE = 60;

function mediaQuery(f: MediaFilter) {
  let q = supabase.from("media").select("*").eq("status", "ready");
  if (f.view === "trash") q = q.not("deleted_at", "is", null);
  else q = q.is("deleted_at", null);
  if (f.view === "photos") q = q.eq("kind", "image");
  if (f.view === "videos") q = q.eq("kind", "video");
  if (f.view === "favourites") q = q.eq("is_favourite", true);
  if (f.view === "album" && f.albumId) q = q.eq("album_id", f.albumId);
  if (f.kind) q = q.eq("kind", f.kind);
  if (f.search?.trim()) q = q.ilike("filename", `%${f.search.trim().replace(/[%_]/g, "\\$&")}%`);
  if (f.from) q = q.gte("created_at", new Date(f.from + "T00:00:00").toISOString());
  if (f.to) q = q.lt("created_at", new Date(new Date(f.to + "T00:00:00").getTime() + 86400000).toISOString());
  switch (f.sort) {
    case "oldest":
      q = q.order("created_at", { ascending: true }).order("id", { ascending: true });
      break;
    case "size":
      q = q.order("size_bytes", { ascending: false }).order("id");
      break;
    case "name":
      q = q.order("filename", { ascending: true }).order("id");
      break;
    default:
      q = q.order(f.view === "trash" ? "deleted_at" : "created_at", { ascending: false }).order("id", { ascending: false });
  }
  return q;
}

export function useMediaList(f: MediaFilter) {
  return useInfiniteQuery({
    queryKey: ["media", f],
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => {
      const { data, error } = await mediaQuery(f).range(pageParam * PAGE_SIZE, pageParam * PAGE_SIZE + PAGE_SIZE - 1);
      if (error) throw error;
      return data as Media[];
    },
    getNextPageParam: (last, all) => (last.length === PAGE_SIZE ? all.length : undefined),
  });
}

export function useMediaSample(key: string, build: () => PromiseLike<{ data: unknown; error: unknown }>) {
  return useQuery({
    queryKey: ["media", "sample", key],
    queryFn: async () => {
      const { data, error } = await build();
      if (error) throw error;
      return (data ?? []) as Media[];
    },
  });
}

export function useStats() {
  return useQuery({
    queryKey: ["stats"],
    queryFn: async () => {
      const { data, error } = await supabase.rpc("get_storage_stats");
      if (error) throw error;
      const r = (data as StorageStats[])[0];
      return Object.fromEntries(Object.entries(r).map(([k, v]) => [k, Number(v)])) as unknown as StorageStats;
    },
  });
}

export function useMonthly() {
  return useQuery({
    queryKey: ["stats", "monthly"],
    queryFn: async () => {
      const { data, error } = await supabase.rpc("get_upload_stats_by_month", { p_months: 12 });
      if (error) throw error;
      return data as { month: string; kind: "image" | "video"; file_count: number; total_bytes: number }[];
    },
  });
}

export function useAlbums() {
  return useQuery({
    queryKey: ["albums"],
    queryFn: async () => {
      const { data, error } = await supabase
        .from("albums")
        .select("id, name, cover_media_id, created_at, media!media_album_id_fkey(count)")
        .order("created_at", { ascending: false });
      if (error) throw error;
      return data as unknown as Album[];
    },
  });
}

export function useInvalidate() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ["media"] });
    qc.invalidateQueries({ queryKey: ["stats"] });
    qc.invalidateQueries({ queryKey: ["albums"] });
  };
}
