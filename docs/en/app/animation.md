# Animation Effects

Animation Effects control lyric transition visuals. They are located in **App Styles** under the *
*Animation** tab.

## Animation Switch

When animations are disabled, lyric changes become direct and resource usage is lower. If the device
is weak, lyrics update frequently, or display glitches occur, disable animations first for
troubleshooting.

## Animation Presets

Lyricon includes several animation presets. After selecting a preset, the current app style uses
that transition effect.

Different presets have different visual intensity and resource cost. Choose based on device
performance and available status bar space.

## Recommendations

| Scenario               | Recommendation                              |
|:-----------------------|:--------------------------------------------|
| Stability first        | Disable animations or choose a light preset |
| Frequent lyric updates | Avoid aggressive animations                 |
| Low-end device         | Disable animations first                    |
| Visual effect first    | Choose a more noticeable transition         |

## Troubleshooting

If lyric transitions flicker, stutter, or move incorrectly:

1. Disable animations.
2. Restart System UI.
3. Confirm text size, width, and marquee settings are reasonable.
4. Try animation presets one by one.
