import * as React from "react";
import { ImageOff } from "lucide-react";
import { resolveFileUrl } from "~/lib/files";

import { ImageLightbox } from "./image-lightbox";

interface ImagePartProps {
  url: string;
  /**
   * 同一条消息里所有图片的 url 列表（含自己），用于点开大图后可以左右滑动切换。
   * 不传时默认只有自己这一张（比如工具输出里的单张图片场景）。
   */
  siblingImages?: string[];
  /** 自己在 siblingImages 里的下标，配合 siblingImages 使用 */
  siblingIndex?: number;
}

export function ImagePart({ url, siblingImages, siblingIndex }: ImagePartProps) {
  const [error, setError] = React.useState(false);
  const [loaded, setLoaded] = React.useState(false);
  const [lightboxOpen, setLightboxOpen] = React.useState(false);
  const imageUrl = resolveFileUrl(url);

  if (!url) return null;

  if (error) {
    return (
      <div className="flex items-center gap-2 rounded-md border border-destructive/50 bg-destructive/10 px-3 py-2 text-sm text-destructive">
        <ImageOff className="h-4 w-4" />
        <span>Failed to load image: {resolveFileUrl(url)}</span>
      </div>
    );
  }

  const lightboxImages = siblingImages && siblingImages.length > 0 ? siblingImages : [url];
  const lightboxInitialIndex = siblingIndex ?? 0;

  return (
    <div className="relative my-2 max-w-md">
      {!loaded && (
        <div className="flex h-48 items-center justify-center rounded-md border border-muted bg-muted/30">
          <div className="text-sm text-muted-foreground">Loading image...</div>
        </div>
      )}
      <img
        src={imageUrl}
        alt="Message attachment"
        className={`cursor-pointer rounded-md border border-muted object-contain ${loaded ? "block" : "hidden"}`}
        onClick={() => setLightboxOpen(true)}
        onLoad={() => setLoaded(true)}
        onError={() => setError(true)}
        style={{ maxHeight: "500px", width: "auto" }}
      />
      {lightboxOpen && (
        <ImageLightbox
          images={lightboxImages}
          initialIndex={lightboxInitialIndex}
          onClose={() => setLightboxOpen(false)}
        />
      )}
    </div>
  );
}
