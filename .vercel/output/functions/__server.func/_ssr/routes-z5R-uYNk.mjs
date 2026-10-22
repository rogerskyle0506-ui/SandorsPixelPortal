import { i as __toESM } from "../_runtime.mjs";
import { a as require_jsx_runtime, o as require_react } from "../_libs/@radix-ui/react-collection+[...].mjs";
import { a as Square, c as Radio, d as Download, f as ChevronDown, l as Play, n as Volume2, o as Smartphone, p as Cast, r as Tv, s as SkipForward, t as Wifi, u as Pause } from "../_libs/lucide-react.mjs";
import { i as SliderTrack, n as SliderRange, r as SliderThumb, t as Slider } from "../_libs/@radix-ui/react-slider+[...].mjs";
import { t as create } from "../_libs/zustand.mjs";
//#region node_modules/.nitro/vite/services/ssr/assets/routes-z5R-uYNk.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_runtime = require_jsx_runtime();
var ROOM = [{
	id: "living",
	name: "Sandors-A1F3",
	host: "192.168.1.42",
	place: "Living room"
}, {
	id: "bed",
	name: "Sandors-9C20",
	host: "192.168.1.57",
	place: "Bedroom"
}];
var QUALITIES = [
	"720p30",
	"1080p30",
	"1080p60"
];
var BITRATE = {
	"720p30": "4.0 Mb/s",
	"1080p30": "8.0 Mb/s",
	"1080p60": "12.0 Mb/s"
};
function makePin() {
	const buf = /* @__PURE__ */ new Uint32Array(1);
	crypto.getRandomValues(buf);
	return String(buf[0] % 1e4).padStart(4, "0");
}
function readSaved() {
	if (typeof window === "undefined") return null;
	try {
		return localStorage.getItem("sandors-last");
	} catch {
		return null;
	}
}
var useCast = create((set, get) => ({
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
			shake: false
		});
		window.setTimeout(() => {
			if (get().phase !== "scanning") return;
			set({
				phase: "idle",
				savedId: readSaved(),
				devices: ROOM,
				error: ""
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
			shade: false
		});
		window.setTimeout(() => {
			const state = get();
			if (state.phase === "connecting" && state.target?.id === device.id) set({ phase: "need_pin" });
		}, 700);
	},
	press: (key) => {
		const state = get();
		if (state.phase !== "need_pin") return;
		if (key === "back") {
			set({
				entry: state.entry.slice(0, -1),
				shake: false,
				error: ""
			});
			return;
		}
		if (state.entry.length >= 4) return;
		set({
			entry: state.entry + key,
			shake: false
		});
	},
	commitPin: () => {
		const state = get();
		if (state.phase !== "need_pin" || state.entry.length !== 4) return;
		if (state.entry === state.pin && state.target) {
			try {
				localStorage.setItem("sandors-last", state.target.id);
			} catch {}
			set({
				phase: "consent",
				savedId: state.target.id,
				entry: "",
				error: "",
				shake: false
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
				error: "Too many tries. Pair again."
			});
			return;
		}
		set({
			attempts,
			entry: "",
			shake: true,
			error: `That code did not match. ${attempts} left.`
		});
	},
	allowCapture: () => {
		if (get().phase !== "consent" || !get().target) return;
		set({
			phase: "casting",
			error: ""
		});
	},
	denyCapture: () => set({
		phase: "idle",
		entry: "",
		pin: "",
		target: null,
		error: "Screen capture was cancelled.",
		shake: false,
		attempts: 3
	}),
	cancelPair: () => set({
		phase: "idle",
		entry: "",
		pin: "",
		target: null,
		error: "",
		shake: false,
		attempts: 3
	}),
	togglePlay: () => {
		const phase = get().phase;
		if (phase === "casting") set({ phase: "paused" });
		else if (phase === "paused") set({ phase: "casting" });
	},
	nextQuality: () => {
		const current = get().quality;
		set({ quality: QUALITIES[(QUALITIES.indexOf(current) + 1) % QUALITIES.length] ?? "1080p30" });
	},
	setVolume: (volume) => set({ volume: Math.max(0, Math.min(100, volume)) }),
	stop: () => set({
		phase: "idle",
		entry: "",
		pin: "",
		target: null,
		error: "",
		shade: false
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
	bitrate: () => BITRATE[get().quality]
}));
var surfaces = {
	source: null,
	tv: null
};
function drawScreen(canvas, now, lagMs, reduce) {
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
	ctx.globalAlpha = .85;
	ctx.strokeStyle = "#3b82ff";
	ctx.lineWidth = Math.max(2, 3 * s);
	const drift = reduce ? w * .12 : t / 26 % (w + 120) - 60;
	ctx.beginPath();
	ctx.moveTo(drift, h * .96);
	ctx.lineTo(drift + w * .22, h * .22);
	ctx.lineTo(drift + w * .4, h * .52);
	ctx.stroke();
	ctx.strokeStyle = "#ff3344";
	ctx.lineWidth = Math.max(4, 8 * s);
	ctx.beginPath();
	ctx.arc(w * .84, h * .2, 46 * s, .2, 1.45);
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
	const clock = /* @__PURE__ */ new Date();
	const hh = String(clock.getHours()).padStart(2, "0");
	const mm = String(clock.getMinutes()).padStart(2, "0");
	const day = [
		"Sunday",
		"Monday",
		"Tuesday",
		"Wednesday",
		"Thursday",
		"Friday",
		"Saturday"
	][clock.getDay()];
	ctx.textAlign = "left";
	ctx.fillStyle = "#f3efe6";
	ctx.font = `700 ${Math.round(84 * s)}px Syne, sans-serif`;
	ctx.fillText(`${hh}:${mm}`, 28 * s, h * .42);
	ctx.fillStyle = "#9aa3b5";
	ctx.font = `500 ${Math.round(16 * s)}px Manrope, sans-serif`;
	ctx.fillText(`${day}  ·  screen capture`, 32 * s, h * .42 + 28 * s);
	const cardX = w * .56;
	const cardW = w * .38;
	roundRect(ctx, cardX, h * .22, cardW, h * .22, 16 * s, "#121826");
	ctx.fillStyle = "#f0c14b";
	ctx.fillRect(cardX + 16 * s, h * .22 + 16 * s, 28 * s, 4 * s);
	ctx.fillStyle = "#f3efe6";
	ctx.font = `600 ${Math.round(18 * s)}px Manrope, sans-serif`;
	ctx.fillText("Living room", cardX + 16 * s, h * .22 + 48 * s);
	ctx.fillStyle = "#9aa3b5";
	ctx.font = `500 ${Math.round(14 * s)}px Manrope, sans-serif`;
	ctx.fillText("Stick is on the LAN", cardX + 16 * s, h * .22 + 72 * s);
	const playY = h * .5;
	roundRect(ctx, cardX, playY, cardW, h * .28, 16 * s, "#121826");
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
	const progress = reduce ? .4 : t / 9e3 % 1;
	const barX = cardX + 16 * s;
	const barW = cardW - 32 * s;
	const barY = playY + h * .28 - 28 * s;
	ctx.fillStyle = "#243044";
	roundRect(ctx, barX, barY, barW, 6 * s, 3 * s, "#243044");
	ctx.fillStyle = "#3b82ff";
	roundRect(ctx, barX, barY, Math.max(6 * s, barW * progress), 6 * s, 3 * s, "#3b82ff");
	const dockY = h * .84;
	const icons = [
		"#ff3344",
		"#3b82ff",
		"#f0c14b",
		"#243044"
	];
	const gap = 16 * s;
	const icon = 36 * s;
	const dockW = icons.length * icon + (icons.length - 1) * gap + 28 * s;
	roundRect(ctx, 28 * s, dockY, dockW, 52 * s, 18 * s, "#121826");
	icons.forEach((color, index) => {
		ctx.fillStyle = color;
		roundRect(ctx, 42 * s + index * (icon + gap), dockY + 8 * s, icon, icon, 10 * s, color);
	});
}
function roundRect(ctx, x, y, w, h, r, fill) {
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
function StageLoop({ active, paused, quality, latencyMs }) {
	(0, import_react.useEffect)(() => {
		const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
		let frame = 0;
		let lastTv = 0;
		const tick = (now) => {
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
	}, [
		active,
		paused,
		quality,
		latencyMs
	]);
	return null;
}
function sizeFor(quality) {
	if (quality === "720p30") return {
		width: 640,
		height: 360
	};
	return {
		width: 960,
		height: 540
	};
}
var KEYS = [
	"1",
	"2",
	"3",
	"4",
	"5",
	"6",
	"7",
	"8",
	"9"
];
function Studio() {
	const tab = useCast((s) => s.tab);
	const phase = useCast((s) => s.phase);
	const quality = useCast((s) => s.quality);
	const latencyMs = useCast((s) => s.latencyMs);
	const setLatency = useCast((s) => s.setLatency);
	(0, import_react.useEffect)(() => {
		try {
			const saved = localStorage.getItem("sandors-last");
			if (saved) useCast.setState({ savedId: saved });
		} catch {}
	}, []);
	(0, import_react.useEffect)(() => {
		if (phase !== "casting") return;
		const id = window.setInterval(() => {
			setLatency(18 + Math.round(Math.random() * 22));
		}, 900);
		return () => window.clearInterval(id);
	}, [phase, setLatency]);
	const pinFirst = phase === "connecting" || phase === "need_pin" || phase === "consent";
	const both = pinFirst || phase === "casting" || phase === "paused";
	const frame = "min-w-0 flex-col lg:flex lg:h-full lg:min-h-0 lg:overflow-hidden";
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex h-dvh flex-col overflow-hidden bg-obsidian text-ink",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(StageLoop, {
				active: phase === "casting" || phase === "paused",
				paused: phase === "paused",
				quality,
				latencyMs
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Header, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("main", {
				className: tab === "guide" ? "mx-auto min-h-0 w-full max-w-6xl flex-1 overflow-y-auto px-4 py-3 lg:px-6" : "mx-auto grid min-h-0 w-full max-w-6xl flex-1 grid-cols-1 gap-3 overflow-y-auto px-4 py-3 lg:grid-cols-2 lg:overflow-hidden lg:px-6",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
						className: tab === "guide" ? "hidden" : both ? `${pinFirst ? "order-2 lg:order-1" : "order-1"} flex ${frame}` : tab === "pixel" ? `order-1 flex ${frame}` : `order-1 hidden ${frame}`,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PixelPanel, {})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
						className: tab === "guide" ? "hidden" : both ? `${pinFirst ? "order-1 lg:order-2" : "order-2"} flex ${frame}` : tab === "stick" ? `order-2 flex ${frame}` : `order-2 hidden ${frame}`,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(StickPanel, {})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("section", {
						className: tab === "guide" ? "min-w-0" : "hidden",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(GuidePanel, {})
					})
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Shade, {}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("nav", {
				className: "z-30 shrink-0 border-t border-line bg-obsidian",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "mx-auto grid max-w-6xl grid-cols-3 gap-2 px-3 py-2 lg:px-6",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TabButton, {
							id: "pixel",
							label: "Pixel",
							icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Smartphone, { className: "size-5" })
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TabButton, {
							id: "stick",
							label: "Fire Stick",
							icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tv, { className: "size-5" })
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TabButton, {
							id: "guide",
							label: "Build",
							icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Download, { className: "size-5" })
						})
					]
				})
			})
		]
	});
}
function Header() {
	const toggleShade = useCast((s) => s.toggleShade);
	const phase = useCast((s) => s.phase);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("header", {
		className: "mx-auto flex w-full max-w-6xl items-center justify-between gap-3 px-4 py-5 lg:px-6",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
			className: "font-display text-xs font-bold tracking-widest text-red",
			children: "SANDORS"
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("h1", {
			className: "font-display text-3xl font-bold leading-none",
			children: "Mirrorcast"
		})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
			type: "button",
			onClick: () => toggleShade(true),
			"aria-label": "Quick settings",
			className: "flex min-h-11 items-center gap-2 rounded-full border border-line bg-obsidian-2 px-4 text-sm font-semibold text-ink",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { className: phase === "casting" || phase === "paused" ? "size-2 rounded-full bg-red" : "size-2 rounded-full bg-blue" }),
				"Tiles",
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ChevronDown, { className: "size-4 text-muted" })
			]
		})]
	});
}
function PixelPanel() {
	const phase = useCast((s) => s.phase);
	const devices = useCast((s) => s.devices);
	const savedId = useCast((s) => s.savedId);
	const target = useCast((s) => s.target);
	const entry = useCast((s) => s.entry);
	const error = useCast((s) => s.error);
	const shake = useCast((s) => s.shake);
	const quality = useCast((s) => s.quality);
	const volume = useCast((s) => s.volume);
	const latencyMs = useCast((s) => s.latencyMs);
	const scan = useCast((s) => s.scan);
	const select = useCast((s) => s.select);
	const press = useCast((s) => s.press);
	const cancelPair = useCast((s) => s.cancelPair);
	const commitPin = useCast((s) => s.commitPin);
	const allowCapture = useCast((s) => s.allowCapture);
	const denyCapture = useCast((s) => s.denyCapture);
	const clearShake = useCast((s) => s.clearShake);
	const bitrate = useCast((s) => s.bitrate);
	(0, import_react.useEffect)(() => {
		if (phase === "need_pin" && entry.length === 4) commitPin();
	}, [
		phase,
		entry,
		commitPin
	]);
	(0, import_react.useEffect)(() => {
		if (!shake) return;
		const id = window.setTimeout(clearShake, 400);
		return () => window.clearTimeout(id);
	}, [shake, clearShake]);
	const saved = ROOM.find((device) => device.id === savedId) ?? null;
	const pairing = phase === "connecting" || phase === "need_pin";
	const live = phase === "casting" || phase === "paused";
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex h-auto flex-col gap-4 overflow-y-auto rounded-card border border-line bg-obsidian-2 p-4 lg:h-full",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex items-center justify-between gap-3",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-sm font-semibold text-muted",
					children: "Pixel 10 · sender"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-sm text-blue",
					children: phase === "scanning" ? "Scanning" : phase === "connecting" ? "Connecting" : phase === "need_pin" ? "Enter code" : phase === "consent" ? "Allow capture" : phase === "paused" ? "Paused" : phase === "casting" ? "On air" : "Idle"
				})]
			}),
			!pairing && !live && phase !== "consent" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-base leading-relaxed text-ink",
					children: "Find the Fire Stick, confirm the code on the TV, then leave the app. The cast stays up."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
					type: "button",
					onClick: scan,
					disabled: phase === "scanning",
					className: "min-h-12 rounded-card bg-red px-4 text-base font-semibold text-obsidian disabled:opacity-60",
					children: phase === "scanning" ? "Listening for Sticks…" : "Scan the room"
				}),
				saved && phase !== "scanning" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DeviceRow, {
					device: saved,
					action: "Reconnect",
					saved: true,
					onSelect: select
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "flex flex-col gap-2",
					children: [devices.map((device) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DeviceRow, {
						device,
						action: "Pair",
						saved: device.id === savedId,
						onSelect: select
					}, device.id)), phase !== "scanning" && devices.length === 0 && !saved && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "text-sm text-muted",
						children: "Sticks on this Wi-Fi show up here. This preview uses two rehearsal Sticks."
					})]
				}),
				error && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "text-sm text-yellow",
					children: error
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-auto pt-4 text-sm leading-relaxed text-muted",
					children: "Same Wi-Fi. The Stick shows the code. The phone types it."
				})
			] }),
			pairing && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: shake ? "sandors-shake" : "",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "text-base text-ink",
						children: phase === "connecting" ? `Reaching ${target?.place ?? "the Stick"}…` : `Type the 4-digit code on the Fire Stick.`
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
						className: "mt-4 grid grid-cols-4 gap-2",
						children: [
							0,
							1,
							2,
							3
						].map((index) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
							className: "flex h-14 items-center justify-center rounded-card border border-line bg-obsidian font-display text-3xl font-bold text-yellow",
							children: entry[index] ?? ""
						}, index))
					}),
					error && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "mt-3 text-sm text-yellow",
						children: error
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "mt-4 grid grid-cols-3 gap-2",
						children: [
							KEYS.map((key) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
								type: "button",
								disabled: phase !== "need_pin",
								onClick: () => press(key),
								className: "min-h-12 rounded-card bg-obsidian text-xl font-semibold text-ink disabled:opacity-30",
								children: key
							}, key)),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { "aria-hidden": "true" }),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
								type: "button",
								disabled: phase !== "need_pin",
								onClick: () => press("0"),
								className: "min-h-12 rounded-card bg-obsidian text-xl font-semibold text-ink disabled:opacity-30",
								children: "0"
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
								type: "button",
								disabled: phase !== "need_pin",
								onClick: () => press("back"),
								className: "min-h-12 rounded-card bg-obsidian text-base font-semibold text-ink disabled:opacity-30",
								children: "Del"
							})
						]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "button",
						onClick: cancelPair,
						className: "mt-3 min-h-12 w-full rounded-card border border-line text-base font-semibold text-ink",
						children: "Cancel"
					})
				]
			}),
			phase === "consent" && target && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex flex-col gap-3",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "text-sm font-semibold tracking-wide text-blue",
						children: "Screen capture"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
						className: "font-display text-2xl font-bold",
						children: [
							"Share this Pixel with ",
							target.place,
							"?"
						]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "text-base leading-relaxed text-muted",
						children: "Sandors will record everything on the screen, including notifications, and the system audio that apps allow."
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "button",
						onClick: allowCapture,
						className: "min-h-12 rounded-card bg-red px-4 text-base font-semibold text-obsidian",
						children: "Start now"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "button",
						onClick: denyCapture,
						className: "min-h-12 rounded-card border border-line text-base font-semibold text-ink",
						children: "Don't allow"
					})
				]
			}),
			live && target && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex flex-col gap-4",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "font-display text-2xl font-bold",
						children: phase === "paused" ? "Paused" : `Casting to ${target.place}`
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
						className: "mt-1 text-sm text-muted",
						children: [
							target.name,
							" · ",
							target.host
						]
					})] }),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "grid grid-cols-3 gap-2 text-center",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stat, {
								label: "Latency",
								value: phase === "paused" ? "Held" : `${latencyMs} ms`
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stat, {
								label: "Bitrate",
								value: bitrate()
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stat, {
								label: "Picture",
								value: quality
							})
						]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
						className: "sandors-stage h-40 w-full shrink-0",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
							className: "sandors-frame rounded-card bg-obsidian",
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("canvas", {
								ref: (node) => {
									surfaces.source = node;
								},
								width: 960,
								height: 540,
								className: "absolute inset-0 h-full w-full"
							})
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
						className: "flex items-center gap-3 text-sm text-muted",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Volume2, { className: "size-5 text-blue" }),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Slider, {
								value: [volume],
								max: 100,
								step: 1,
								onValueChange: (value) => useCast.getState().setVolume(value[0] ?? 0),
								className: "relative flex h-11 min-w-0 flex-1 touch-none items-center",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SliderTrack, {
									className: "relative h-1.5 grow rounded-full bg-line",
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SliderRange, { className: "absolute h-full rounded-full bg-blue" })
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SliderThumb, {
									className: "flex size-11 items-center justify-center rounded-full bg-transparent outline-none",
									"aria-label": "Cast volume",
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { className: "block size-5 rounded-full border border-line bg-ink shadow" })
								})]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "w-8 text-right text-ink",
								children: volume
							})
						]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
						className: "sticky bottom-0 z-10 bg-obsidian-2 pt-1",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(NotificationCard, {})
					})
				]
			})
		]
	});
}
function DeviceRow({ device, action, saved, onSelect }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
		type: "button",
		onClick: () => onSelect(device),
		className: "flex min-h-16 w-full items-center justify-between gap-3 rounded-card border border-line bg-obsidian px-4 py-3 text-left",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "block font-semibold text-ink",
			children: device.place
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
			className: "block text-sm text-muted",
			children: [
				device.name,
				saved ? " · saved" : "",
				" · ",
				device.host
			]
		})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "font-semibold text-blue",
			children: action
		})]
	});
}
function Stat({ label, value }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "rounded-card bg-obsidian px-2 py-3",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
			className: "text-xs text-muted",
			children: label
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
			className: "mt-1 text-sm font-semibold text-ink",
			children: value
		})]
	});
}
function NotificationCard() {
	const phase = useCast((s) => s.phase);
	const quality = useCast((s) => s.quality);
	const togglePlay = useCast((s) => s.togglePlay);
	const nextQuality = useCast((s) => s.nextQuality);
	const stop = useCast((s) => s.stop);
	const playing = phase === "casting";
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "rounded-card bg-obsidian p-3",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "flex items-center gap-3",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "flex size-11 items-center justify-center rounded-2xl bg-red text-obsidian",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Cast, { className: "size-5" })
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "block text-sm font-semibold",
				children: "Sandors Mirrorcast"
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "block text-sm text-muted",
				children: playing ? `Casting · ${quality}` : `Paused · ${quality}`
			})] })]
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "mt-3 grid grid-cols-3 gap-2",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
					label: playing ? "Pause" : "Play",
					onClick: togglePlay,
					children: playing ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Pause, { className: "size-5" }) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Play, { className: "size-5" })
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
					label: "Next quality",
					onClick: nextQuality,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SkipForward, { className: "size-5" })
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
					label: "Stop cast",
					onClick: stop,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Square, { className: "size-5" })
				})
			]
		})]
	});
}
function IconButton({ label, onClick, children }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
		type: "button",
		"aria-label": label,
		onClick,
		className: "flex min-h-12 flex-col items-center justify-center gap-1 rounded-card bg-obsidian-3 text-xs font-semibold text-ink",
		children: [children, label]
	});
}
function StickPanel() {
	const phase = useCast((s) => s.phase);
	const pin = useCast((s) => s.pin);
	const target = useCast((s) => s.target);
	const quality = useCast((s) => s.quality);
	const live = phase === "casting" || phase === "paused";
	const size = sizeFor(quality);
	(0, import_react.useEffect)(() => {
		const tv = surfaces.tv;
		if (!tv) return;
		if (tv.width !== size.width || tv.height !== size.height) {
			tv.width = size.width;
			tv.height = size.height;
		}
	}, [
		size.width,
		size.height,
		live
	]);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex min-h-64 flex-col overflow-hidden rounded-card border border-line bg-obsidian-3 lg:h-full lg:min-h-0",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "flex items-center justify-between px-4 py-3",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "text-sm font-semibold text-muted",
				children: "Fire Stick · receiver"
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
				className: "flex items-center gap-2 text-sm text-yellow",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Radio, { className: phase === "idle" || phase === "scanning" ? "size-4 text-blue" : "sandors-pulse size-4 text-red" }), target?.name ?? "Sandors-A1F3"]
			})]
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: live ? "sandors-stage relative mx-4 mb-4 aspect-video w-full lg:aspect-auto lg:min-h-0 lg:flex-1" : "relative mx-4 mb-4 flex min-h-48 flex-1 flex-col items-center justify-center overflow-hidden rounded-card bg-obsidian",
			children: live ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "sandors-frame rounded-card bg-obsidian",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("canvas", {
						ref: (node) => {
							surfaces.tv = node;
						},
						width: size.width,
						height: size.height,
						className: "absolute inset-0 h-full w-full"
					}),
					phase === "paused" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", { className: "absolute inset-0 bg-obsidian/50" }),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "absolute bottom-3 left-3 z-10 rounded-full bg-obsidian/80 px-3 py-1 text-sm font-semibold text-yellow",
						children: phase === "paused" ? "Paused" : `Live · ${quality}`
					})
				]
			}) : phase === "need_pin" ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex flex-col items-center px-6 py-10 text-center",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "text-sm font-bold tracking-widest text-blue",
						children: "ENTER THIS CODE ON YOUR PIXEL"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "mt-4 font-display text-5xl font-bold text-yellow",
						children: pin
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "mt-4 text-sm text-muted",
						children: "Three tries, then the Stick closes the socket."
					})
				]
			}) : phase === "consent" ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex flex-col items-center px-6 py-10 text-center",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "text-sm font-bold tracking-widest text-yellow",
						children: "PAIRED"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "mt-4 font-display text-3xl font-bold",
						children: "Waiting for the Pixel"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "mt-3 max-w-sm text-sm leading-relaxed text-muted",
						children: "The phone still has to allow screen capture. Nothing is on the Stick until it does."
					})
				]
			}) : phase === "connecting" ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "px-6 py-16 text-center text-base text-ink",
				children: "A Pixel is asking to pair."
			}) : phase === "scanning" ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex flex-col items-center px-6 py-16 text-center",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "font-display text-3xl font-bold",
					children: "Answering the scan"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-3 max-w-sm text-sm leading-relaxed text-muted",
					children: "Sandors-A1F3 and Sandors-9C20 are on this Wi-Fi. Pick one on the Pixel."
				})]
			}) : /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex flex-col items-center px-6 py-16 text-center",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "font-display text-3xl font-bold",
					children: "Waiting for a Pixel"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-3 max-w-sm text-sm leading-relaxed text-muted",
					children: "Advertising sandorscast on the LAN. The code appears here when a phone connects."
				})]
			})
		})]
	});
}
function GuidePanel() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex flex-col gap-5 rounded-card border border-line bg-obsidian-2 p-5",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "font-display text-xs font-bold tracking-widest text-yellow",
					children: "SOURCE"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
					className: "mt-2 font-display text-3xl font-bold",
					children: "Put it on the devices"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-3 max-w-2xl text-base leading-relaxed text-muted",
					children: "This studio rehearses discovery, the 4-digit handshake, the screen-capture prompt, and the cast controls. The Pixel cannot capture its screen from the browser. The download is the two Android projects that do."
				})
			] }),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("a", {
				href: "/downloads/sandors-mirrorcast-source.zip",
				download: true,
				className: "flex min-h-12 items-center justify-center gap-2 rounded-card bg-yellow px-4 text-base font-semibold text-obsidian",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Download, { className: "size-5" }), "Download both projects"]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("ol", {
				className: "flex flex-col gap-3",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Step, {
						n: "1",
						title: "Unzip two apps",
						body: "SandorsSender for the Pixel 10. SandorsReceiver for the Fire Stick on Fire OS 7."
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Step, {
						n: "2",
						title: "Open each in Android Studio",
						body: "Let Gradle sync. Studio needs JDK 17 and Android SDK 35. It writes the SDK path itself."
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Step, {
						n: "3",
						title: "Sideload the Stick",
						body: "Turn on ADB debugging, then install the receiver APK. Open it once so it can listen after a reboot."
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Step, {
						n: "4",
						title: "Add the Sandors tile",
						body: "Scan, type the code on the TV, accept capture. Pause, next quality, volume, and stop live in the media notification."
					})
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "grid gap-3 sm:grid-cols-2",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Fact, {
						title: "Picture",
						body: "MediaProjection into a Baseline H.264 encoder. No B-frames. SPS and PPS ride every keyframe."
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Fact, {
						title: "Sound",
						body: "Playback capture as 48 kHz stereo PCM. No microphone permission."
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Fact, {
						title: "Network",
						body: "mDNS type _sandorscast._tcp, a TCP control line, and UDP for video and audio."
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Fact, {
						title: "Stays awake",
						body: "Foreground service, partial wake lock, and a low-latency Wi-Fi lock while the phone screen is off."
					})
				]
			})
		]
	});
}
function Step({ n, title, body }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("li", {
		className: "flex gap-3",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "flex size-8 shrink-0 items-center justify-center rounded-full bg-red text-sm font-bold text-obsidian",
			children: n
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "block font-semibold",
			children: title
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "block text-sm leading-relaxed text-muted",
			children: body
		})] })]
	});
}
function Fact({ title, body }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "rounded-card bg-obsidian p-4",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
			className: "font-semibold text-blue",
			children: title
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
			className: "mt-1 text-sm leading-relaxed text-muted",
			children: body
		})]
	});
}
function Shade() {
	const shade = useCast((s) => s.shade);
	const phase = useCast((s) => s.phase);
	const toggleShade = useCast((s) => s.toggleShade);
	const tileTap = useCast((s) => s.tileTap);
	if (!shade) return null;
	const active = phase === "casting" || phase === "paused";
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "fixed inset-0 z-40 flex flex-col bg-obsidian/80",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
			type: "button",
			"aria-label": "Close quick settings",
			className: "min-h-16 flex-1",
			onClick: () => toggleShade(false)
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "rounded-t-card border border-line bg-obsidian-2 p-4 pb-8",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "mb-4 flex items-center justify-between text-sm text-muted",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
					className: "flex items-center gap-2",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Wifi, { className: "size-4 text-blue" }), "Home Wi-Fi"]
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Quick settings" })]
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "grid grid-cols-2 gap-3",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
					type: "button",
					onClick: tileTap,
					className: active ? "flex min-h-24 flex-col items-start justify-between rounded-card bg-red p-4 text-left text-obsidian" : "flex min-h-24 flex-col items-start justify-between rounded-card bg-obsidian p-4 text-left text-ink",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Cast, { className: "size-6" }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "block font-semibold",
						children: "Sandors"
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "block text-sm",
						children: active ? "Casting" : "Mirrorcast"
					})] })]
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "flex min-h-24 flex-col justify-between rounded-card border border-line p-4",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Wifi, { className: "size-6 text-blue" }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "block font-semibold",
						children: "Same network"
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "block text-sm text-muted",
						children: "Multicast has to be allowed"
					})] })]
				})]
			})]
		})]
	});
}
function TabButton({ id, label, icon }) {
	const tab = useCast((s) => s.tab);
	const setTab = useCast((s) => s.setTab);
	const phase = useCast((s) => s.phase);
	const attention = id === "stick" && (phase === "need_pin" || phase === "connecting" || phase === "consent");
	const selected = tab === id;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
		type: "button",
		onClick: () => setTab(id),
		className: selected ? "flex min-h-12 flex-col items-center justify-center gap-1 rounded-card bg-obsidian-3 text-xs font-semibold text-ink" : "flex min-h-12 flex-col items-center justify-center gap-1 rounded-card text-xs font-semibold text-muted",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: attention ? "text-yellow" : selected ? "text-red" : "text-muted",
			children: icon
		}), label]
	});
}
var SplitComponent = Studio;
//#endregion
export { SplitComponent as component };
