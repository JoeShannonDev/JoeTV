#!/usr/bin/env python3
"""
One-time Google Calendar authorization for JoeTV.

Run this ONCE on a desktop machine with a browser. It prints a refresh token
that you paste into local.properties as JOETV_GOOGLE_REFRESH_TOKEN. JoeTV then
mints its own access tokens forever and never needs to sign in on the Pi.

Why this exists: the Pi has no browser and no signed-in Google account, and
Google's device flow (code on TV, enter it on your phone) does NOT support
Calendar scopes -- only sign-in, Drive and YouTube. So the loopback flow has to
happen somewhere with a browser, and that is here.

Usage:
    python tools/get_google_refresh_token.py

Reads the client ID/secret from local.properties if present, otherwise prompts.
Standard library only -- no pip install needed.
"""

import base64
import hashlib
import http.server
import json
import os
import secrets
import socket
import sys
import threading
import urllib.parse
import urllib.request
import webbrowser

SCOPE = "https://www.googleapis.com/auth/calendar.events.readonly"
AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCAL_PROPERTIES = os.path.join(REPO_ROOT, "local.properties")


def read_local_properties():
    """Pulls the client credentials out of local.properties if they're there."""
    values = {}
    if not os.path.exists(LOCAL_PROPERTIES):
        return values

    with open(LOCAL_PROPERTIES, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip()

    return values


def prompt_for(label, existing):
    if existing:
        print("  using %s from local.properties" % label)
        return existing
    return input("  paste your %s: " % label).strip()


def free_port():
    """Grabs an OS-assigned port. Desktop-app clients allow any 127.0.0.1 port."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


class CallbackHandler(http.server.BaseHTTPRequestHandler):
    """Catches Google's redirect and stashes the authorization code."""

    captured = {}

    def do_GET(self):
        query = urllib.parse.urlparse(self.path).query
        params = urllib.parse.parse_qs(query)

        CallbackHandler.captured = {
            key: values[0] for key, values in params.items()
        }

        if "code" in CallbackHandler.captured:
            message = "JoeTV is authorized. You can close this tab and go back to the terminal."
        else:
            message = "Authorization failed: %s" % CallbackHandler.captured.get(
                "error", "no code returned"
            )

        body = (
            "<html><body style='font-family:sans-serif;background:#05070B;"
            "color:#fff;display:flex;align-items:center;justify-content:center;"
            "height:100vh;margin:0'><h2>%s</h2></body></html>" % message
        ).encode("utf-8")

        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass  # keep the console clean


def main():
    print("\nJoeTV -- one-time Google Calendar authorization\n")

    props = read_local_properties()
    client_id = prompt_for(
        "JOETV_GOOGLE_CLIENT_ID", props.get("JOETV_GOOGLE_CLIENT_ID")
    )
    client_secret = prompt_for(
        "JOETV_GOOGLE_CLIENT_SECRET", props.get("JOETV_GOOGLE_CLIENT_SECRET")
    )

    if not client_id or not client_secret:
        sys.exit("\nBoth a client ID and secret are required. Aborting.")

    port = free_port()
    redirect_uri = "http://127.0.0.1:%d" % port
    state = secrets.token_urlsafe(24)

    # PKCE. Google flags installed-app clients that skip this as "not using
    # secure OAuth flows", because without it anyone who intercepts the
    # authorization code on the loopback redirect can redeem it themselves.
    # The verifier never leaves this process; only its SHA-256 hash is sent
    # up front, so an intercepted code is useless on its own.
    code_verifier = secrets.token_urlsafe(64)
    code_challenge = base64.urlsafe_b64encode(
        hashlib.sha256(code_verifier.encode("ascii")).digest()
    ).decode("ascii").rstrip("=")

    auth_url = AUTH_ENDPOINT + "?" + urllib.parse.urlencode(
        {
            "client_id": client_id,
            "redirect_uri": redirect_uri,
            "response_type": "code",
            "scope": SCOPE,
            "state": state,
            # access_type=offline is what makes Google issue a refresh token
            # at all; prompt=consent forces a NEW one even if this account has
            # authorized before, which is the usual reason people get back a
            # response with no refresh_token in it.
            "access_type": "offline",
            "prompt": "consent",
            "code_challenge": code_challenge,
            "code_challenge_method": "S256",
        }
    )

    server = http.server.HTTPServer(("127.0.0.1", port), CallbackHandler)
    threading.Thread(target=server.handle_request, daemon=True).start()

    print("\n  Opening your browser to sign in...")
    print("  If it doesn't open, paste this URL yourself:\n")
    print("  " + auth_url + "\n")
    webbrowser.open(auth_url)

    print("  Waiting for Google to redirect back...")
    server.socket.close()

    captured = CallbackHandler.captured
    if "code" not in captured:
        sys.exit(
            "\nNo authorization code received: %s"
            % captured.get("error", "unknown error")
        )

    if captured.get("state") != state:
        sys.exit("\nState mismatch -- aborting rather than trusting this response.")

    print("  Exchanging the code for tokens...")

    payload = urllib.parse.urlencode(
        {
            "code": captured["code"],
            "client_id": client_id,
            "client_secret": client_secret,
            "redirect_uri": redirect_uri,
            "grant_type": "authorization_code",
            "code_verifier": code_verifier,
        }
    ).encode("utf-8")

    try:
        with urllib.request.urlopen(
            urllib.request.Request(TOKEN_ENDPOINT, data=payload)
        ) as response:
            tokens = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        sys.exit("\nToken exchange failed (HTTP %d):\n%s" % (error.code, detail))

    refresh_token = tokens.get("refresh_token")

    if not refresh_token:
        sys.exit(
            "\nGoogle returned no refresh token. This usually means the account "
            "already authorized this client. Revoke JoeTV at "
            "https://myaccount.google.com/permissions and run this again."
        )

    print("\n" + "=" * 68)
    print("Add this line to local.properties, then rebuild JoeTV:\n")
    print("JOETV_GOOGLE_REFRESH_TOKEN=%s" % refresh_token)
    print("=" * 68)
    print(
        "\nHeads up: if your OAuth consent screen is still in 'Testing' status,\n"
        "this token expires in 7 days. Set it to 'In production' in Google Cloud\n"
        "Console so it lasts indefinitely.\n"
    )


if __name__ == "__main__":
    main()
