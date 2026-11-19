# Tiida Smart Control
Android companion app for a 2011 Nissan Tiida using a Bluetooth Classic ELM327 adapter.

## Current prototype
- Pair/select Bluetooth ELM327 device
- Connect over Bluetooth SPP
- Initialize adapter with basic AT commands
- AT command console and response log
- Lock / Unlock / Lights are intentionally disabled until BCM protocol and commands are confirmed.

## Build
Open this folder in Android Studio and run on a phone. Android 12+ Bluetooth Connect permission is requested.

## Safety
ECM protocol detection does not confirm BCM access. Do not send unknown vehicle commands. Active vehicle controls remain disabled until verified.
