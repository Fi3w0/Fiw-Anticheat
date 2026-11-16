#!/usr/bin/env python3
"""Boot the production jar twice in a disposable server, including real runtime dependencies."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import time
import urllib.parse
import urllib.request


ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "build" / "smoke-downloads"


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if line.strip() and not line.lstrip().startswith("#") and "=" in line)


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "Fiw-Mods-Smoke-Test/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def download(url, destination):
    CACHE.mkdir(parents=True, exist_ok=True)
    cached = CACHE / hashlib.sha256(url.encode()).hexdigest()
    if not cached.exists():
        data = fetch(url)
        cached.write_bytes(data)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(cached, destination)


def java_for(version):
    for key in [f"JAVA_{version}", f"JAVA_HOME_{version}_X64", f"JAVA_HOME_{version}_ARM64", f"JAVA_HOME_{version}"]:
        value = os.environ.get(key)
        if value:
            executable = Path(value)
            if executable.is_dir():
                executable /= "bin/java"
            return str(executable)
    mac = Path(f"/Library/Java/JavaVirtualMachines/temurin-{version}.jdk/Contents/Home/bin/java")
    return str(mac) if mac.exists() else shutil.which("java")


def kotlin_dependency(work, mc, loader, required):
    query = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps([loader])})
    versions = json.loads(fetch(f"https://api.modrinth.com/v2/project/kotlin-for-forge/version?{query}"))
    matches = [v for v in versions if v["version_number"].lstrip("v") == required]
    if not matches:
        # Maven compile releases are not always published as standalone Modrinth distributions.
        # Use a newer compatible patch from the same language-provider major, tagged for this game.
        key = lambda value: tuple(int(part) for part in value.lstrip("v").split("."))
        matches = [v for v in versions if v["version_number"].lstrip("v").split(".")[0] == required.split(".")[0]
                   and key(v["version_number"]) >= key(required)]
    if not matches:
        raise RuntimeError(f"No compatible Kotlin for Forge {required}+ distribution for {loader} {mc}")
    print(f"Runtime Kotlin for Forge {matches[0]['version_number']} for {mc}", flush=True)
    artifact = next(f for f in matches[0]["files"] if f["primary"])
    download(artifact["url"], work / "mods" / artifact["filename"])


def player_session(script, port, mc, phase, work, server):
    with (work / f"player-{phase}.log").open("w") as output:
        bot = subprocess.Popen(["node", str(script), str(port), mc, phase, str(work)],
                               cwd=work, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        try:
            for line in bot.stdout:
                output.write(line)
                output.flush()
                try:
                    event = json.loads(line)
                except ValueError:
                    continue
                if "console" in event:
                    server.stdin.write(event["console"] + "\n")
                    server.stdin.flush()
                else:
                    print(json.dumps(event), flush=True)
            if bot.wait(timeout=10):
                raise RuntimeError(f"Player assertions failed: {work / f'player-{phase}.log'}")
        finally:
            if bot.poll() is None:
                bot.kill()
                bot.wait()


def known_noise():
    """Log lines that look like errors but are verified harmless. Keep each pattern specific.

    NeoForge 1.21.11 on Linux: its debug log appender fails to render Netty's probe of the
    macOS/BSD-only kqueue transport and prints that stack trace ("Caused by: ... kqueue",
    "Only supported on OSX/BSD"). The server is unaffected. Seen on GitHub runners, not on macOS.
    A repository can add its own patterns, one regex per line, in .github/boot/known-noise.txt.
    """
    patterns = [r"io\.netty\.channel\.kqueue", r"Only supported on OSX/BSD"]
    extra = ROOT / ".github" / "boot" / "known-noise.txt"
    if extra.exists():
        patterns += [line.strip() for line in extra.read_text().splitlines()
                     if line.strip() and not line.lstrip().startswith("#")]
    return re.compile("|".join(f"(?:{p})" for p in patterns))


def run_session(command, work, session, commands, expected, mc, script=None, port=None, after_script=None):
    log = work / f"{session}.log"
    with log.open("w") as output:
        process = subprocess.Popen(command, cwd=work, stdin=subprocess.PIPE,
                                   stdout=output, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + int(os.environ.get("BOOT_TIMEOUT", "300"))
            while "Done (" not in log.read_text(errors="replace"):
                if process.poll() is not None:
                    raise RuntimeError(f"Server exited before Done; see {log}")
                if time.monotonic() >= deadline:
                    raise RuntimeError(f"Server startup timed out; see {log}")
                time.sleep(0.5)
            for line in commands:
                process.stdin.write(line + "\n")
                process.stdin.flush()
                time.sleep(1)
            if script:
                player_session(script, port, mc, session, work, process)
            if after_script and session == "verify":
                player_session(after_script, port, mc, session + "-integration", work, process)
            process.stdin.write("save-all flush\nstop\n")
            process.stdin.flush()
            if process.wait(timeout=90):
                raise RuntimeError(f"Server exited with {process.returncode}; see {log}")
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            process.stdin.close()
            snapshots = work / "snapshots" / session
            for saved in (work / "world").rglob("*.dat"):
                if "playerdata" in saved.parts or "data" in saved.parts:
                    destination = snapshots / saved.relative_to(work / "world")
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(saved, destination)
    text = log.read_text(errors="replace")
    if f"Starting minecraft server version {mc}" not in text:
        raise RuntimeError(f"Wrong Minecraft version; see {log}")
    for token in expected:
        if token not in text:
            raise RuntimeError(f"Missing command response {token!r}; see {log}")
    failures = [line for line in text.splitlines()
                if re.search(r"(?:/ERROR\]|\[ERROR\]|FATAL|Caused by:|MixinApplyError|InvalidMixinException)", line)
                and not known_noise().search(line)]
    if failures:
        raise RuntimeError(f"Errors in {log}:\n" + "\n".join(failures[:20]))
    if list((work / "crash-reports").glob("*")):
        raise RuntimeError(f"Crash report written in {work}")
    print(f"PASS {session}: {mc}; {log}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("module")
    parser.add_argument("jar", type=Path)
    parser.add_argument("--suite-file", type=Path)
    parser.add_argument("--player-script", type=Path)
    parser.add_argument("--after-player-script", type=Path)
    parser.add_argument("--player-phases", default="create,partial,collect,verify,hazards,hazards-restart")
    parser.add_argument("--extra-jar", action="append", default=[], type=Path)
    parser.add_argument("--command", action="append", default=[])
    parser.add_argument("--expect", action="append", default=[])
    args = parser.parse_args()
    if args.suite_file:
        suite = json.loads(args.suite_file.read_text())
        args.extra_jar += [Path(p) for p in suite.get("jars", [])]
        if suite.get("player_script"):
            args.player_script = Path(suite["player_script"])
        if suite.get("after_player_script"):
            args.after_player_script = Path(suite["after_player_script"]).resolve(strict=True)
        args.player_phases = suite.get("player_phases", args.player_phases)
        args.command += suite.get("commands", [])
        args.expect += suite.get("expected", [])
    target = ROOT / args.module
    if target.parent != ROOT or not target.is_dir():
        parser.error("module must be an immediate project directory")
    props = properties(ROOT / "gradle.properties") | properties(target / "gradle.properties")
    mc = props["minecraft_version"]
    loader = args.module.split("-", 1)[0]
    java = java_for("25" if mc.startswith("26.") else "17" if mc == "1.20.1" else "21")
    work = ROOT / "build" / "smoke" / args.module
    if work.exists():
        shutil.rmtree(work)
    (work / "mods").mkdir(parents=True)
    for artifact in [args.jar, *args.extra_jar]:
        shutil.copyfile(artifact.resolve(strict=True), work / "mods" / artifact.name)
    if loader == "fabric":
        version = props.get("fabric_loader_version", props.get("loader_version"))
        installer = json.loads(fetch("https://meta.fabricmc.net/v2/versions/installer"))[0]["version"]
        download(f"https://meta.fabricmc.net/v2/versions/loader/{mc}/{version}/{installer}/server/jar", work / "server.jar")
        api = props.get("fabric_api_version", props.get("fabric_version"))
        download(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar", work / "mods/fabric-api.jar")
        if props.get("fabric_kotlin_version"):
            kotlin = props["fabric_kotlin_version"]
            download(f"https://maven.fabricmc.net/net/fabricmc/fabric-language-kotlin/{kotlin}/fabric-language-kotlin-{kotlin}.jar", work / "mods/fabric-language-kotlin.jar")
        command = [java, "-Xmx1G", "-jar", "server.jar", "nogui"]
    else:
        version = props["neoforge_version"] if loader == "neoforge" else mc + "-" + props["forge_version"]
        base = "https://maven.neoforged.net/releases/net/neoforged/neoforge" if loader == "neoforge" else "https://maven.minecraftforge.net/net/minecraftforge/forge"
        download(f"{base}/{version}/{loader}-{version}-installer.jar", work / "installer.jar")
        with (work / "installer.log").open("w") as output:
            subprocess.run([java, "-jar", "installer.jar", "--installServer", "."], cwd=work,
                           stdout=output, stderr=subprocess.STDOUT, check=True, timeout=300)
        if props.get("kotlin_for_forge_version"):
            kotlin_dependency(work, mc, loader, props["kotlin_for_forge_version"])
        group = "net/neoforged/neoforge" if loader == "neoforge" else "net/minecraftforge/forge"
        command = [java, "-Xmx1G", f"@libraries/{group}/{version}/unix_args.txt", "nogui"]
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    if args.player_script:
        args.player_script = args.player_script.resolve(strict=True)
        config = work / "config/fiwgraves"
        config.mkdir(parents=True, exist_ok=True)
        (config / "config.json").write_text(json.dumps({"despawnSeconds": -1, "playerGraveTeleport": True,
                                                       "autoRotateEnabled": False}))
    (work / "eula.txt").write_text("eula=true\n")
    (work / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n"
        "generate-structures=false\nallow-flight=true\npause-when-empty-seconds=-1\nmax-tick-time=-1\n"
        # Minecraft 26.3 turns the whitelist on by default; test clients must be able to join.
        "white-list=false\n"
        # A fixed world makes every failure reproducible instead of depending on random terrain.
        "level-seed=fiw-production-verification\n")
    checks = json.loads((ROOT / ".github/boot/smoke.json").read_text())
    commands = checks["commands"] + args.command
    expected = checks["expected"] + args.expect
    phases = args.player_phases.split(",") if args.player_script else ["first", "restart"]
    for session in phases:
        run_session(command, work, session, commands, expected, mc, args.player_script, port, args.after_player_script)
    print(f"PRODUCTION SMOKE PASSED: {args.module}", flush=True)


if __name__ == "__main__":
    main()
