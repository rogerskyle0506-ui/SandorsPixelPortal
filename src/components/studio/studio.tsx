import { useEffect, type ReactNode } from "react";
import * as Slider from "@radix-ui/react-slider";
import {
  Cast,
  ChevronDown,
  Download,
  Pause,
  Play,
  Radio,
  SkipForward,
  Smartphone,
  Square,
  Tv,
  Volume2,
  Wifi,
} from "lucide-react";
import { ROOM, useCast, type Device, type Quality } from "@/lib/cast-store";
import { sizeFor, StageLoop, surfaces } from "@/components/studio/stage";

const KEYS = ["1", "2", "3", "4", "5", "6", "7", "8", "9"] as const;

export function Studio() {
  const tab = useCast((s) => s.tab);
  const phase = useCast((s) => s.phase);
  const quality = useCast((s) => s.quality);
  const setLatency = useCast((s) => s.setLatency);

  useEffect(() => {
    try {
      const saved = localStorage.getItem("sandors-last");
      if (saved) useCast.setState({ savedId: saved });
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    if (phase !== "casting") return;
    const id = window.setInterval(() => {
      setLatency(18 + Math.round(Math.random() * 22));
    }, 900);
    return () => window.clearInterval(id);
  }, [phase, setLatency]);

  const session =
    phase === "connecting" ||
    phase === "need_pin" ||
    phase === "consent" ||
    phase === "casting" ||
    phase === "paused";
  const pinFirst = phase === "connecting" || phase === "need_pin" || phase === "consent";

  return (
    <div className="min-h-screen bg-obsidian text-ink">
      <StageLoop active={phase === "casting"} paused={phase === "paused"} quality={quality} />
      <Header />
      <main className="mx-auto grid w-full max-w-6xl gap-4 px-4 pb-32 lg:grid-cols-2 lg:px-6 lg:pb-6">
        <section
          className={
            tab === "guide"
              ? "hidden"
              : session
                ? pinFirst
                  ? "order-2 block lg:order-1"
                  : "order-1 block"
                : tab === "stick"
                  ? "hidden lg:block"
                  : "block"
          }
        >
          <PixelPanel />
        </section>
        <section
          className={
            tab === "guide"
              ? "hidden"
              : session
                ? pinFirst
                  ? "order-1 block lg:order-2"
                  : "order-2 block"
                : tab === "pixel"
                  ? "hidden lg:block"
                  : "block"
          }
        >
          <StickPanel />
        </section>
        <section className={tab === "guide" ? "lg:col-span-2" : "hidden"}>
          <GuidePanel />
        </section>
      </main>
      <Shade />
      <nav className="z-30 border-t border-line bg-obsidian max-lg:fixed max-lg:inset-x-0 max-lg:bottom-0 lg:border-t-0">
        <div className="mx-auto grid max-w-6xl grid-cols-3 gap-2 px-3 py-2 lg:px-6">
          <TabButton id="pixel" label="Pixel" icon={<Smartphone className="size-5" />} />
          <TabButton id="stick" label="Fire Stick" icon={<Tv className="size-5" />} />
          <TabButton id="guide" label="Build" icon={<Download className="size-5" />} />
        </div>
      </nav>
    </div>
  );
}

function Header() {
  const toggleShade = useCast((s) => s.toggleShade);
  const phase = useCast((s) => s.phase);
  const live = phase === "casting" || phase === "paused";
  return (
    <header className="mx-auto flex w-full max-w-6xl items-center justify-between gap-3 px-4 py-5 lg:px-6">
      <div>
        <p className="font-display text-xs font-bold tracking-widest text-red">SANDORS</p>
        <h1 className="font-display text-3xl font-bold leading-none">Mirrorcast</h1>
      </div>
      <button
        type="button"
        onClick={() => toggleShade(true)}
        aria-label="Quick settings"
        className="flex min-h-11 items-center gap-2 rounded-full border border-line bg-obsidian-2 px-4 text-sm font-semibold text-ink"
      >
        <span className={live ? "size-2 rounded-full bg-red" : "size-2 rounded-full bg-blue"} />
        Tiles
        <ChevronDown className="size-4 text-muted" />
      </button>
    </header>
  );
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

  useEffect(() => {
    if (phase === "need_pin" && entry.length === 4) commitPin();
  }, [phase, entry, commitPin]);

  useEffect(() => {
    if (!shake) return;
    const id = window.setTimeout(clearShake, 400);
    return () => window.clearTimeout(id);
  }, [shake, clearShake]);

  const saved = ROOM.find((device) => device.id === savedId) ?? null;
  const pairing = phase === "connecting" || phase === "need_pin";
  const live = phase === "casting" || phase === "paused";

  return (
    <div className="flex flex-col gap-4 overflow-hidden rounded-card border border-line bg-obsidian-2 p-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-muted">Pixel 10 · sender</p>
        <p className="text-sm text-blue">
          {phase === "scanning"
            ? "Scanning"
            : phase === "connecting"
              ? "Connecting"
              : phase === "need_pin"
                ? "Enter code"
                : phase === "consent"
                  ? "Allow capture"
                  : phase === "paused"
                    ? "Paused"
                    : phase === "casting"
                      ? "On air"
                      : "Idle"}
        </p>
      </div>

      {!pairing && !live && phase !== "consent" && (
        <>
          <p className="text-base leading-relaxed text-ink">
            Find the Fire Stick, confirm the code on the TV, then leave the app. The cast stays up.
          </p>
          <button
            type="button"
            onClick={scan}
            disabled={phase === "scanning"}
            className="min-h-12 rounded-card bg-red px-4 text-base font-semibold text-obsidian disabled:opacity-60"
          >
            {phase === "scanning" ? "Listening for Sticks…" : "Scan the room"}
          </button>
          {saved && phase !== "scanning" && (
            <DeviceRow device={saved} action="Reconnect" saved onSelect={select} />
          )}
          <div className="flex flex-col gap-2">
            {devices.map((device) => (
              <DeviceRow key={device.id} device={device} action="Pair" saved={device.id === savedId} onSelect={select} />
            ))}
            {phase !== "scanning" && devices.length === 0 && !saved && (
              <p className="text-sm text-muted">Sticks on this Wi-Fi show up here. This preview uses two rehearsal Sticks.</p>
            )}
          </div>
          {error && <p className="text-sm text-yellow">{error}</p>}
        </>
      )}

      {pairing && (
        <div className={shake ? "sandors-shake" : ""}>
          <p className="text-base text-ink">
            {phase === "connecting"
              ? `Reaching ${target?.place ?? "the Stick"}…`
              : `Type the 4-digit code on the Fire Stick.`}
          </p>
          <div className="mt-4 grid grid-cols-4 gap-2">
            {[0, 1, 2, 3].map((index) => (
              <div
                key={index}
                className="flex h-16 items-center justify-center rounded-card bg-obsidian font-display text-3xl font-bold text-yellow"
              >
                {entry[index] ?? ""}
              </div>
            ))}
          </div>
          {error && <p className="mt-3 text-sm text-yellow">{error}</p>}
          <div className="mt-4 grid grid-cols-3 gap-2">
            {KEYS.map((key) => (
              <button
                key={key}
                type="button"
                disabled={phase !== "need_pin"}
                onClick={() => press(key)}
                className="min-h-12 rounded-card bg-obsidian text-xl font-semibold text-ink disabled:opacity-30"
              >
                {key}
              </button>
            ))}
            <span aria-hidden="true" />
            <button
              type="button"
              disabled={phase !== "need_pin"}
              onClick={() => press("0")}
              className="min-h-12 rounded-card bg-obsidian text-xl font-semibold text-ink disabled:opacity-30"
            >
              0
            </button>
            <button
              type="button"
              disabled={phase !== "need_pin"}
              onClick={() => press("back")}
              className="min-h-12 rounded-card bg-obsidian text-base font-semibold text-ink disabled:opacity-30"
            >
              Del
            </button>
          </div>
          <button
            type="button"
            onClick={cancelPair}
            className="mt-3 min-h-12 w-full rounded-card border border-line text-base font-semibold text-ink"
          >
            Cancel
          </button>
        </div>
      )}

      {phase === "consent" && target && (
        <div className="flex flex-col gap-3">
          <p className="text-sm font-semibold tracking-wide text-blue">Screen capture</p>
          <p className="font-display text-2xl font-bold">Share this Pixel with {target.place}?</p>
          <p className="text-base leading-relaxed text-muted">
            Sandors will record everything on the screen, including notifications, and the system audio that apps allow.
          </p>
          <button
            type="button"
            onClick={allowCapture}
            className="min-h-12 rounded-card bg-red px-4 text-base font-semibold text-obsidian"
          >
            Start now
          </button>
          <button
            type="button"
            onClick={denyCapture}
            className="min-h-12 rounded-card border border-line text-base font-semibold text-ink"
          >
            Don't allow
          </button>
        </div>
      )}

      {live && target && (
        <div className="flex flex-col gap-4">
          <div>
            <p className="font-display text-2xl font-bold">
              {phase === "paused" ? "Paused" : `Casting to ${target.place}`}
            </p>
            <p className="mt-1 text-sm text-muted">{target.name} · {target.host}</p>
          </div>
          <div className="grid grid-cols-3 gap-2 text-center">
            <Stat label="Latency" value={phase === "paused" ? "Held" : `${latencyMs} ms`} />
            <Stat label="Bitrate" value={bitrate()} />
            <Stat label="Picture" value={quality} />
          </div>
          <canvas
            ref={(node) => {
              surfaces.source = node;
            }}
            width={960}
            height={540}
            className="aspect-video w-full rounded-card bg-obsidian"
          />
          <label className="flex items-center gap-3 text-sm text-muted">
            <Volume2 className="size-5 text-blue" />
            <Slider.Root
              value={[volume]}
              max={100}
              step={1}
              onValueChange={(value) => useCast.getState().setVolume(value[0] ?? 0)}
              className="relative flex h-11 min-w-0 flex-1 touch-none items-center"
            >
              <Slider.Track className="relative h-1.5 grow rounded-full bg-line">
                <Slider.Range className="absolute h-full rounded-full bg-blue" />
              </Slider.Track>
              <Slider.Thumb
                className="flex size-11 items-center justify-center rounded-full bg-transparent outline-none"
                aria-label="Cast volume"
              >
                <span className="block size-5 rounded-full border border-line bg-ink shadow" />
              </Slider.Thumb>
            </Slider.Root>
            <span className="w-8 text-right text-ink">{volume}</span>
          </label>
          <NotificationCard />
        </div>
      )}
    </div>
  );
}

function DeviceRow({
  device,
  action,
  saved,
  onSelect,
}: {
  device: Device;
  action: string;
  saved: boolean;
  onSelect: (device: Device) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onSelect(device)}
      className="flex min-h-16 w-full items-center justify-between gap-3 rounded-card border border-line bg-obsidian px-4 py-3 text-left"
    >
      <span>
        <span className="block font-semibold text-ink">{device.place}</span>
        <span className="block text-sm text-muted">
          {device.name}
          {saved ? " · saved" : ""} · {device.host}
        </span>
      </span>
      <span className="font-semibold text-blue">{action}</span>
    </button>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-card bg-obsidian px-2 py-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 text-sm font-semibold text-ink">{value}</p>
    </div>
  );
}

