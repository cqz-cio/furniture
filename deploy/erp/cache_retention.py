"""Latest-five complete OCI archives; no recursive deletion or Docker data pruning."""
import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import stat

from common import RELEASE, fingerprint, latest_release_plan, require, validate_release, write_json

MEMBERS = {'release.json', 'header.json', 'images.oci.tar', 'production.oci.tar'}


def plain(path):
    info = path.lstat()
    require(not path.is_symlink() and not (getattr(info, 'st_file_attributes', 0) & 0x400),
            'Refusing symlink or reparse point: '+path.name)
    return info


@contextmanager
def cache_lock(cache):
    root = Path(cache).absolute()
    require(root != Path(root.anchor), 'Cache cannot be a filesystem root')
    root.mkdir(parents=True, exist_ok=True)
    for parent in [root, *root.parents]:
        plain(parent)
    path = root/'.archive-operation.lock'
    if path.exists() or path.is_symlink():
        require(stat.S_ISREG(plain(path).st_mode), 'Invalid cache lock')
    with path.open('a+b') as stream:
        if path.stat().st_size == 0:
            stream.write(b'0'); stream.flush()
        stream.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            raise ValueError('Local images are busy; retry after build, upload or deployment completes') from None
        try:
            yield root.resolve()
        finally:
            stream.seek(0)
            if os.name == 'nt':
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream.fileno(), fcntl.LOCK_UN)


def inventory(root):
    records, files, skipped = [], {}, []
    for directory in root.iterdir():
        if not RELEASE.fullmatch(directory.name):
            continue
        require(stat.S_ISDIR(plain(directory).st_mode), 'Invalid release directory')
        complete = directory/'complete'
        require(not complete.is_symlink(), 'Unsafe complete directory')
        if not complete.exists():
            skipped.append(directory.name)
            continue
        require(stat.S_ISDIR(plain(complete).st_mode) and complete.resolve().is_relative_to(root), 'Unsafe archive directory')
        members = {p.name for p in complete.iterdir()}
        require(members in ({'release.json','header.json','images.oci.tar'},
                            {'release.json','header.json','images.oci.tar','production.oci.tar'}),
                'Incomplete or unknown complete-archive files')
        for name in members:
            require(stat.S_ISREG(plain(complete/name).st_mode), 'Unsafe archive file')
        value = validate_release(json.loads((complete/'release.json').read_text(encoding='utf-8')))
        require(value['id'] == directory.name and value['schema'] == 2
            and value['repository'] == 'cqz-cio/furniture', 'Unexpected local release identity')
        header = json.loads((complete/'header.json').read_text(encoding='utf-8'))
        require(header['release'] == value and header['bytes'] == (complete/'images.oci.tar').stat().st_size,
                'Local archive header differs from manifest')
        records.append(value)
        files[value['id']] = members
    return records, files, skipped


def retiring_inventory(root):
    pending = {}
    for parent in root.iterdir():
        if not RELEASE.fullmatch(parent.name):
            continue
        directory = parent/'retiring'
        if not directory.exists() and not directory.is_symlink():
            continue
        require(stat.S_ISDIR(plain(parent).st_mode) and stat.S_ISDIR(plain(directory).st_mode)
                and directory.resolve().is_relative_to(root), 'Unsafe retirement directory')
        require(not (parent/'complete').exists(), 'Release is both complete and retiring')
        members = {p.name for p in directory.iterdir()}
        require(members <= MEMBERS, 'Unknown retirement files')
        for name in members:
            require(stat.S_ISREG(plain(directory/name).st_mode), 'Unsafe retirement file')
        if members:
            require('release.json' in members, 'Retirement identity missing')
            value = validate_release(json.loads((directory/'release.json').read_text(encoding='utf-8')))
            require(value['id'] == parent.name and value['schema'] == 2
                    and value['repository'] == 'cqz-cio/furniture', 'Unexpected retirement identity')
        pending[parent.name] = members
    return pending


def clean_cache(cache, apply=False, output='local-retention-plan.json'):
    with cache_lock(cache) as root:
        records, files, skipped = inventory(root)
        pending = retiring_inventory(root)
        plan = latest_release_plan(records)
        sizes = {r['id']:sum((root/r['id']/'complete'/name).stat().st_size for name in files[r['id']]) for r in records}
        report = {**plan,'mode':'apply' if apply else 'dry-run','cache':str(root),
                  'incomplete_skipped':[ident for ident in skipped if ident not in pending], 'resume':sorted(pending),
                  'reclaimable_bytes':sum(sizes[ident] for ident in plan['retire']) +
                    sum((root/ident/'retiring'/name).stat().st_size for ident, names in pending.items() for name in names),
                  'removed':[]}
        write_json(output,report)
        if apply:
            by_id = {r['id']:r for r in records}
            for ident in [*pending, *plan['retire']]:
                directory = root/ident/('retiring' if ident in pending else 'complete')
                require(directory.resolve().is_relative_to(root), 'Archive escaped cache')
                plain(directory.parent); plain(directory)
                members = pending[ident] if ident in pending else files[ident]
                require({p.name for p in directory.iterdir()} == members, 'Archive files changed during cleanup')
                if ident not in pending:
                    current = json.loads((directory/'release.json').read_text(encoding='utf-8'))
                    require(fingerprint(current) == fingerprint(by_id[ident]), 'Archive changed during cleanup')
                for name in members:
                    require(stat.S_ISREG(plain(directory/name).st_mode), 'Archive member changed')
                if ident not in pending:
                    target = directory.parent/'retiring'
                    require(not target.exists() and not target.is_symlink(), 'Retirement already exists')
                    directory.rename(target)
                    directory = target
                # Names and resolved parents were checked above. Never recurse.
                # Keep identity until all other files are gone, so interrupted deletion can resume.
                for name in sorted(members - {'release.json'}) + (['release.json'] if 'release.json' in members else []):
                    (directory/name).unlink()
                directory.rmdir()
                if not any(directory.parent.iterdir()):
                    directory.parent.rmdir()
                report['removed'].append(ident)
                write_json(output,report)
        return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--cache', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if args.apply:
        from retention import require_cleanup_context
        require_cleanup_context()
    print(json.dumps(clean_cache(args.cache,args.apply),indent=2),flush=True)


if __name__ == '__main__':
    main()
