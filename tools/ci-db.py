"""Write synthetic disposable CI connection data privately; never print passwords."""
from pathlib import Path
import json
import os

path = Path(os.environ["LUNIXI_TEST_DB_FILE"])
if path.exists(): raise SystemExit("Use a new disposable CI configuration file.")
value = {"host": "127.0.0.1", "port": 3306, "database": "lunixi_spring_ci", "username": "lunixi", "password": os.environ["LUNIXI_CI_DATABASE_PASSWORD"]}
handle = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(handle, "w") as output: json.dump(value, output)
