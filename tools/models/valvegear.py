"""Pushrod and rocker."""

from models.core import BRASS, IRON, IRON_BLOCK, box, write


def pushrod():
    """A pushrod and the follower foot at its bottom, in the frame of the cylinder block it runs beside: +Y is the bore,
    +Z the side the cam is on, the rod runs just off that face from the height of the lobes (the middle of the block) up
    to the rocker on the head. Centred on the middle of the block across the cam, so the renderer slides it to its lobe."""
    write("cylinder/pushrod", {"rod": IRON_BLOCK, "foot": IRON, "particle": IRON_BLOCK},
          [box((7.25, 8, 16.25), (8.75, 19.25, 18), "rod"), box((6.75, 7, 16.25), (9.25, 9, 18), "foot")])


def rocker():
    """A rocker arm, in the frame of the head it sits on: +Y is the bore, +Z the side its cam is on. It pivots about a pin
    inside the head (at y 4, z 14) and the arm comes out of the head's face, so the pushrod under its end tips it."""
    write("cylinder_head/rocker", {"arm": BRASS, "cap": IRON_BLOCK, "particle": BRASS},
          [box((6.75, 3.25, 14), (9.25, 4.75, 20.5), "arm"), box((6.25, 3, 18.5), (9.75, 5, 20.5), "cap")])
