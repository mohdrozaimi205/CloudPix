export interface Media {
  id: string;
  user_id?: string;
  album_id: string | null;
  kind: "image" | "video";
  status: "pending" | "ready" | "failed";
  filename: string;
  mime_type: string;
  size_bytes: number;
  sha256: string | null;
  thumb_key?: string | null;
  has_thumb?: boolean;
  width: number | null;
  height: number | null;
  duration_ms: number | null;
  is_favourite: boolean;
  taken_at: string | null;
  last_accessed_at: string | null;
  deleted_at: string | null;
  source: string;
  created_at: string;
  updated_at: string;
}

export interface Album {
  id: string;
  name: string;
  cover_media_id: string | null;
  created_at: string;
  media?: { count: number }[];
}

export interface StorageStats {
  quota_bytes: number;
  used_bytes: number;
  available_bytes: number;
  used_percent: number;
  photo_count: number;
  video_count: number;
  photo_bytes: number;
  video_bytes: number;
  trash_count: number;
  trash_bytes: number;
  favourite_count: number;
}

export interface StorageInfo {
  quotaBytes: number;
  usedBytes: number;
  availableBytes: number;
  usedPercent: number;
  warningLevel?: number;
}

export type View = "all" | "photos" | "videos" | "favourites" | "trash" | "album";
export type Sort = "newest" | "oldest" | "size" | "name";

export interface MediaFilter {
  view: View;
  albumId?: string;
  search?: string;
  kind?: "" | "image" | "video";
  from?: string;
  to?: string;
  sort: Sort;
}

export const hasThumb = (m: Media) => Boolean(m.thumb_key ?? m.has_thumb);
