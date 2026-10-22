import { useEffect } from "react";
import type { Quality } from "@/lib/cast-store";

export const surfaces = {
  source: null as HTMLCanvasElement | null,
  tv: null as HTMLCanvasElement | null,
};

function drawScreen(canvas: HTMLCanvasElement, now: number, lagMs: number, reduce: boolean) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  const s = h / 540;
  const t = reduce ? 0 : now - lagMs;

  ctx.clearRect(0, 0, w, h);
  ctx.fillStyle = "#0b0f19";
  ctx.fillRect(0, 0, w, h);

  ctx.save();
  ctx.globalAlpha = 0.85;
  ctx.strokeStyle = "#3b82ff";
  ctx.lineWidth = Math.max(2, 3 * s);
  const drift = reduce ? w * 0.12 : ((t / 26) % (w + 120)) - 60;
  ctx.beginPath();
  ctx.moveTo(drift, h * 0.96);
  ctx.lineTo(drift + w * 0.22, h * 0.22);
  ctx.lineTo(drift + w * 0.4, h * 0.52);
  ctx.stroke();
  ctx.strokeStyle = "#ff3344";
  ctx.lineWidth = Math.max(4, 8 * s);
  ctx.beginPath();
  ctx.arc(w * 0.84, h * 0.2, 46 * s, 0.2, 1.45);
  ctx.stroke();
  ctx.restore();

  ctx.fillStyle = "#f0c14b";
  ctx.fillRect(28 * s, 22 * s, 18 * s, 4 * s);

  ctx.fillStyle = "#f3efe6";
  ctx.font = `600 ${Math.round(15 * s)}px Manrope, sans-serif`;
  ctx.textAlign = "left";
  ctx.fillText("Pixel 10", 28 * s, 48 * s);
  ctx.fillStyle = "#9aa3b5";
  ctx.font = `500 ${Math.round(15 * s)}px Manrope, sans-serif`;
  ctx.textAlign = "right";
  ctx.fillText("Home  ·  86%", w - 28 * s, 48 * s);

  const clock = new Date();
  const hh = String(clock.getHours()).padStart(2, "0");
  const mm = String(clock.getMinutes()).padStart(2, "0");
  const day = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"][clock.getDay()];
  ctx.textAlign = "left";
  ctx.fillStyle = "#f3efe6";
  ctx.font = `700 ${Math.round(84 * s)}px Syne, sans-serif`;
  ctx.fillText(`${hh}:${mm}`, 28 * s, h * 0.42);
  ctx.fillStyle = "#9aa3b5";
  ctx.font = `500 ${Math.round(16 * s)}px Manrope, sans-serif`;
  ctx.fillText(`${day}  ·  screen capture`, 32 * s, h * 0.42 + 28 * s);

  const cardX = w * 0.56;
  const cardW = w * 0.38;
  roundRect(ctx, cardX, h * 0.22, cardW, h * 0.22, 16 * s, "#121826");
  ctx.fillStyle = "#f0c14b";
  ctx.fillRect(cardX + 16 * s, h * 0.22 + 16 * s, 28 * s, 4 * s);
  ctx.fillStyle = "#f3efe6";
  ctx.font = `600 ${Math.round(18 * s)}px Manrope, sans-serif`;
  ctx.fillText("Living room", cardX + 16 * s, h * 0.22 + 48 * s);
  ctx.fillStyle = "#9aa3b5";
  ctx.font = `500 ${Math.round(14 * s)}px Manrope, sans-serif`;
  ctx.fillText("Stick is on the LAN", cardX + 16 * s, h * 0.22 + 72 * s);

  const playY = h * 0.5;
  roundRect(ctx, cardX, playY, cardW, h * 0.28, 16 * s, "#121826");
  ctx.fillStyle = "#3b82ff";
  ctx.beginPath();
  ctx.arc(cardX + 28 * s, playY + 28 * s, 8 * s, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#f3efe6";
  ctx.font = `600 ${Math.round(18 * s)}px Manrope, sans-serif`;
  ctx.fillText("Night drive", cardX + 46 * s, playY + 34 * s);
  ctx.fillStyle = "#9aa3b5";
  ctx.font = `500 ${Math.round(14 * s)}px Manrope, sans-serif`;
  ctx.fillText("System audio", cardX + 16 * s, playY + 64 * s);

  const progress = reduce ? 0.4 : (t / 9000) % 1;
  const barX = cardX + 16 * s;
  const barW = cardW - 32 * s;
  const barY = playY + h * 0.28 - 28 * s;
  ctx.fillStyle = "#243044";
  roundRect(ctx, barX, barY, barW, 6 * s, 3 * s, "#243044");
  ctx.fillStyle = "#3b82ff";
  roundRect(ctx, barX, barY, Math.max(6 * s, barW * progress), 6 * s, 3 * s, "#3b82ff");

  const dockY = h * 0.84;
  const icons = ["#ff3344", "#3b82ff", "#f0c14b", "#243044"];
  const gap = 16 * s;
  const icon = 36 * s;
  const dockW = icons.length * icon + (icons.length - 1) * gap + 28 * s;
  roundRect(ctx, 28 * s, dockY, dockW, 52 * s, 18 * s, "#121826");
  icons.forEach((color, index) => {
    ctx.fillStyle = color;
    roundRect(ctx, 42 * s + index * (icon + gap), dockY + 8 * s, icon, icon, 10 * s, color);
  });
}

function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
  fill: string,
) {
  const radius = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + w, y, x + w, y + h, radius);
  ctx.arcTo(x + w, y + h, x, y + h, radius);
  ctx.arcTo(x, y + h, x, y, radius);
  ctx.arcTo(x, y, x + w, y, radius);
  ctx.closePath();
  ctx.fillStyle = fill;
  ctx.fill();
}

export function StageLoop({
  active,
  paused,
  quality,
  latencyMs,
}: {
  active: boolean;
  paused: boolean;
  quality: Quality;
  latencyMs: number;
}) {
  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let frame = 0;
    let lastTv = 0;
    const tick = (now: number) => {
      const source = surfaces.source;
      if (source) drawScreen(source, now, 0, reduce);
      const tv = surfaces.tv;
      const interval = quality === "1080p60" ? 16 : 33;
      if (tv && active && !paused && now - lastTv >= interval) {
        lastTv = now;
        drawScreen(tv, now, reduce ? 0 : latencyMs, reduce);
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [active, paused, quality, latencyMs]);

  return null;
}

export function sizeFor(quality: Quality) {
  if (quality === "720p30") return { width: 640, height: 360 };
  return { width: 960, height: 540 };
}
