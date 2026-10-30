# DIVlauncher performance baseline

Goal: measure before changing anything, so every later change (Performance toggle, renderer, Rust) is judged with numbers.

## One-time setup
1. Install the same APK build for every run. Note the app version (Settings, bottom of the category list).
2. Settings > Experimental > turn on **Performance log**.
3. Same phone state for every run: battery above 60%, not charging, fixed screen brightness, airplane mode on,
   other apps closed, phone at room temperature (let it cool 10 minutes after a run).

## Test world (always the same)
- A fixed profile (note: Minecraft version, loader, mods).
- A new creative world with a fixed seed (write it down), same render distance (for example 8) and graphics settings.
- Same resolution scale and renderer (note them).

## One run (about 3 minutes)
1. Start the game, load the world, stand still for 20 seconds.
2. Walk or fly in a straight line for 2 minutes, turn on the spot at the end.
3. Quit the game normally.

## What gets recorded
Files appear in `games/PojavLauncher/benchmark/` (inside the game directory):
- `frames.csv`: `epoch_ms,avg_fps,low1_fps,max_frame_ms,frames` every 5 seconds. `low1_fps` is the average FPS of the slowest 1% of frames; low values mean stutter.
- `device.csv`: `epoch_ms,battery_temp_c,battery_percent,thermal_status` every 5 seconds (0 none, 1 light, 2 moderate, 3 severe or higher).

## Compare runs
Repeat each setup 3 times and keep the middle result. Compare average FPS, 1% lows and how the battery temperature and
thermal status rise. FPS falling while thermal status reaches 2 or more means heat, not the game, is the limit.

## Template
| Run | App version | Phone | Renderer | Java | Resolution scale | Render distance | Mods | Avg FPS | 1% low | Max battery temp |
|-----|-------------|-------|----------|------|------------------|-----------------|------|---------|--------|------------------|
|     |             |       |          |      |                  |                 |      |         |        |                  |
