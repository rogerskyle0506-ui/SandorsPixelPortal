import { create } from "zustand";

export type Tab = "pixel" | "stick" | "guide";
export type Phase = "idle" | "scanning" | "connecting" | "need_pin" | "consent" | "casting" | "paused";
export type Quality = "720p30" | "1080p30" | "1080p60";

export type Device = {
  id: string;
  name: string;
  host: string;
  place: string;
};

export const ROOM: Device[] = [
  { id: "living", name: "Sandors-A1F3", host: "192.168.1.42", place: "Living room" },
  { id: "bed", name: "Sandors-9C20", host: "192.168.1.57", place: "Bedroom" },
];

const QUALITIES: Quality[] = ["720p30", "1080p30", "1080p60"];

const BITRATE: Record<Quality, string> = {
  "720p30": "4.0 Mb/s",
  "1080p30": "8.0 Mb/s",
  "1080p60": "12.0 Mb/s",
};

type CastState = {
  tab: Tab;
  phase: Phase;
  shade: boolean;
  devices: Device[];
  target: Device | null;
  savedId: string | null;
  pin: string;
  entry: string;
  attempts: number;
  error: string;
  shake: boolean;
  quality: Quality;
  volume: number;
  latencyMs: number;
  setTab: (tab: Tab) => void;
  toggleShade: (open?: boolean) => void;
  scan: () => void;
  select: (device: Device) => void;
  press: (key: string) => void;
  commitPin: () => void;
  allowCapture: () => void;
  denyCapture: () => void;
  cancelPair: () => void;
  togglePlay: () => void;
  nextQuality: () => void;
  setVolume: (volume: number) => void;
  stop: () => void;
  tileTap: () => void;
  setLatency: (latencyMs: number) => void;
  clearShake: () => void;
  bitrate: () => string;
};

function makePin() {
  const buf = new Uint32Array(1);
  crypto.getRandomValues(buf);
  return String(buf[0]! % 10000).padStart(4, "0");
}

function readSaved(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return localStorage.getItem("sandors-last");
  } catch {
    return null;
  }
}

export const useCast = create<CastState>((set, get) => ({
  tab: "pixel",
  phase: "idle",
  shade: false,
  devices: [],
  target: null,
  savedId: null,
  pin: "",
  entry: "",
  attempts: 3,
  error: "",
  shake: false,
  quality: "1080p30",
  volume: 80,
  latencyMs: 24,
  setTab: (tab) => set({ tab }),
  toggleShade: (open) => set({ shade: open ?? !get().shade }),
  scan: () => {
    set({
      phase: "scanning",
      error: "",
      devices: [],
      shade: false,
      entry: "",
      pin: "",
      target: null,
      attempts: 3,
      shake: false,
    });
    window.setTimeout(() => {
      if (get().phase !== "scanning") return;
      const saved = readSaved();
      set({
        phase: "idle",
        savedId: saved,
        devices: ROOM,
        error: "",
      });
    }, 900);
  },
  select: (device) => {
    set({
      phase: "connecting",
      target: device,
      pin: makePin(),
      entry: "",
      attempts: 3,
      error: "",
      shake: false,
      shade: false,
    });
    window.setTimeout(() => {
      const state = get();
      if (state.phase === "connecting" && state.target?.id === device.id) {
        set({ phase: "need_pin" });
      }
    }, 700);
  },
  press: (key) => {
    const state = get();
    if (state.phase !== "need_pin") return;
    if (key === "back") {
      set({ entry: state.entry.slice(0, -1), shake: false, error: "" });
      return;
    }
    if (state.entry.length >= 4) return;
    set({ entry: state.entry + key, shake: false });
  },
  commitPin: () => {
    const state = get();
    if (state.phase !== "need_pin" || state.entry.length !== 4) return;
    if (state.entry === state.pin && state.target) {
      try {
        localStorage.setItem("sandors-last", state.target.id);
      } catch {
        /* rehearsal still proceeds */
      }
      set({
        phase: "consent",
        savedId: state.target.id,
        entry: "",
        error: "",
        shake: false,
      });
      return;
    }
    const attempts = state.attempts - 1;
    if (attempts <= 0) {
      set({
        phase: "idle",
        attempts: 3,
        entry: "",
        pin: "",
        target: null,
        shake: true,
        error: "Too many tries. Pair again.",
      });
      return;
    }
    set({
      attempts,
      entry: "",
      shake: true,
      error: `That code did not match. ${attempts} left.`,
    });
  },
  allowCapture: () => {
    if (get().phase !== "consent" || !get().target) return;
    set({ phase: "casting", error: "" });
  },
  denyCapture: () =>
    set({
      phase: "idle",
      entry: "",
      pin: "",
      target: null,
      error: "Screen capture was cancelled.",
      shake: false,
      attempts: 3,
    }),
  cancelPair: () =>
    set({
      phase: "idle",
      entry: "",
      pin: "",
      target: null,
      error: "",
      shake: false,
      attempts: 3,
    }),
  togglePlay: () => {
    const phase = get().phase;
    if (phase === "casting") set({ phase: "paused" });
    else if (phase === "paused") set({ phase: "casting" });
  },
  nextQuality: () => {
    const current = get().quality;
    const next = QUALITIES[(QUALITIES.indexOf(current) + 1) % QUALITIES.length] ?? "1080p30";
    set({ quality: next });
  },
  setVolume: (volume) => set({ volume: Math.max(0, Math.min(100, volume)) }),
  stop: () =>
    set({
      phase: "idle",
      entry: "",
      pin: "",
      target: null,
      error: "",
      shade: false,
    }),
  tileTap: () => {
    const { phase } = get();
    if (phase === "casting" || phase === "paused") {
      get().stop();
      return;
    }
    set({ shade: false });
    get().scan();
  },
  setLatency: (latencyMs) => set({ latencyMs }),
  clearShake: () => set({ shake: false }),
  bitrate: () => BITRATE[get().quality],
}));
