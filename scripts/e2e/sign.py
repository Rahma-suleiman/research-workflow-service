#!/usr/bin/env python3
"""Prints the X-Actor-Signature header value for manual testing (curl, Swagger, Postman).

Instance action:
  python3 scripts/e2e/sign.py action --secret <signingSecret> \
      --instance-id <id> --action SUBMIT --body body.json

Validate-only transition:
  python3 scripts/e2e/sign.py transition --secret <signingSecret> --body body.json

body.json must contain exactly the JSON you send, because every field is signed.
"""
import argparse
import json

from engine_client import sign_instance_action, sign_transition


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("kind", choices=["action", "transition"])
    parser.add_argument("--secret", required=True, help="the SERVICE client's signing secret")
    parser.add_argument("--body", required=True, help="path to the JSON request body")
    parser.add_argument("--instance-id", help="required for 'action'")
    parser.add_argument("--action", help="action key, required for 'action'")
    args = parser.parse_args()

    with open(args.body, encoding="utf-8") as handle:
        body = json.load(handle)
    if args.kind == "action":
        if not args.instance_id or not args.action:
            parser.error("'action' needs --instance-id and --action")
        print(sign_instance_action(args.secret, args.instance_id, args.action, body))
    else:
        print(sign_transition(args.secret, body))


if __name__ == "__main__":
    main()
