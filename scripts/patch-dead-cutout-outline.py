#!/usr/bin/env python3
"""Remove one provably-dead R8 API outline that writes deprecated SHORT_EDGES.

R8 can synthesize an API-model outline containing
WindowManager.LayoutParams.layoutInDisplayCutoutMode = 1 even when the outline has
no callers. Play then reports the dead method as deprecated API usage.

This post-processor is intentionally narrow:
* it requires exactly one const/4(1) + iput to layoutInDisplayCutoutMode,
* it requires zero invoke callers to that method,
* it only NOPs that dead write while preserving code size,
* it repairs the DEX SHA-1 signature and Adler32 checksum,
* it fails closed if the bytecode shape changes.
"""
import argparse
import hashlib
import json
import os
import struct
import zlib
import zipfile

TARGET_CLASS = "Landroid/view/WindowManager$LayoutParams;"
TARGET_FIELD = "layoutInDisplayCutoutMode"
TARGET_TYPE = "I"

def uleb(data, off):
    out = 0
    shift = 0
    while True:
        b = data[off]
        off += 1
        out |= (b & 0x7F) << shift
        if b < 0x80:
            return out, off
        shift += 7
        if shift > 35:
            raise ValueError("Bad ULEB128")

def read_mutf8(data, off):
    _, off = uleb(data, off)
    end = data.index(0, off)
    return data[off:end].decode("utf-8", "replace")

def parse_dex(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("Not a DEX file")
    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 56)
    type_ids_size, type_ids_off = struct.unpack_from("<II", data, 64)
    field_ids_size, field_ids_off = struct.unpack_from("<II", data, 80)
    method_ids_size, method_ids_off = struct.unpack_from("<II", data, 88)
    class_defs_size, class_defs_off = struct.unpack_from("<II", data, 96)

    strings = []
    for i in range(string_ids_size):
        (soff,) = struct.unpack_from("<I", data, string_ids_off + 4 * i)
        strings.append(read_mutf8(data, soff))

    types = []
    for i in range(type_ids_size):
        (sid,) = struct.unpack_from("<I", data, type_ids_off + 4 * i)
        types.append(strings[sid])

    fields = []
    for i in range(field_ids_size):
        class_idx, type_idx, name_idx = struct.unpack_from("<HHI", data, field_ids_off + 8 * i)
        fields.append((types[class_idx], strings[name_idx], types[type_idx]))

    methods = []
    for i in range(method_ids_size):
        class_idx, proto_idx, name_idx = struct.unpack_from("<HHI", data, method_ids_off + 8 * i)
        methods.append((types[class_idx], strings[name_idx], proto_idx))

    method_code = {}
    for i in range(class_defs_size):
        values = struct.unpack_from("<IIIIIIII", data, class_defs_off + 32 * i)
        class_data_off = values[6]
        if not class_data_off:
            continue
        off = class_data_off
        static_fields, off = uleb(data, off)
        instance_fields, off = uleb(data, off)
        direct_methods, off = uleb(data, off)
        virtual_methods, off = uleb(data, off)
        for _ in range(static_fields + instance_fields):
            _, off = uleb(data, off)
            _, off = uleb(data, off)
        method_idx = 0
        for _ in range(direct_methods + virtual_methods):
            diff, off = uleb(data, off)
            method_idx += diff
            _, off = uleb(data, off)
            code_off, off = uleb(data, off)
            if code_off:
                method_code[method_idx] = code_off

    return fields, methods, method_code

def code_units(data, code_off):
    insns_size = struct.unpack_from("<I", data, code_off + 12)[0]
    insn_off = code_off + 16
    units = list(struct.unpack_from("<" + "H" * insns_size, data, insn_off))
    return insn_off, units

def find_writers(data):
    fields, methods, method_code = parse_dex(data)
    target_fields = [
        i for i, field in enumerate(fields)
        if field == (TARGET_CLASS, TARGET_FIELD, TARGET_TYPE)
    ]
    hits = []
    for method_idx, code_off in method_code.items():
        insn_off, units = code_units(data, code_off)
        for pos in range(1, len(units) - 1):
            if (units[pos] & 0xFF) != 0x59:  # iput
                continue
            if units[pos + 1] not in target_fields:
                continue
            previous = units[pos - 1]
            if (previous & 0xFF) != 0x12:  # const/4
                continue
            high = (previous >> 8) & 0xFF
            const_reg = high & 0x0F
            literal = (high >> 4) & 0x0F
            if literal >= 8:
                literal -= 16
            iput_high = (units[pos] >> 8) & 0xFF
            src_reg = iput_high & 0x0F
            obj_reg = (iput_high >> 4) & 0x0F
            if const_reg != src_reg or literal != 1:
                continue
            hits.append({
                "method_idx": method_idx,
                "class": methods[method_idx][0],
                "method": methods[method_idx][1],
                "code_off": code_off,
                "insn_off": insn_off,
                "pos": pos,
                "src_reg": src_reg,
                "obj_reg": obj_reg,
                "field_idx": units[pos + 1],
                "units": units[max(0, pos - 2):min(len(units), pos + 4)],
            })
    return hits, methods, method_code

def callers_of(data, target_method_idx):
    _, methods, method_code = parse_dex(data)
    callers = []
    for method_idx, code_off in method_code.items():
        _, units = code_units(data, code_off)
        for pos in range(len(units) - 1):
            opcode = units[pos] & 0xFF
            if (0x6E <= opcode <= 0x72 or 0x74 <= opcode <= 0x78) and units[pos + 1] == target_method_idx:
                callers.append({
                    "class": methods[method_idx][0],
                    "method": methods[method_idx][1],
                    "position": pos,
                })
    return callers

def patch_dex(data):
    buf = bytearray(data)
    hits, _, _ = find_writers(buf)
    patchable = []
    for hit in hits:
        hit["callers"] = callers_of(buf, hit["method_idx"])
        if not hit["callers"]:
            patchable.append(hit)

    if len(patchable) != 1:
        raise SystemExit(
            f"Expected exactly one dead SHORT_EDGES writer; "
            f"found {len(patchable)} patchable of {len(hits)} total: {hits}"
        )

    hit = patchable[0]
    unit_off = hit["insn_off"] + 2 * (hit["pos"] - 1)
    struct.pack_into("<HHH", buf, unit_off, 0, 0, 0)

    buf[12:32] = hashlib.sha1(buf[32:]).digest()
    struct.pack_into("<I", buf, 8, zlib.adler32(buf[12:]) & 0xFFFFFFFF)

    remaining, _, _ = find_writers(buf)
    if remaining:
        raise SystemExit(f"Target writer still present after patch: {remaining}")
    return bytes(buf), hit

def rewrite_aab(src, dst):
    temp = dst + ".tmp"
    reports = []
    with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(temp, "w", allowZip64=True) as zout:
        for info in zin.infolist():
            payload = zin.read(info.filename)
            if info.filename.startswith("base/dex/") and info.filename.endswith(".dex"):
                hits, _, _ = find_writers(payload)
                if hits:
                    payload, report = patch_dex(payload)
                    report["entry"] = info.filename
                    reports.append(report)
            zout.writestr(info, payload)

    if len(reports) != 1:
        os.remove(temp)
        raise SystemExit(f"Expected one patched DEX method, got {len(reports)}: {reports}")
    os.replace(temp, dst)
    return reports

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("src")
    parser.add_argument("dst")
    args = parser.parse_args()
    print(json.dumps(rewrite_aab(args.src, args.dst), indent=2))

if __name__ == "__main__":
    main()
