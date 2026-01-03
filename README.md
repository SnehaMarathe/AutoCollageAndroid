# AutoCollageAndroid Pro (Camera-in-slot)

Instagram-friendly collage creator:
- Tap a collage slot -> **live camera opens inside the slot**
- Align framing to the slot -> Capture -> Use -> Crop
- Pinch-zoom + drag to position inside each slot
- Export saves **one final image** to `Pictures/AutoCollage`

## Controls
- Tap slot: in-slot camera
- Long-press slot: Photo Picker (gallery)
- Export: saves collage

## GitHub Actions
Workflow builds Debug APK and uploads it as an artifact.
If `gradle-wrapper.jar` is missing, the workflow generates it on the runner.
