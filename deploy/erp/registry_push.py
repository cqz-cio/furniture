"""Bounded OCI publication; preserve original manifests and every layer digest."""
import base64
import hashlib
import json
from pathlib import Path
import tarfile
import time
import urllib.error
import urllib.parse
import urllib.request

from common import PACKAGES, DIGEST, RELEASE, require
from image_archive import ACCEPT, Registry
from image_relay import validate_archive


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None


class Publisher:
    def __init__(self, actor, token, seconds=1800):
        require(actor and token, 'Registry credentials missing')
        self.actor, self.token = actor, token
        self.tokens = {}
        self.deadline = time.monotonic() + seconds
        self.opener = urllib.request.build_opener(NoRedirect())

    def check(self):
        require(time.monotonic() < self.deadline, 'Image publication exceeded its deadline')

    def headers(self, package):
        require(package in PACKAGES.values(), 'Unexpected registry package')
        self.check()
        if package not in self.tokens:
            url = 'https://ghcr.io/token?' + urllib.parse.urlencode({'service': 'ghcr.io', 'scope': 'repository:'+package+':pull,push'})
            auth = base64.b64encode((self.actor+':'+self.token).encode()).decode()
            req = urllib.request.Request(url, headers={'Authorization': 'Basic '+auth})
            try:
                with self.opener.open(req, timeout=20) as response:
                    self.tokens[package] = json.loads(response.read(65536))['token']
            except Exception as error:
                raise RuntimeError('GHCR authentication failed: '+type(error).__name__) from None
        return {'Authorization': 'Bearer '+self.tokens[package], 'Accept': ACCEPT}

    def url(self, package, location):
        url = urllib.parse.urljoin('https://ghcr.io', location)
        parsed = urllib.parse.urlsplit(url)
        require(parsed.scheme == 'https' and parsed.netloc == 'ghcr.io' and not parsed.fragment
            and parsed.path.startswith('/v2/'+package+'/') and '..' not in parsed.path
            and '%' not in parsed.path, 'Unsafe registry upload URL')
        return url

    def request(self, package, method, location, data=None, headers=None, expected=(200,)):
        self.check()
        url = self.url(package, location)
        for attempt in range(2):
            req = urllib.request.Request(url, data=data, method=method,
                headers={**self.headers(package), **(headers or {})})
            try:
                response = self.opener.open(req, timeout=max(1, min(20, self.deadline-time.monotonic())))
            except urllib.error.HTTPError as error:
                response = error
            except Exception as error:
                raise RuntimeError('GHCR '+method+' failed: '+type(error).__name__) from None
            if response.status == 401 and attempt == 0:
                response.close()
                self.tokens.pop(package, None)
                self.check()
                continue
            break
        with response:
            require(response.status in expected, f'GHCR {method} returned HTTP {response.status}')
            raw = response.read(2*1024**2+1)
            require(len(raw) <= 2*1024**2, 'Oversized registry response')
            return response.status, response.headers, raw

    def blob(self, archive, package, descriptor):
        digest, size = descriptor['digest'], descriptor['size']
        require(DIGEST.fullmatch(digest) and type(size) is int and size >= 0, 'Invalid blob descriptor')
        endpoint = '/v2/'+package+'/blobs/'+digest
        status, _, _ = self.request(package, 'HEAD', endpoint, expected=(200, 404))
        if status == 200:
            print(json.dumps({'stage':'publish-cached-layer','digest':digest}), flush=True)
            return
        _, headers, _ = self.request(package, 'POST', '/v2/'+package+'/blobs/uploads/', b'', expected=(202,))
        location = self.url(package, headers['Location'])
        completed = False
        try:
            member = archive.getmember('blobs/sha256/'+digest[7:])
            require(member.isfile() and member.size == size, 'Truncated image layer')
            count, checksum = 0, hashlib.sha256()
            with archive.extractfile(member) as stream:
                while chunk := stream.read(4*1024**2):
                    self.check()
                    _, headers, _ = self.request(package, 'PATCH', location, chunk,
                        {'Content-Type':'application/octet-stream', 'Content-Range':f'{count}-{count+len(chunk)-1}'}, expected=(202,))
                    count += len(chunk); checksum.update(chunk)
                    require(headers.get('Range') == f'0-{count-1}', 'Registry acknowledged an unexpected byte range')
                    location = self.url(package, headers['Location'])
                    print(json.dumps({'stage':'publish-layer','bytes':count,'total':size}), flush=True)
            require(count == size and 'sha256:'+checksum.hexdigest() == digest, 'Image layer changed during upload')
            separator = '&' if urllib.parse.urlsplit(location).query else '?'
            self.request(package, 'PUT', location+separator+urllib.parse.urlencode({'digest':digest}), b'',
                         {'Content-Type':'application/octet-stream'}, expected=(201,))
            completed = True
        finally:
            if not completed:
                try:
                    self.request(package, 'DELETE', location, expected=(204, 404))
                except Exception:
                    # GHCR currently responds 405 to upload cancellation. Never
                    # claim that a failed upload's temporary bytes were removed.
                    print(json.dumps({'stage':'upload-cancellation-unconfirmed'}), flush=True)

    def archive(self, path, references, release_id):
        require(RELEASE.fullmatch(release_id), 'Invalid publication release')
        validate_archive(Path(path), references)
        with tarfile.open(path, 'r:') as archive:
            for number, reference in enumerate(references):
                package, digest = reference.removeprefix('ghcr.io/').split('@')
                require(reference.startswith('ghcr.io/') and package in PACKAGES.values(), 'Invalid publication reference')
                with archive.extractfile('blobs/sha256/'+digest[7:]) as source:
                    raw = source.read(2*1024**2+1)
                require(len(raw) <= 2*1024**2 and 'sha256:'+hashlib.sha256(raw).hexdigest() == digest, 'Manifest changed')
                manifest = json.loads(raw)
                require('layers' in manifest and 'config' in manifest, 'Expected a single-platform OCI image')
                for descriptor in [manifest['config'], *manifest['layers']]:
                    self.blob(archive, package, descriptor)
                tag = release_id + ('' if number == 0 else '-test' if number == 1 else '-production')
                endpoint = '/v2/'+package+'/manifests/'
                status, _, existing = self.request(package, 'GET', endpoint+tag, expected=(200, 404))
                require(status == 404 or existing == raw, 'Published release tag already has different content')
                if status == 404:
                    self.request(package, 'PUT', endpoint+tag, raw,
                        {'Content-Type':manifest.get('mediaType', 'application/vnd.oci.image.manifest.v1+json')}, expected=(201,))
                _, _, confirmed = self.request(package, 'GET', endpoint+digest)
                require(confirmed == raw, 'Published manifest does not match the CI artifact')
                # Production Engine pulls anonymously: prove the same access now.
                require(Registry().manifest(package, digest) == raw, 'Published image is not anonymously readable')
                print(json.dumps({'stage':'published-image','image':reference}), flush=True)
