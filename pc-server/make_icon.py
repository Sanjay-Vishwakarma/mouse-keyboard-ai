"""Generates assets/icon.png and assets/icon.ico (blue circle with a white monitor). Needs Pillow."""

import os

from PIL import Image, ImageDraw

SIZE = 512
BLUE = (79, 142, 247, 255)
WHITE = (255, 255, 255, 255)

here = os.path.dirname(os.path.abspath(__file__))
out_dir = os.path.join(here, "assets")
os.makedirs(out_dir, exist_ok=True)

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.ellipse((0, 0, SIZE - 1, SIZE - 1), fill=BLUE)

s = SIZE / 48  # same geometry as the Android launcher icon (48-unit grid)
screen = (12.0 * s, 14.0 * s, 36.0 * s, 30.5 * s)
d.rounded_rectangle(screen, radius=2.2 * s, fill=WHITE)
inner = (14.4 * s, 16.4 * s, 33.6 * s, 28.1 * s)
d.rectangle(inner, fill=BLUE)
d.rectangle((22.2 * s, 30.5 * s, 25.8 * s, 33.5 * s), fill=WHITE)   # neck
d.rounded_rectangle((18.0 * s, 33.0 * s, 30.0 * s, 35.2 * s), radius=0.8 * s, fill=WHITE)  # stand

img.resize((256, 256), Image.LANCZOS).save(os.path.join(out_dir, "icon.png"))
img.save(os.path.join(out_dir, "icon.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("wrote", out_dir)
