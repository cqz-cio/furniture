"""Wait for usable GitHub TLS connections before a local runner accepts jobs."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import time
import urllib.error
import urllib.request

ENDPOINTS = (
    ("api.github.com", "https://api.github.com/", {200}),
    ("launch.actions.githubusercontent.com", "https://launch.actions.githubusercontent.com/", {404}),
    ("codeload.github.com", "https://codeload.github.com/", {200}),
    ("ghcr.io", "https://ghcr.io/v2/", {401}),
)

def probe(endpoint):
    name, url, expected = endpoint
    request = urllib.request.Request(url, headers={"User-Agent": "oakved-runner-network-check"})
    try:
        # Use the OS/TUN route, independent of browser-only proxy settings.
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(request, timeout=5) as response:
            status = response.status
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
    except (OSError, urllib.error.URLError):
        return name, False
    return name, status in expected

def wait_for_network(timeout=1200, interval=10, stable_rounds=3,
                     check=probe, clock=time.monotonic, sleep=time.sleep):
    deadline = clock() + timeout
    consecutive = 0
    with ThreadPoolExecutor(max_workers=len(ENDPOINTS)) as pool:
        while clock() < deadline:
            results = list(pool.map(check, ENDPOINTS))
            failed = [name for name, success in results if not success]
            consecutive = 0 if failed else consecutive + 1
            print(time.strftime("%Y-%m-%d %H:%M:%S") + " " + (
                "Waiting for TLS connectivity: " + ", ".join(failed) if failed else
                f"GitHub connectivity passed ({consecutive}/{stable_rounds})"), flush=True)
            if consecutive >= stable_rounds and clock() <= deadline:
                return True
            remaining = deadline - clock()
            if remaining <= 0:
                break
            sleep(min(interval, remaining))
    print("Network readiness deadline exceeded; runner has not been started.", flush=True)
    return False

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--timeout", type=int, default=1200)
    args = parser.parse_args()
    if not 1 <= args.timeout <= 1200:
        parser.error("timeout must be between 1 and 1200 seconds")
    raise SystemExit(0 if wait_for_network(timeout=args.timeout) else 1)
