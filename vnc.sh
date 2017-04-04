#!/bin/bash

# Usage: `eval $(./vnc.sh)`

display=":42"
if [ ! -z "$1" ]; then
  display=":$1"
fi
security_types=""
if [ ! -z "$VNC_SECURITY_TYPES" ]; then
  security_types="-SecurityTypes $VNC_SECURITY_TYPES"
fi

vncserver -kill $display
vncserver -geometry 1750x1250 $security_types $display > /dev/null
if command -v vncviewer >/dev/null 2>&1; then
  vncviewer localhost$display > /dev/null &
fi

echo export BROWSER_DISPLAY=$display
echo export DISPLAY=$display
