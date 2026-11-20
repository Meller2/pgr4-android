#!/usr/bin/env python3
"""Launch the base-disc Palace Square test race on an attached Android device."""

import argparse
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import re
import subprocess
from time import monotonic, sleep

from PIL import Image


PACKAGE = "dev.pgr4port.android"
ACTIVITY = f"{PACKAGE}/.GameActivity"
WIDTH, HEIGHT = 2376, 1080
CHANNEL = {"red": 0, "green": 1, "blue": 2}


@dataclass(frozen=True)
class Prompt:
    bounds: tuple[int, int, int, int]
    color: str
    mask: int

    def score(self, frame):
        # Map the reference 16:9 game viewport to the device's screen.
        scale = frame.height / HEIGHT
        x1, y1, x2, y2 = self.bounds
        crop = (
            round((x1 - WIDTH / 2) * scale + frame.width / 2),
            round(y1 * scale),
            round((x2 - WIDTH / 2) * scale + frame.width / 2),
            round(y2 * scale),
        )
        pixels = frame.crop(crop).resize((40, 12), Image.Resampling.BILINEAR)
        c = CHANNEL[self.color]
        actual = 0
        raw = pixels.tobytes()
        for index in range(0, len(raw), 3):
            r, g, b = raw[index:index + 3]
            rgb = (r, g, b)
            foreground = rgb[c] >= 65 and rgb[c] - max(rgb[(c + 1) % 3], rgb[(c + 2) % 3]) >= 22
            actual = (actual << 1) | foreground
        return 2 * (self.mask & actual).bit_count() / (self.mask.bit_count() + actual.bit_count())


# Color fingerprints of the English menu labels. No reference images are needed.
PROMPTS = {
    "title": Prompt((704, 830, 1697, 889), "red",
        0x0000000000ffbfff7fffffffffffff87f75afe7dfffffffffffffffffbff9fff67fbedffffe7ffeffffce7fffff57fe6fff5ffffffffffbfbfe77fff),
    "profile": Prompt((1567, 642, 1609, 679), "green",
        0x000000000000000000000003fffc00000fffff80001ffaffc0003ff77fc0003fffffc0001fffffc00007ffff000000fff00000000000000000000000),
    "main": Prompt((510, 805, 1030, 864), "red",
        0x0000000000ffffff9fffffffff9fffb7def3b7dff7fffcb6ff76feffbedf38def3bfd89dfffff7fe9df6fec7de7d307a7fc7fffffbfffffadfd3ef7d),
    "single": Prompt((510, 805, 992, 864), "red",
        0x0000000000ffefffffffffffffffff3b3b2f6ccf3cf3cf6b33efffcfff33e7fecfff33afeadfff373aa2affaabdb7c67f998ffffffffff77f7bdf6ef),
    "chapter": Prompt((510, 807, 1156, 861), "red",
        0x8001030020c6ef9ff0706eef9ff050fffefffffffffffffffffa9ffbb7fefffffefbffefff3efbfbfffffefbef7ffffef99ffffffcfffffffffcff7f),
    "event": Prompt((510, 805, 961, 864), "red",
        0x0000000000fffffffb3fffffffffffa08ee3adc5feceffedff7eeefdffbd366ef59ebfb7aff55abbb7aef578bb7f803d67c8dffffdffff9b7fd9b9ff),
    "palace": Prompt((1203, 632, 1460, 667), "red",
        0x0000000013000000001338c007800344e006beff57fff7ffff56fff58fef5ef7f66f975efbfe6f976afffb92bf67ffe7dffe00000071e40000006000),
    "car": Prompt((1740, 78, 2045, 195), "blue",
        0x0000000000003ffff80003ffffffc01ffff8fff03feffffff87ffffffbfc7ffdf8e7fc3fbb796ffc1f8f7f4ff003ffffffc0003ffff8000000000000),
    "difficulty": Prompt((526, 805, 750, 865), "red",
        0x0000000000ffefcffffcffefdffffe2646f07e63e6c6f67ef2e6c6667c666cc66c7c0c0cfe4cfccc4cff1cfcc6cc0f3c09e6fffbfffffe0000000000),
    "driving": Prompt((330, 258, 430, 303), "red",
        0x01ff00000007ffc000000f10e000001ffc7000001ff8f0000000e0e0700000fc78f8001ffc3fd8001ffc1dd8000f18ddd80007fffff80001fff3f800),
}

STAGES = [
    ("profile", "Loaded profile"),
    ("main", "Single Player"),
    ("single", "Arcade Mode"),
    ("chapter", "Catch Me If You Can"),
    ("event", "Palace Square, Street Race"),
    ("car", "Ford Sierra Cosworth RS500"),
    ("difficulty", "Silver difficulty"),
    ("driving", "Race is ready"),
]


def adb_client(serial):
    command = ["adb", "-s", serial] if serial else ["adb"]

    def adb(*arguments):
        result = subprocess.run([*command, *arguments], capture_output=True, timeout=12)
        if result.returncode:
            detail = (result.stderr or result.stdout).decode(errors="replace").strip()
            raise RuntimeError(f"ADB {arguments[0]} failed: {detail}")
        return result.stdout

    return adb