function NotificationCard() {
  const phase = useCast((s) => s.phase);
  const quality = useCast((s) => s.quality);
  const togglePlay = useCast((s) => s.togglePlay);
  const nextQuality = useCast((s) => s.nextQuality);
  const stop = useCast((s) => s.stop);
  const playing = phase === "casting";
  return (
    <div className="rounded-card bg-obsidian p-3">
      <div className="flex items-center gap-3">
        <span className="flex size-11 items-center justify-center rounded-2xl bg-red text-obsidian">
          <Cast className="size-5" />
        </span>
        <span>
          <span className="block text-sm font-semibold">Sandors Mirrorcast</span>
          <span className="block text-sm text-muted">{playing ? `Casting · ${quality}` : `Paused · ${quality}`}</span>
        </span>
      </div>
      <div className="mt-3 grid grid-cols-3 gap-2">
        <IconButton label={playing ? "Pause" : "Play"} onClick={togglePlay}>
          {playing ? <Pause className="size-5" /> : <Play className="size-5" />}
        </IconButton>
        <IconButton label="Next quality" onClick={nextQuality}>
          <SkipForward className="size-5" />
        </IconButton>
        <IconButton label="Stop cast" onClick={stop}>
          <Square className="size-5" />
        </IconButton>
      </div>
    </div>
  );
}

