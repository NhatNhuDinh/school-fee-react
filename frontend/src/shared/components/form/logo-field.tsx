"use client";

import { ImagePlus, Trash2 } from "lucide-react";
import { useRef, useState } from "react";

import { Button } from "@/shared/ui/button";
import { cn } from "@/shared/lib/utils";

/** The longest side a logo is shrunk to. It is shown at 32px and sent on every sign-in. */
const MAX_SIDE = 256;

/** Larger than this is refused before it is even decoded. */
const MAX_FILE_BYTES = 5 * 1024 * 1024;

type LogoFieldProps = {
  /** The logo as a data URL, or empty for none. */
  value: string | null | undefined;
  onChange: (value: string) => void;
  className?: string;
};

/**
 * Reads an image file and shrinks it to a small PNG on a white square.
 *
 * Done in the browser so the API only ever receives something small: the logo
 * rides along with the signed-in identity, and a photograph straight from a
 * camera would make every sign-in and refresh carry megabytes. A white
 * background rather than a transparent one, because a logo with transparent
 * areas disappears on the dark sidebar.
 */
async function toSquareDataUrl(file: File): Promise<string> {
  const source = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("Không đọc được tệp ảnh"));
    reader.readAsDataURL(file);
  });

  const image = new Image();
  image.src = source;
  await image.decode();

  const scale = Math.min(MAX_SIDE / image.width, MAX_SIDE / image.height, 1);
  const width = Math.max(1, Math.round(image.width * scale));
  const height = Math.max(1, Math.round(image.height * scale));
  const side = Math.max(width, height);

  const canvas = document.createElement("canvas");
  canvas.width = side;
  canvas.height = side;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Trình duyệt không xử lý được ảnh");
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, side, side);
  context.imageSmoothingQuality = "high";
  context.drawImage(image, (side - width) / 2, (side - height) / 2, width, height);
  return canvas.toDataURL("image/png");
}

/**
 * Picks, previews and removes a school's logo.
 *
 * The value is a data URL held in the form like any other string, so the form
 * needs no special handling: an empty string means "no logo" and is what the API
 * reads as "remove it".
 */
export function LogoField({ value, onChange, className }: LogoFieldProps) {
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleFile(file: File | undefined) {
    if (!file) return;
    setError(null);
    if (!file.type.startsWith("image/")) {
      setError("Hãy chọn một tệp ảnh");
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      setError("Ảnh quá lớn, hãy chọn ảnh nhỏ hơn 5 MB");
      return;
    }
    setBusy(true);
    try {
      onChange(await toSquareDataUrl(file));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không đọc được ảnh");
    } finally {
      setBusy(false);
      // Choosing the same file again must still fire a change.
      if (input.current) input.current.value = "";
    }
  }

  return (
    <div className={cn("flex items-center gap-4", className)}>
      <div className="bg-muted/40 flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-xl border">
        {value ? (
          <img src={value} alt="Logo trường" className="size-full object-contain" />
        ) : (
          <ImagePlus className="text-muted-foreground size-6" aria-hidden />
        )}
      </div>

      <div className="flex flex-col gap-2">
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={busy}
            onClick={() => input.current?.click()}
          >
            {value ? "Đổi ảnh" : "Chọn ảnh"}
          </Button>
          {value ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => onChange("")}
            >
              <Trash2 className="h-4 w-4" />
              Xóa logo
            </Button>
          ) : null}
        </div>
        <p className="text-muted-foreground text-xs">
          Ảnh png, jpg, webp hoặc svg. Tự thu nhỏ về ô vuông.
        </p>
        {error ? <p className="text-destructive text-xs">{error}</p> : null}
      </div>

      <input
        ref={input}
        type="file"
        accept="image/png,image/jpeg,image/webp,image/svg+xml"
        className="hidden"
        onChange={(e) => void handleFile(e.target.files?.[0])}
      />
    </div>
  );
}
