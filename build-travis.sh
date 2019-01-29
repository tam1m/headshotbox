#!/bin/bash
set -e
set -x

rm -rf win linux
mkdir win
mkdir linux
python2 get-demoinfogo.py
mv demoinfogo.exe win/
unzip demoinfogo-linux.zip -d linux
demoinfogo_bin_win=win demoinfogo_bin_linux=linux python build.py