function IconButton({
  label,
  onClick,
  children,
}: {
  label: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="flex min-h-12 flex-col items-center justify-center gap-1 rounded-card bg-obsidian-3 text-xs font-semibold text-ink"
    >
      {children}
      {label}
    </button>
  );
}

function StickPanel() {
  const phase = useCast((s) => s.phase);
  const pin = useCast((s) => s.pin);
  const target = useCast((s) => s.target);
  const quality = useCast((s) => s.quality);
  const live = phase === "casting" || phase === "paused";
  const size = sizeFor(quality);

  useEffect(() => {
    const tv = surfaces.tv;
    if (!tv) return;
    if (tv.width !== size.width || tv.height !== size.height) {
      tv.width = size.width;
      tv.height = size.height;
    }
  }, [size.width, size.height, live]);

  return (
    <div className={`flex flex-col overflow-hidden rounded-card border border-line bg-obsidian-3 ${live ? "min-h-[28rem]" : "min-h-[16rem]"}`}>
      <div className="flex items-center justify-between px-4 py-3">
        <p className="text-sm font-semibold text-muted">Fire Stick · receiver</p>
        <p className="flex items-center gap-2 text-sm text-yellow">
          <Radio className={phase === "idle" || phase === "scanning" ? "size-4 text-blue" : "sandors-pulse size-4 text-red"} />
          {target?.name ?? "Sandors-A1F3"}
        </p>
      </div>
      <div className="relative mx-4 mb-4 flex flex-1 flex-col items-center justify-center overflow-hidden rounded-card bg-obsidian">
        {live ? (
          <canvas
            ref={(node) => {
              surfaces.tv = node;
            }}
            width={size.width}
            height={size.height}
            className="aspect-video w-full"
          />
        ) : phase === "need_pin" ? (
          <div className="flex flex-col items-center px-6 py-10 text-center">
            <p className="text-sm font-bold tracking-widest text-blue">ENTER THIS CODE ON YOUR PIXEL</p>
            <p className="mt-4 font-display text-5xl font-bold text-yellow">{pin}</p>
            <p className="mt-4 text-sm text-muted">Three tries, then the Stick closes the socket.</p>
          </div>
        ) : phase === "consent" ? (
          <div className="flex flex-col items-center px-6 py-10 text-center">
            <p className="text-sm font-bold tracking-widest text-yellow">PAIRED</p>
            <p className="mt-4 font-display text-3xl font-bold">Waiting for the Pixel</p>
            <p className="mt-3 max-w-sm text-sm leading-relaxed text-muted">
              The phone still has to allow screen capture. Nothing is on the Stick until it does.
            </p>
          </div>
        ) : phase === "connecting" ? (
          <p className="px-6 py-16 text-center text-base text-ink">A Pixel is asking to pair.</p>
        ) : (
          <div className="flex flex-col items-center px-6 py-16 text-center">
            <p className="font-display text-3xl font-bold">Waiting for a Pixel</p>
            <p className="mt-3 max-w-sm text-sm leading-relaxed text-muted">
              Advertising sandorscast on the LAN. The code appears here when a phone connects.
            </p>
          </div>
        )}
        {live && (
          <p className="absolute top-3 right-3 rounded-full bg-obsidian/80 px-3 py-1 text-sm font-semibold text-yellow">
            {phase === "paused" ? "Paused" : `Live · ${quality}`}
          </p>
        )}
      </div>
    </div>
  );
}

