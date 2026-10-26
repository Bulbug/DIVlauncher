#!/usr/bin/env python3
"""Repack plain per-architecture Android JRE tarballs into the layout DIVlauncher bundles.

The app expects, per runtime folder under assets/components/:
  universal.tar.xz   files that are identical on every architecture
  bin-<arch>.tar.xz  everything else for that architecture (arm, arm64, x86, x86_64)
  version            a string that changes whenever the runtime changes

Usage: repack_jre.py OUT_DIR VERSION_LABEL arm=a.tar.xz arm64=b.tar.xz x86=c.tar.xz x86_64=d.tar.xz
       [--only-arch arm64]   only write bin-<arch> for that architecture (smaller APK)
"""
import hashlib, os, shutil, subprocess, sys, tarfile, tempfile

ARCHES = ["arm", "arm64", "x86", "x86_64"]


def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fingerprint(root):
    """relative path -> ('d',) | ('l', target) | ('f', sha256, executable)"""
    out = {}
    for base, dirs, files in os.walk(root):
        for name in dirs + files:
            full = os.path.join(base, name)
            rel = os.path.relpath(full, root)
            if os.path.islink(full):
                out[rel] = ("l", os.readlink(full))
            elif os.path.isdir(full):
                out[rel] = ("d",)
            else:
                out[rel] = ("f", sha(full), os.access(full, os.X_OK))
    return out


def write_tar(dest, root, rels):
    def reset(ti):
        ti.uid = ti.gid = 0
        ti.uname = ti.gname = ""
        return ti
    with tarfile.open(dest, "w:xz", preset=6) as tf:
        for rel in sorted(rels):
            tf.add(os.path.join(root, rel), arcname=rel, recursive=False, filter=reset)


def main():
    args = [a for a in sys.argv[1:]]
    only = None
    if "--only-arch" in args:
        i = args.index("--only-arch"); only = args[i + 1]; del args[i:i + 2]
    out_dir, label = args[0], args[1]
    tars = dict(a.split("=", 1) for a in args[2:])
    missing = [a for a in ARCHES if a not in tars]
    if missing:
        sys.exit("missing architectures: %s" % missing)
    os.makedirs(out_dir, exist_ok=True)
    work = tempfile.mkdtemp(prefix="repack_")
    try:
        roots, prints = {}, {}
        for arch in ARCHES:
            roots[arch] = os.path.join(work, arch)
            os.makedirs(roots[arch])
            subprocess.check_call(["tar", "-xJf", tars[arch], "-C", roots[arch]])
            prints[arch] = fingerprint(roots[arch])
            print("%-7s %6d entries" % (arch, len(prints[arch])), flush=True)
        common = {rel for rel, fp in prints[ARCHES[0]].items()
                  if all(prints[a].get(rel) == fp for a in ARCHES[1:])}
        # executables with the same bytes on every arch are still fine as "universal"
        write_tar(os.path.join(out_dir, "universal.tar.xz"), roots[ARCHES[0]], common)
        for arch in ARCHES:
            if only and arch != only:
                continue
            rest = set(prints[arch]) - common
            # keep parent directories so extraction never depends on tar ordering
            parents = {os.path.dirname(r) for r in rest}
            while parents - {""}:
                parents = {p for p in parents if p}
                rest |= parents
                parents = {os.path.dirname(p) for p in parents}
            write_tar(os.path.join(out_dir, "bin-%s.tar.xz" % arch), roots[arch], rest)
        with open(os.path.join(out_dir, "version"), "w") as f:
            f.write(label)
        print("common entries: %d" % len(common))
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
