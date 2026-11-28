"""The wear mode's wrecks."""

from models.core import BLACK, CUT_COPPER, DARK, IRON, IRON_BLOCK, MAGMA, QUARTZ, RED, _wrapped_default_uv, box, write
from models.head import head


# --- the wear mode's wrecks ----------------------------------------------------------------------------------------

def tilt(element, origin, axis, angle):
    """The same element turned about a point, as the game's single-axis element rotation does (22.5 degree steps)."""
    element["rotation"] = {"origin": [round(v, 4) for v in origin], "axis": axis, "angle": angle}
    return element


def wear_wrecks():
    """What a failure leaves behind, drawn over the part as it stands (the renderers lay each in the part's own frame, upright):

    cylinder_head/wreck         the head a rod went through: a split in its top plate, a flap of the plate thrown up, the broken
                                rod standing out of the hole with its glowing break, and bolts and shards flung about
    cylinder_head/blown_gasket  a red gasket squeezed out all round the joint at the bottom of the head, its torn tongues sticking
                                out past the block, a glowing hole above it and soot up the side where the gas came out
    cylinder/wreck              the wreck inside the bore: the rod bent in two, and the shards of the piston lying in it
    cylinder/seized_ring        a scorched band and a heat-coloured ring on a piston that has seized, drawn at the piston's own pose"""
    textures = {"iron": IRON, "steel": IRON_BLOCK, "dark": DARK, "black": BLACK, "plate": CUT_COPPER, "hot": MAGMA,
                "particle": IRON}
    head = [
        # the split in the top plate, and a second crack across it
        box((4, 16, 6.5), (12, 16.3, 8.5), "black"),
        box((7, 16, 3), (9, 16.3, 13), "black"),
        # a flap of the plate thrown up at one side of the hole
        tilt(box((9.5, 16, 9), (14.5, 16.8, 14), "plate"), (12, 16, 9), "x", 22.5),
        # the oil that came up with it
        box((2, 16, 10), (7, 16.1, 15), "dark"),
        # the rod standing out of the hole, leaning, with its big end torn off ragged and the break glowing
        tilt(box((7, 12, 7), (9, 23, 9), "iron"), (8, 16, 8), "z", 22.5),
        tilt(box((6.3, 21.5, 6.4), (9.7, 24, 9.6), "steel"), (8, 16, 8), "z", 22.5),
        tilt(box((6.9, 24, 6.9), (9.1, 24.4, 9.1), "hot"), (8, 16, 8), "z", 22.5),
        # bolts and shards flung across the top
        tilt(box((2, 16, 2), (3.4, 17, 3.6), "dark"), (2.7, 16, 2.8), "y", 22.5),
        box((12.6, 16, 2.4), (14.2, 16.8, 3.8), "dark"),
        tilt(box((11, 16, 12.5), (12.2, 16.6, 14.4), "iron"), (11.6, 16, 13.4), "y", -22.5),
    ]
    write("cylinder_head/wreck", textures, head)

    # The gasket squeezed out of the joint at the bottom of the head, torn and drooping, and the soot where the gas came out, up the side of the head. Every
    # side has them, so a pipe on one face does not hide the damage: the north's are made and turned a quarter turn for each of the others.
    def quarter(element, turns):
        a, b = element["from"], element["to"]
        def turn(x, z):
            for _ in range(turns):
                x, z = 16 - z, x
            return x, z
        x1, z1 = turn(a[0], a[2])
        x2, z2 = turn(b[0], b[2])
        element["from"] = [min(x1, x2), a[1], min(z1, z2)]
        element["to"] = [max(x1, x2), b[1], max(z1, z2)]
        if "rotation" in element:
            ox, oy, oz = element["rotation"]["origin"]
            tx, tz = turn(ox, oz)
            element["rotation"]["origin"] = [tx, oy, tz]
            axis = element["rotation"]["axis"]
            if axis in ("x", "z") and turns % 2:
                element["rotation"]["axis"] = "z" if axis == "x" else "x"
                if (turns == 1 and axis == "x") or (turns == 3 and axis == "z"):
                    element["rotation"]["angle"] = -element["rotation"]["angle"]
            elif axis in ("x", "z") and turns == 2:
                element["rotation"]["angle"] = -element["rotation"]["angle"]
        return element

    def north_side():
        return [
            # the gasket itself, bright red and squeezed out all along the joint, so the head wears a collar no sound one has
            box((0, -0.2, -1.5), (16, 1.8, 0), "gasket"),
            # a wide torn tongue of it blown out and drooping, and a second torn end curled up the side of the head
            tilt(box((2, -0.9, -5), (9.5, 0.9, -1), "gasket"), (5.75, 0, -1), "x", 22.5),
            tilt(box((10.5, 1.2, -4), (14.5, 3, -0.6), "gasket"), (12.5, 1.8, -0.6), "x", -22.5),
            # the hole the gas leaves by, still glowing, and the soot it has blown up the side, widest at the joint
            box((4, 1.8, -0.7), (10, 4.2, 0), "hot"),
            box((2, 4.2, -0.12), (12, 7.5, 0), "black"),
            box((3.5, 7.5, -0.12), (10.5, 11, 0), "black"),
            box((5.5, 11, -0.12), (8.5, 13.5, 0), "black"),
        ]

    gasket = []
    for turns in range(4):
        for element in north_side():
            turned = quarter(element, turns)
            # The collar and tongues stand out past the block, so each face's patch is worked out again from where it ended up
            for face, data in turned["faces"].items():
                data.pop("uv", None)
                patch = _wrapped_default_uv(turned["from"], turned["to"], face)
                if patch:
                    data["uv"] = patch
            gasket.append(turned)
    write("cylinder_head/blown_gasket", dict(textures, gasket=RED), gasket)

    # A head warped by heat: the joint glowing where it no longer meets the block, a heat-crazed line across the top plate and the
    # scorch round it. Low and thin, so it reads as damage to the head and not as a wreck
    def warped_side():
        return [
            box((1, 0.2, -0.14), (7, 0.9, 0), "hot"),
            box((7, 0.7, -0.12), (15, 1.3, 0), "hot"),
            box((2, 1.3, -0.1), (14, 3, 0), "dark"),
        ]

    warped = []
    for turns in range(4):
        warped += [quarter(e, turns) for e in warped_side()]
    warped += [
        box((1.5, 16, 7), (6.5, 16.25, 8), "hot"),
        box((6.5, 16, 8), (10, 16.25, 9), "hot"),
        box((10, 16, 7.2), (14.5, 16.25, 8.2), "hot"),
        box((3, 16, 5), (13, 16.1, 11), "dark"),
    ]
    write("cylinder_head/warped", textures, warped)

    bore = [
        # the connecting rod, bent in two where it folded
        tilt(box((7, 0, 7.5), (9, 9, 9.5), "iron"), (8, 0, 8.5), "z", 22.5),
        tilt(box((6.6, 8, 6.6), (8.6, 13.5, 8.6), "iron"), (7.6, 9, 7.6), "x", -22.5),
        box((6.4, 11.5, 6.2), (9.4, 14, 9.2), "steel"),
        # the shards of the piston lying in the bore
        tilt(box((3.5, 1.5, 3.5), (7.5, 4.5, 6), "steel"), (5.5, 3, 4.5), "z", -22.5),
        tilt(box((9, 2.5, 9), (12.6, 5.5, 12), "steel"), (10.8, 4, 10.5), "x", 22.5),
        box((9.5, 0, 3.5), (11.5, 1.2, 5.5), "dark"),
    ]
    write("cylinder/wreck", textures, bore)

    # Drawn at the piston's pose, so it turns with it: the crown's rim scorched black, and a ring of heat colour just under the rings
    seized = [
        # the skirt blackened where it dragged, a wide ring of heat colour round the crown, and a glowing seam where it has welded to the bore
        box((2.8, -0.5, 2.8), (13.2, 4.0, 13.2), "dark"),
        box((2.9, 4.7, 2.9), (13.1, 5.4, 13.1), "hot"),
        box((6, -1.5, 2.7), (8.2, 5, 3.0), "hot"),
        box((9.5, -1.5, 13), (11.2, 4, 13.3), "hot"),
    ]
    write("cylinder/seized_ring", textures, seized)

    # A failed set of apex seals, in the housing's own frame (+Y along the shaft): thick pale strips of ceramic seal sprung out
    # across each face, bright against the copper and brass, with the soot of the blow-by on the rims either side. Every side has them, because the shaft can lie any way
    # and pipes hide faces. All of it stays out of the rotor's sweep, which the other rotors on the shaft may still be turning.
    def seal_side():
        return [
            # a thick length of seal sprung out through the gap and standing across the face, and the end snapped off it
            tilt(box((6.8, 1, -3.6), (9.2, 15, -1.8), "seal"), (8, 8, -2.7), "z", 45),
            tilt(box((10.8, 1.5, -4.6), (13, 7.5, -3), "seal"), (11.9, 4.5, -3.8), "x", -22.5),
            # the glowing break where it let go
            box((6.4, 6.4, -4.2), (9.6, 9.6, -3.6), "hot"),
            # a length of the bottom rim torn outward by it
            tilt(box((1.5, 0.4, -3.4), (7, 2.4, -0.2), "rim"), (4.25, 1.4, -0.2), "x", 22.5),
            # soot blown across both rims and past them
            box((1, 0, -0.16), (15, 2.6, 0), "black"),
            box((2.5, 13.4, -0.16), (13.5, 16, 0), "black"),
        ]

    seals = []
    for turns in range(4):
        for element in seal_side():
            turned = quarter(element, turns)
            # The strips stand out past the block, so each face's patch is worked out again from where it ended up
            for face, data in turned["faces"].items():
                data.pop("uv", None)
                patch = _wrapped_default_uv(turned["from"], turned["to"], face)
                if patch:
                    data["uv"] = patch
            seals.append(turned)
    write("rotor_housing/wreck", dict(textures, seal=QUARTZ, rim=CUT_COPPER), seals)