function GuidePanel() {
  return (
    <div className="flex flex-col gap-5 rounded-card border border-line bg-obsidian-2 p-5">
      <div>
        <p className="font-display text-xs font-bold tracking-widest text-yellow">SOURCE</p>
        <h2 className="mt-2 font-display text-3xl font-bold">Put it on the devices</h2>
        <p className="mt-3 max-w-2xl text-base leading-relaxed text-muted">
          This studio rehearses discovery, the 4-digit handshake, the screen-capture prompt, and the cast controls. The Pixel
          cannot capture its screen from the browser. The download is the two Android projects that do.
        </p>
      </div>
      <a
        href="/downloads/sandors-mirrorcast-source.zip"
        download
        className="flex min-h-12 items-center justify-center gap-2 rounded-card bg-yellow px-4 text-base font-semibold text-obsidian"
      >
        <Download className="size-5" />
        Download both projects
      </a>
      <ol className="flex flex-col gap-3">
        <Step n="1" title="Unzip two apps" body="SandorsSender for the Pixel 10. SandorsReceiver for the Fire Stick on Fire OS 7." />
        <Step n="2" title="Open each in Android Studio" body="Let Gradle sync. Studio needs JDK 17 and Android SDK 35. It writes the SDK path itself." />
        <Step n="3" title="Sideload the Stick" body="Turn on ADB debugging, then install the receiver APK. Open it once so it can listen after a reboot." />
        <Step n="4" title="Add the Sandors tile" body="Scan, type the code on the TV, accept capture. Pause, next quality, volume, and stop live in the media notification." />
      </ol>
      <div className="grid gap-3 sm:grid-cols-2">
        <Fact title="Picture" body="MediaProjection into a Baseline H.264 encoder. No B-frames. SPS and PPS ride every keyframe." />
        <Fact title="Sound" body="Playback capture as 48 kHz stereo PCM. No microphone permission." />
        <Fact title="Network" body="mDNS type _sandorscast._tcp, a TCP control line, and UDP for video and audio." />
        <Fact title="Stays awake" body="Foreground service, partial wake lock, and a low-latency Wi-Fi lock while the phone screen is off." />
      </div>
    </div>
  );
}

