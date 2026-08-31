#!/usr/bin/env python3
"""
Turns a downloaded Google service account JSON key into local.properties lines.

Run this once after creating the service account. It prints the three values
JoeTV needs, with the PEM private key flattened to a single line so it survives
a .properties file and a Kotlin string literal.

Usage:
    python tools/prepare_service_account.py path\\to\\service-account.json
    python tools/prepare_service_account.py path\\to\\key.json --calendar you@gmail.com

Standard library only -- no pip install needed.
"""

import base64
import json
import os
import sys

PEM_HEADER = "-----BEGIN PRIVATE KEY-----"
PEM_FOOTER = "-----END PRIVATE KEY-----"


def flatten_private_key(pem):
    """
    Strips a PKCS#8 PEM down to its bare base64 body.

    The app feeds this straight into PKCS8EncodedKeySpec, and a .properties
    value cannot hold real newlines, so the armor and line breaks both go.
    """
    if PEM_HEADER not in pem:
        sys.exit(
            "That key is not in PKCS#8 PEM format (no '%s' line).\n"
            "Make sure you downloaded a JSON key, not a P12." % PEM_HEADER
        )

    body = pem.split(PEM_HEADER, 1)[1].split(PEM_FOOTER, 1)[0]
    flattened = "".join(body.split())

    # Fail loudly here rather than letting Android throw InvalidKeySpecException
    # at runtime, where the only symptom is an empty calendar card.
    try:
        base64.b64decode(flattened, validate=True)
    except Exception as error:
        sys.exit("Private key body is not valid base64: %s" % error)

    return flattened


def main():
    args = [a for a in sys.argv[1:]]

    calendar_id = ""
    if "--calendar" in args:
        index = args.index("--calendar")
        try:
            calendar_id = args[index + 1]
        except IndexError:
            sys.exit("--calendar needs an email address after it.")
        del args[index:index + 2]

    if not args:
        sys.exit(__doc__)

    key_path = args[0]
    if not os.path.exists(key_path):
        sys.exit("No such file: %s" % key_path)

    with open(key_path, "r", encoding="utf-8") as handle:
        key_data = json.load(handle)

    if key_data.get("type") != "service_account":
        sys.exit(
            "That JSON is not a service account key (type is %r).\n"
            "Download it from IAM & Admin > Service Accounts > Keys > Add key."
            % key_data.get("type")
        )

    client_email = key_data.get("client_email", "")
    private_key = key_data.get("private_key", "")

    if not client_email or not private_key:
        sys.exit("Key file is missing client_email or private_key.")

    flattened_key = flatten_private_key(private_key)

    if not calendar_id:
        print("\nWhich calendar should JoeTV read?")
        print("This is normally your own Gmail address.\n")
        calendar_id = input("  calendar ID: ").strip()

    print("\n" + "=" * 72)
    print("1. Share your calendar with this address, 'See all event details':\n")
    print("   " + client_email)
    print("\n2. Add these lines to local.properties, then rebuild JoeTV:\n")
    print("JOETV_GOOGLE_SERVICE_ACCOUNT_EMAIL=%s" % client_email)
    print("JOETV_GOOGLE_SERVICE_ACCOUNT_KEY=%s" % flattened_key)
    print("JOETV_GOOGLE_CALENDAR_ID=%s" % calendar_id)
    print("=" * 72)
    print(
        "\nStep 1 is not optional -- without it the service account can reach\n"
        "the API but sees none of your events, and the card stays empty.\n"
    )


if __name__ == "__main__":
    main()
