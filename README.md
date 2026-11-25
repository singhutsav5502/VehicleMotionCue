# CutLog

A small, battery-light Android app for tracking a fat-loss cut: food, protein, workouts and weight, with daily vs weekly calorie budgets and a Claude coach.

## Features
- **Today**: calories left plus protein/carbs/fat bars, entries grouped by meal, the day's dinner-rotation item, and protein-gap hints. You can step back to log earlier days.
- **Add food**: one-tap presets from the plan (whey + skim milk, hung curd, paneer, tofu, soy chunks, chicken, canteen lunches…), ×0.5–×2 portions, custom entries that can be saved as presets, and **"Describe it, Claude estimates"** (needs an API key).
- **Workouts**: presets or custom entries. Burn is tracked only and never added to your food budget.
- **Week**: Mon–Sun or rolling 7 days, a daily bar chart against the target, weekly budget and what's left, a "to land on budget" kcal/day figure for the rest of the week, protein days, estimated deficit and fat loss, and the last 4 weeks.
- **Weight**: daily weigh-ins with optional body fat %, a 7-day moving average, a 3-week trend, and a projection to the goal date. Seeded with the 15 Nov 2026 InBody reading.
- **Claude**: in-app chat (Anthropic API key) with your live data as context, plus **Share to app**, which sends your weekly summary to the Claude Android app.
- **Settings**: targets, maintenance estimate, goal, protein rotation, API key and model, CSV export.

## Battery
No background services, alarms, sync or location. The network is used only when you send something to Claude. The true-black theme saves power on AMOLED screens. Data lives in local SQLite.

## Build
GitHub Actions builds `app-release.apk` (debug-signed so it can be sideloaded) on every push to `main`. Get it from Actions → latest run → artifact **cutlog-apk**.

Locally: `./gradlew :app:assembleRelease` (JDK 17 + Android SDK 35).
