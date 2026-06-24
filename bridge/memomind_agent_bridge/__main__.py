from __future__ import annotations

import argparse
import sys

from .doctor import doctor
from .main import main
from .status import status


if __name__ == "__main__":
    parser = argparse.ArgumentParser(prog="memomind_agent_bridge")
    parser.add_argument(
        "command",
        nargs="?",
        default="run",
        choices=("run", "doctor", "status"),
        help="run: start the bridge loop, doctor: validate local Bridge environment, status: show recent bridge task activity",
    )
    args = parser.parse_args()
    if args.command == "doctor":
        sys.exit(0 if doctor() else 1)
    elif args.command == "status":
        status()
    else:
        main()
