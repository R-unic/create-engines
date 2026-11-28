"""Writes the block models under src/main/resources/assets/create_engines/models/block/.

Run from the repository root: python tools/generate_models.py
Everything here is built from boxes in 1/16 block units, textured with Create's casings and shafts and with the metals
drawn by tools/generate_textures.py, which are made to sit next to them. Parts that
the engine animates (piston, rod, valve, crank, cam) keep the pivot and extent the renderers expect: they are drawn
about the same axis and stay inside the same envelope, with detail added inside it.

The models are drawn by the modules in tools/models/, one per subject.
"""

from models.airflow import air_intake, exhaust_outlet, framed_blueprint, maker_plate, pipes, rain_cap
from models.casing import engine_casing
from models.crank import cam_lobe, camshaft, crank_throw, crankshaft
from models.cylinder import cylinder, piston, rod
from models.electric import alternator, battery, starter
from models.fittings import air_filter, black_box, dyno, intercooler, jerry_can, muffler, soot, soot_trap
from models.flywheel import cast_flywheel, dual_mass_flywheel
from models.head import head, valve
from models.induction import supercharger, turbo_port, turbocharger, variable_supercharger, variable_turbocharger
from models.plant import flare_stack, radiator, refinery, sump, transmission
from models.precision import (
    bank_builder, compression_pistons, forged_piston, governor, headphones, piston_rings, precision_cylinder,
    precision_head, precision_injector_item, timing_tool)
from models.rotor import rotor_housing
from models.valvegear import pushrod, rocker
from models.wrecks import wear_wrecks


def main():
    rotor_housing()
    precision_cylinder()
    precision_head()
    forged_piston()
    compression_pistons()
    piston_rings()
    precision_injector_item()
    governor()
    timing_tool()
    bank_builder()
    headphones()
    crankshaft()
    crank_throw()
    camshaft()
    cam_lobe()
    cylinder()
    piston()
    rod()
    head()
    valve()
    wear_wrecks()
    pushrod()
    rocker()
    air_intake()
    exhaust_outlet()
    rain_cap()
    maker_plate()
    framed_blueprint()
    pipes()
    starter()
    battery()
    alternator()
    turbocharger()
    turbo_port()
    variable_turbocharger()
    supercharger()
    variable_supercharger()
    cast_flywheel()
    dual_mass_flywheel()
    dyno()
    black_box()
    intercooler()
    air_filter()
    transmission()
    muffler()
    engine_casing()
    sump()
    radiator()
    refinery()
    flare_stack()
    soot_trap()
    soot()
    jerry_can()


main()
