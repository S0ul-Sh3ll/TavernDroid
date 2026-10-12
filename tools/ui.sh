#!/bin/bash
# Prints clickable UI elements (text + center coordinates) of the phone's current screen.
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
$ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
$ADB shell cat /sdcard/ui.xml | python3 -I -c '
import re,sys
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    n=m.group(0); g=lambda k:(re.search(k+r"=\"([^\"]*)\"",n) or [None,""])[1]; t=g(" text"); d=g("content-desc")
    b=list(map(int,re.findall(r"\d+",re.search(r"bounds=\"([^\"]*)\"",n).group(1))))
    if t or d: print(f"{(b[0]+b[2])//2},{(b[1]+b[3])//2}\t{t or d}")
'
$ADB shell rm /sdcard/ui.xml
