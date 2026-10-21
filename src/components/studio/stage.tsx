import { useEffect } from "react";
import type { Quality } from "@/lib/cast-store";

export const surfaces = {
  source: null as HTMLCanvasElement | null,
  tv: null as HTMLCanvasElement | null,
};

function drawSource(canvas: HTMLCanvasElement, now: number, reduce: boolean) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  ctx.fillStyle = "#0b0f19";
  ctx.fillRect(0, 0, w, h);

  const drift = reduce ? 0 : (now / 18) % w;
  ctx.strokeStyle = "#3b82ff";
  ctx.globalAlpha = 0.85;
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.moveTo(-40 + drift, h * 0.72);
  ctx.lineTo(w * 0.35 + drift * 0.2, h * 0.28);
  ctx.lineTo(w * 0.62, h * 0.58);
  ctx.stroke();

  ctx.strokeStyle = "#ff3344";
  ctx.lineWidth = 8;
  ctx.beginPath();
  ctx.arc(w * 0.78, h * 0.34, 54, 0.2, 1.4);
  ctx.stroke();

  ctx.globalAlpha = 1;
  ctx.fillStyle = "#f0c14b";
  ctx.fillRect(36, 36, 18, 4);

  const clock = new Date();
  const hh = String(clock.getHours()).padStart(2, "0");
  const mm = String(clock.getMinutes()).padStart(2, "0");
  ctx.fillStyle = "#f3efe6";
  ctx.font = "600 72px Manrope, sans-serif";
  ctx.fillText(`${hh}:${mm}`, 36, h * 0.62);
  ctx.fillStyle = "#9aa3b5";
  ctx.font = "500 18px Manrope, sans-serif";
  ctx.fillText("PIXEL 10  ·  SCREEN", 40, h * 0.62 + 32);
}

export function StageLoop({
  active,
  paused,
  quality,
}: {
  active: boolean;
  paused: boolean;
  quality: Quality;
}) {
  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let frame = 0;
    let lastCopy = 0;
    const tick = (now: number) => {
      const source = surfaces.source;
      if (source) drawSource(source, now, reduce);
      const tv = surfaces.tv;
      const interval = quality === "1080p60" ? 16 : 34;
      if (tv && source && active && !paused && now - lastCopy >= interval) {
        lastCopy = now;
        const ctx = tv.getContext("2d");
        if (ctx) ctx.drawImage(source, 0, 0, tv.width, tv.height);
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [active, paused, quality]);

  return null;
}

export function sizeFor(quality: Quality) {
  if (quality === "720p30") return { width: 640, height: 360 };
  return { width: 960, height: 540 };
}
