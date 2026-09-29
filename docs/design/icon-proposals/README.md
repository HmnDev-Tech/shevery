# Launcher Icon Proposals

Design proposals for a refreshed Shevery launcher icon, discussed in
issue #ISSUE_NUMBER and introduced by PR #PR_NUMBER.

The current icon's composition (cat cropped into a corner, gear mostly
hidden behind it) does not respect the adaptive-icon safe zone, so many
launchers mask or crop it badly. All concepts below keep the existing
identity — periwinkle field, white cat, dark-navy gear — but rebuild the
composition so it survives adaptive masking and stays legible at 48 px.

## Concepts

| File | Concept | Composition |
|---|---|---|
| `hub-cat.svg` | Hub Cat | The cat head becomes the gear's hub; the teeth read as a mane around it |
| `paw-hub.svg` | Paw Hub | A paw print replaces the gear's hub; playful, still mechanical |
| `orbit-cat.svg` | Orbit Cat | The cat curls around the gear, guarding it |
| `peek-mask.svg` | Peek Mask | The cat peeks over the gear, which sits like a mask/collar |
| `gear-pin.svg` | Gear Pin | A tiny gear pinned on the ear; the most minimal take |
| `halo-cat.svg` | Halo Cat | The author's own design: heart-eyed cat framed by the gear as a halo behind it |

## Shared palette

| Role | Flat concepts | Halo Cat |
|---|---|---|
| Background | `#6672C5` | gradient `#7C82D8 → #565CB0` |
| Cat | `#F7F7FB` | gradient `#FFFFFF → #E6E8FA` |
| Gear | `#262B45` | gradient `#3A3D63 → #22243F` |
| Dark core | `#1B2036` | — |
| Accent (eyes, details) | `#4A53B0` | `#5C63C4` |

## Files & usage notes

- Every concept is a single self-contained SVG on a 512×512 canvas,
  built from flat shapes and gradients only — no rasters, no fonts, no
  external references — so any element can be recolored or rearranged
  without tooling.
- Each motif sits inside the central ~66% of the canvas, matching the
  Android adaptive-icon safe zone, so launcher masks (circle, squircle,
  teardrop) cannot crop the cat or the gear.
- Rendered previews on a home-screen mock are attached to the linked
  issue.

## Provenance & license

Concepts 1–5 were explored with AI-assisted design tooling and then
curated, rebuilt and finalized by the author; concept 6 (Halo Cat) is
the author's own design. All artwork is contributed under the project's
license and contains no third-party assets or mascots from other
projects.

## Feedback welcome

If you have thoughts on any of these concepts — color, composition, 
which one feels right for Shevery — please share in the linked issue. 
Happy to iterate or create variations.

## How to preview

Each SVG file can be opened directly in any modern browser, or imported 
into Figma/Illustrator/Inkscape for editing. The shared palette above 
makes it easy to recolor any concept to match a different theme.

## Next step (once a concept is chosen)

A follow-up PR will implement the chosen concept as a real adaptive
icon: `ic_launcher_foreground.xml` / `ic_launcher_background.xml`
vectors on the 108 dp canvas with the motif inside the 66 dp safe zone,
all mipmap densities, a round variant, and `ic_launcher_monochrome.xml`
for Android 13+ themed icons.