function Step({ n, title, body }: { n: string; title: string; body: string }) {
  return (
    <li className="flex gap-3">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-red text-sm font-bold text-obsidian">
        {n}
      </span>
      <span>
        <span className="block font-semibold">{title}</span>
        <span className="block text-sm leading-relaxed text-muted">{body}</span>
      </span>
    </li>
  );
}

function Fact({ title, body }: { title: string; body: string }) {
  return (
    <div className="rounded-card bg-obsidian p-4">
      <p className="font-semibold text-blue">{title}</p>
      <p className="mt-1 text-sm leading-relaxed text-muted">{body}</p>
    </div>
  );
}

function Shade() {
  const shade = useCast((s) => s.shade);
  const phase = useCast((s) => s.phase);
  const toggleShade = useCast((s) => s.toggleShade);
  const tileTap = useCast((s) => s.tileTap);
  if (!shade) return null;
  const active = phase === "casting" || phase === "paused";
  return (
    <div className="fixed inset-0 z-40 flex flex-col bg-obsidian/80">
      <button type="button" aria-label="Close quick settings" className="min-h-16 flex-1" onClick={() => toggleShade(false)} />
      <div className="rounded-t-card border border-line bg-obsidian-2 p-4 pb-8">
        <div className="mb-4 flex items-center justify-between text-sm text-muted">
          <span className="flex items-center gap-2">
            <Wifi className="size-4 text-blue" />
            Home Wi-Fi
          </span>
          <span>Quick settings</span>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={tileTap}
            className={
              active
                ? "flex min-h-24 flex-col items-start justify-between rounded-card bg-red p-4 text-left text-obsidian"
                : "flex min-h-24 flex-col items-start justify-between rounded-card bg-obsidian p-4 text-left text-ink"
            }
          >
            <Cast className="size-6" />
            <span>
              <span className="block font-semibold">Sandors</span>
              <span className="block text-sm">{active ? "Casting" : "Mirrorcast"}</span>
            </span>
          </button>
          <div className="flex min-h-24 flex-col justify-between rounded-card border border-line p-4">
            <Wifi className="size-6 text-blue" />
            <span>
              <span className="block font-semibold">Same network</span>
              <span className="block text-sm text-muted">Multicast has to be allowed</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

function TabButton({ id, label, icon }: { id: "pixel" | "stick" | "guide"; label: string; icon: ReactNode }) {
  const tab = useCast((s) => s.tab);
  const setTab = useCast((s) => s.setTab);
  const phase = useCast((s) => s.phase);
  const attention = id === "stick" && (phase === "need_pin" || phase === "connecting" || phase === "consent");
  const selected = tab === id;
  return (
    <button
      type="button"
      onClick={() => setTab(id)}
      className={
        selected
          ? "flex min-h-12 flex-col items-center justify-center gap-1 rounded-card bg-obsidian-3 text-xs font-semibold text-ink"
          : "flex min-h-12 flex-col items-center justify-center gap-1 rounded-card text-xs font-semibold text-muted"
      }
    >
      <span className={attention ? "text-yellow" : selected ? "text-red" : "text-muted"}>{icon}</span>
      {label}
    </button>
  );
}