def quick_race(adb, timeout):
    def foreground():
        focus = adb("shell", "dumpsys", "window", "displays").splitlines()
        activity_names = (ACTIVITY.encode(), f"{PACKAGE}/{PACKAGE}.GameActivity".encode())
        return any(
            b"mCurrentFocus=" in line and any(name in line for name in activity_names)
            for line in focus
        )

    def press(frame, button):
        if not foreground():
            raise RuntimeError("PGR4 left the foreground")
        # The Android touch pad is laid out in dp. Press long enough for a slow frame to read it.
        if button == "Start":
            x, y = frame.width - round(46 * density), round(39 * density)
        else:
            x, y = frame.width - round(40 * density), frame.height - round(135 * density)
        adb("shell", "input", "swipe", *map(str, (x, y, x, y, 260)))

    def prompt_score(frame, key):
        score = PROMPTS[key].score(frame)
        if key == "event" and PROMPTS["palace"].score(frame) < 0.76:
            return 0.0
        threshold = 0.81 if key == "driving" else 0.86
        return score if score >= threshold else 0.0

    density_output = adb("shell", "wm", "density").decode()
    values = re.findall(r"(?:Physical|Override) density:\s*(\d+)", density_output)
    if not values:
        raise RuntimeError("Could not get Android display density")
    density = int(values[-1]) / 160

    started = monotonic()
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)

    stage = 0
    last_press = 0.0
    while monotonic() - started < timeout:
        if not foreground():
            sleep(0.4)
            continue
        with Image.open(BytesIO(adb("exec-out", "screencap", "-p"))) as screenshot:
            if screenshot.width * 9 < screenshot.height * 16:
                sleep(0.4)
                continue
            frame = screenshot.convert("RGB")

        key, label = STAGES[stage]
        score = prompt_score(frame, key)
        if key in ("profile", "main"):
            next_key, next_label = STAGES[stage + 1]
            next_score = prompt_score(frame, next_key)
            # The first menu can already be past Single Player when A closes the profile notice.
            if next_score > score + 0.03:
                stage += 1
                key, label, score = next_key, next_label, next_score
        if score:
            print(f"{monotonic() - started:5.1f}s  {label}", flush=True)
            if key == "driving":
                print(f"Done in {monotonic() - started:.1f} seconds.", flush=True)
                return frame
            press(frame, "A")
            last_press = monotonic()
            stage += 1
        elif stage == 0 and monotonic() - last_press >= 1.2:
            # Start dismisses the intro and advances the title screen.
            if PROMPTS["title"].score(frame) >= 0.85 or monotonic() - started >= 2.0:
                press(frame, "Start")
                last_press = monotonic()
        elif stage > 0 and monotonic() - last_press >= 2.0:
            previous = STAGES[stage - 1][0]
            if prompt_score(frame, previous) >= 0.9:
                press(frame, "A")
                last_press = monotonic()

        sleep(0.1)

    raise RuntimeError(f"Timed out waiting for {STAGES[stage][1]} on the device")


def benchmark(adb, timeout, output, seconds):
    def game_files(*arguments):
        return adb("shell", "run-as", PACKAGE, *arguments)

    def thermal_status():
        report = adb("shell", "dumpsys", "thermalservice").decode(errors="replace")
        status = re.search(r"^Thermal Status: (\d+)", report, re.MULTILINE)
        return status[1] if status else "unavailable"

    def device_time():
        return adb("shell", "date", "+%Y-%m-%dT%H:%M:%S%z").decode().strip()

    def log_names():
        if b"logs" not in game_files("ls", "files").splitlines():
            return set()
        return set(game_files("ls", "files/logs").splitlines())

    if (b"mWakefulness=Awake" not in adb("shell", "dumpsys", "power")
            or b"mDreamingLockscreen=true" in adb("shell", "dumpsys", "window")):
        raise RuntimeError("Wake and unlock the device before benchmarking")

    prior_logs = log_names()
    package = adb("shell", "dumpsys", "package", PACKAGE).decode(errors="replace")
    build = re.findall(r"^\s*(versionName|versionCode|lastUpdateTime)=([^\r\n]+)",
                       package, re.MULTILINE)
    config_name = "pgr4_recompiled.toml"
    config = (game_files("cat", f"files/{config_name}")
              if config_name.encode() in game_files("ls", "files").splitlines() else b"")
    output.mkdir(parents=True)
    (output / "config.toml").write_bytes(config)
    with (output / "run.txt").open("w", encoding="utf-8") as run:
        for key, value in build:
            print(f"{key}={value}", file=run)
        print(f"thermal_before={thermal_status()}", file=run)
        try:
            frame = quick_race(adb, timeout)
            print(f"hud={device_time()}", file=run)
            frame.save(output / "hud.png")
            sleep(seconds)
            print(f"end={device_time()}", file=run)
        finally:
            adb("shell", "am", "force-stop", PACKAGE)
            logs = log_names() - prior_logs
            for name in sorted(logs):
                if re.fullmatch(rb"pgr4_recompiled_\d+\.log", name):
                    filename = name.decode("ascii")
                    (output / filename).write_bytes(game_files("cat", f"files/logs/{filename}"))
            print(f"thermal_after={thermal_status()}", file=run)
    if not list(output.glob("pgr4_recompiled_*.log")):
        raise RuntimeError("Benchmark finished without a new game log")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="ADB device serial if more than one device is attached")
    parser.add_argument("--timeout", type=float, default=120, help="maximum time in seconds")
    parser.add_argument("--benchmark", type=Path, metavar="NEW_DIR",
                        help="record a stationary race sample and close the game")
    parser.add_argument("--sample-seconds", type=int, choices=range(10, 31),
                        metavar="10-30", default=15, help="benchmark time after HUD (default: 15)")
    args = parser.parse_args()
    adb = adb_client(args.serial)
    try:
        if args.benchmark is None:
            quick_race(adb, args.timeout)
        else:
            benchmark(adb, args.timeout, args.benchmark, args.sample_seconds)
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
