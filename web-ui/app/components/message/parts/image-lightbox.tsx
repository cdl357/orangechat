import * as React from "react";
import { ChevronLeft, ChevronRight, X } from "lucide-react";

import { resolveFileUrl } from "~/lib/files";
import { cn } from "~/lib/utils";

interface ImageLightboxProps {
  images: string[];
  initialIndex: number;
  onClose: () => void;
}

/**
 * 全屏图片查看器，支持多张图片左右切换（点击箭头 / 键盘方向键 / 触摸滑动）。
 * 用来对齐 Android 端 ImagePreviewDialog 的多图浏览体验——之前 web 端只能单张查看，
 * 一条消息发了多张图时无法像手机 APP 一样左右滑动看其它图。
 */
export function ImageLightbox({ images, initialIndex, onClose }: ImageLightboxProps) {
  const [index, setIndex] = React.useState(initialIndex);
  const touchStartX = React.useRef<number | null>(null);

  const goPrev = React.useCallback(() => {
    setIndex((i) => (i - 1 + images.length) % images.length);
  }, [images.length]);

  const goNext = React.useCallback(() => {
    setIndex((i) => (i + 1) % images.length);
  }, [images.length]);

  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
      if (event.key === "ArrowLeft" && images.length > 1) goPrev();
      if (event.key === "ArrowRight" && images.length > 1) goNext();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [goNext, goPrev, images.length, onClose]);

  React.useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  const handleTouchStart = (event: React.TouchEvent) => {
    touchStartX.current = event.touches[0]?.clientX ?? null;
  };

  const handleTouchEnd = (event: React.TouchEvent) => {
    if (touchStartX.current == null || images.length <= 1) return;
    const deltaX = (event.changedTouches[0]?.clientX ?? touchStartX.current) - touchStartX.current;
    const SWIPE_THRESHOLD = 50;
    if (deltaX > SWIPE_THRESHOLD) goPrev();
    else if (deltaX < -SWIPE_THRESHOLD) goNext();
    touchStartX.current = null;
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/90"
      onClick={onClose}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      <button
        aria-label="Close"
        className="absolute top-4 right-4 z-10 rounded-full bg-black/40 p-2 text-white hover:bg-black/60"
        onClick={(event) => {
          event.stopPropagation();
          onClose();
        }}
        type="button"
      >
        <X className="size-5" />
      </button>

      {images.length > 1 && (
        <>
          <button
            aria-label="Previous image"
            className="absolute left-2 top-1/2 z-10 -translate-y-1/2 rounded-full bg-black/40 p-2 text-white hover:bg-black/60 sm:left-4"
            onClick={(event) => {
              event.stopPropagation();
              goPrev();
            }}
            type="button"
          >
            <ChevronLeft className="size-6" />
          </button>
          <button
            aria-label="Next image"
            className="absolute right-2 top-1/2 z-10 -translate-y-1/2 rounded-full bg-black/40 p-2 text-white hover:bg-black/60 sm:right-4"
            onClick={(event) => {
              event.stopPropagation();
              goNext();
            }}
            type="button"
          >
            <ChevronRight className="size-6" />
          </button>

          <div className="absolute bottom-4 left-1/2 z-10 -translate-x-1/2 rounded-full bg-black/40 px-3 py-1 text-xs text-white">
            {index + 1} / {images.length}
          </div>
        </>
      )}

      <img
        alt=""
        className={cn(
          "max-h-[90vh] max-w-[90vw] object-contain",
          images.length > 1 && "select-none",
        )}
        draggable={false}
        onClick={(event) => event.stopPropagation()}
        src={resolveFileUrl(images[index] ?? "")}
      />
    </div>
  );
}
