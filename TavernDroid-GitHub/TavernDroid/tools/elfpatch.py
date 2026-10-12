#!/usr/bin/env python3
"""Rename DT_NEEDED / DT_SONAME entries of an ELF64 little-endian file in place.

Android only extracts native libraries whose file names match lib*.so, so the
Termux libraries (libz.so.1, libcrypto.so.3, ...) are renamed to names such as
libz1.so. Each new name must be no longer than the old one: the string is
overwritten inside .dynstr and padded with NULs, which also keeps the
version-requirement (VERNEED) file names consistent because they point at the
same string offsets.

usage: elfpatch.py FILE OLD=NEW [OLD=NEW ...]
"""
import struct
import sys

DT_NULL, DT_NEEDED, DT_STRTAB, DT_SONAME, DT_RUNPATH, DT_RPATH = 0, 1, 5, 14, 29, 15
PT_DYNAMIC = 2


def patch(path, renames):
    data = bytearray(open(path, 'rb').read())
    if data[:4] != b'\x7fELF' or data[4] != 2 or data[5] != 1:
        raise SystemExit(f'{path}: not an ELF64 little-endian file')
    e_phoff, = struct.unpack_from('<Q', data, 0x20)
    e_phentsize, e_phnum = struct.unpack_from('<HH', data, 0x36)

    loads, dyn = [], None
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, _flags, p_offset, p_vaddr, _paddr, p_filesz = struct.unpack_from('<IIQQQQ', data, off)
        if p_type == 1:
            loads.append((p_vaddr, p_offset, p_filesz))
        elif p_type == PT_DYNAMIC:
            dyn = (p_offset, p_filesz)
    if dyn is None:
        raise SystemExit(f'{path}: no PT_DYNAMIC')

    def v2o(vaddr):
        for va, off, sz in loads:
            if va <= vaddr < va + sz:
                return vaddr - va + off
        raise SystemExit(f'{path}: vaddr {vaddr:#x} not mapped')

    entries = []
    for off in range(dyn[0], dyn[0] + dyn[1], 16):
        tag, val = struct.unpack_from('<qQ', data, off)
        if tag == DT_NULL:
            break
        entries.append((off, tag, val))
    strtab = v2o(next(val for _, tag, val in entries if tag == DT_STRTAB))

    changed = []
    for _, tag, val in entries:
        if tag not in (DT_NEEDED, DT_SONAME):
            continue
        start = strtab + val
        end = data.index(0, start)
        name = data[start:end].decode()
        new = renames.get(name)
        if new is None or new == name:
            continue
        if len(new) > len(name):
            raise SystemExit(f'{path}: {new} is longer than {name}')
        data[start:end] = new.encode() + b'\0' * (len(name) - len(new))
        changed.append(f'{name} -> {new}')
    open(path, 'wb').write(data)
    return changed


if __name__ == '__main__':
    target = sys.argv[1]
    mapping = dict(arg.split('=', 1) for arg in sys.argv[2:])
    for line in patch(target, mapping):
        print(f'{target}: {line}')
