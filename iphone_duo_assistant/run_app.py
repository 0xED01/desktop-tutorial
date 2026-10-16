"""启动入口（也是 PyInstaller 的打包入口）。"""

import multiprocessing
import sys

from duo_assistant.app import main

if __name__ == "__main__":
    multiprocessing.freeze_support()
    sys.exit(main())
