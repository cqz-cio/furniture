"""Bounded retries for read-only HTTP operations; never replay remote mutations."""
import http.client
import socket
import ssl
import time
import urllib.error

DELAYS = (2, 5, 10, 20)

def transient(error):
    if isinstance(error, urllib.error.HTTPError):
        return error.code in (408, 429, 500, 502, 503, 504)
    if isinstance(error, urllib.error.URLError):
        return transient(error.reason)
    if isinstance(error, ssl.SSLCertVerificationError):
        return False
    return isinstance(error, (TimeoutError, ConnectionError, ssl.SSLEOFError,
                              ssl.SSLZeroReturnError, http.client.IncompleteRead,
                              http.client.RemoteDisconnected)) or (
        isinstance(error, socket.gaierror) and error.errno == socket.EAI_AGAIN)

def retry_read(operation, label):
    for attempt in range(len(DELAYS) + 1):
        try:
            return operation()
        except Exception as error:
            if attempt == len(DELAYS) or not transient(error):
                raise
            if isinstance(error, urllib.error.HTTPError):
                error.close()
            # Error text can contain tokens, proxy credentials or signed URLs.
            delay = DELAYS[attempt]
            print(f"{label}: temporary network failure; retry {attempt + 1}/{len(DELAYS)} in {delay}s", flush=True)
            time.sleep(delay)
