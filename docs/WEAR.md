# Wear and tear: the numbers

These are the wear mode's figures as the code gives them today, every one a first pass for the author to tune. This file is written by
`WearTableTest` from the real rules, and the test fails if it no longer matches them: change a number in `Wear.java`, run the
test with `WEAR_TABLE_REGEN=1`, and the diff of this file is the review.

## How long a part lasts

Hours of running from new to spent (wear 1), one engine on good oil, no boost. Wear grows with the square of speed up to four times the
reference speed of 128 RPM, with a floor of 2% when it is barely turning. Heat adds 2.5 per unit over normal and 10 more per unit past hot (1.0), no oil up to five times. A rod's figures are at half load.

| Part | 32 RPM | 64 RPM | 128 RPM | 192 RPM | 256 RPM |
| --- | ---: | ---: | ---: | ---: | ---: |
| Piston, warm | 80.0 | 20.0 | 5.0 | 2.2 | 1.3 |
| Piston, hot | 53.3 | 13.3 | 3.3 | 1.5 | 0.8 |
| Piston, no oil | 16.0 | 4.0 | 1.0 | 0.4 | 0.3 |
| Forged (precision) piston, warm | 200.0 | 50.0 | 12.5 | 5.6 | 3.1 |
| Connecting rod, warm | 128.0 | 32.0 | 8.0 | 3.6 | 2.0 |
| Connecting rod, no oil | 25.6 | 6.4 | 1.6 | 0.7 | 0.4 |
| Connecting rod, hot | 23.3 | 5.8 | 1.5 | 0.6 | 0.4 |
| Rod, standard, warm, spinning free | 213.3 | 53.3 | 13.3 | 5.9 | 3.3 |
| Rod, standard, warm, full load | 91.4 | 22.9 | 5.7 | 2.5 | 1.4 |
| Rod, light, warm, spinning free | 256.0 | 64.0 | 16.0 | 7.1 | 4.0 |
| Rod, light, warm, full load | 64.0 | 16.0 | 4.0 | 1.8 | 1.0 |
| Rod, heavy, warm, spinning free | 182.9 | 45.7 | 11.4 | 5.1 | 2.9 |
| Rod, heavy, warm, full load | 128.0 | 32.0 | 8.0 | 3.6 | 2.0 |
| Gasket, stock, warm, no boost | 48.0 | 12.0 | 3.0 | 1.3 | 0.8 |
| Gasket, cardboard, warm, no boost | 72.0 | 18.0 | 4.5 | 2.0 | 1.1 |
| Gasket, rubber, warm, no boost | 144.0 | 36.0 | 9.0 | 4.0 | 2.3 |
| Gasket, copper, warm, no boost | 288.0 | 72.0 | 18.0 | 8.0 | 4.5 |
| Gasket, stock, warm, 1 bar of boost | 12.0 | 3.0 | 0.8 | 0.3 | 0.2 |
| Gasket, cardboard, warm, 1 bar of boost | 20.6 | 5.1 | 1.3 | 0.6 | 0.3 |
| Gasket, rubber, warm, 1 bar of boost | 54.0 | 13.5 | 3.4 | 1.5 | 0.8 |
| Gasket, copper, warm, 1 bar of boost | 144.0 | 36.0 | 9.0 | 4.0 | 2.3 |

## What a gasket is worth

A fitted gasket keeps more of each burn's heat than a head with none fitted, with the wear mode on or off. With it on the bonus fades as the
gasket wears, and a worn gasket also loses power. A head with none fitted is the baseline (100%).

| Gasket | Bonus when new | Peak torque, new | Peak torque, half worn | Peak torque, spent |
| --- | ---: | ---: | ---: | ---: |
| none fitted | 0% | 100.0% | - | - |
| stock | 1.0% | 101.2% | 91.2% | 62.6% |
| cardboard | 2.5% | 103.1% | 93.0% | 66.4% |
| rubber | 4.0% | 105.0% | 95.8% | 73.8% |
| copper | 6.0% | 107.5% | 98.9% | 81.3% |

## What wear costs

Peak torque of a four-cylinder gasoline engine on the dyno, as a share of a sound one, with a piston and a stock gasket worn the same. Failure
is not possible below 0.50 wear.

| Wear | Peak torque | Drag on the crank |
| ---: | ---: | ---: |
| 0.00 | 100.0% | 1.00x |
| 0.25 | 95.5% | 1.04x |
| 0.50 | 82.3% | 1.15x |
| 0.75 | 61.5% | 1.34x |
| 1.00 | 34.8% | 1.60x |

## How likely a failure is

The chance in one minute that a warm part fails (a rod is thrown, a gasket blows), by how worn it is and how fast the engine turns.
Heat multiplies it: nothing up to hot, 3 times at critical (1.5), 6 times at 0.2 past it.

| Wear | 32 RPM | 64 RPM | 128 RPM | 192 RPM | 256 RPM |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 0.50 | 0.0% | 0.0% | 0.0% | 0.0% | 0.0% |
| 0.60 | 0.1% | 0.1% | 0.1% | 0.1% | 0.2% |
| 0.70 | 0.8% | 0.8% | 0.8% | 1.2% | 1.5% |
| 0.80 | 2.6% | 2.6% | 2.6% | 3.9% | 5.2% |
| 0.90 | 6.1% | 6.1% | 6.1% | 9.2% | 12.3% |
| 1.00 | 12.0% | 12.0% | 12.0% | 18.0% | 24.0% |

## Heat

Block temperature is in the engine's own units (0 ambient, 0.7 warm, which goggles read as 90 degrees; hot is 120, overheating 140 and critical 165). From critical (1.45) heat breaks things by itself,
twice as fast 0.2 past it.

| Block temperature | Sound gasket blows, in a minute | Seconds to warp a head | Seconds to seize an oiled piston at 128 RPM |
| ---: | ---: | ---: | ---: |
| 1.45 | 15.0% | 180 | 240 |
| 1.55 | 22.5% | 120 | 240 |
| 1.65 | 30.0% | 90 | 240 |

A warped head loses 15% of every burn until it is replaced.

## Spark plugs and turbochargers

A plug lasts 4 hours at the reference speed (a coil wears it 1.5 times as fast, soot up to 2 times). From 0.35 wear it misses sparks, up to 40% of them spent. It never fails outright.
A turbocharger lasts 6 hours at 0.5 bar of boost on warm, clean oil, loses up to 30% of its boost, and past half worn can fail,
after which it makes none until it is replaced.

## Knock

A spark engine whose boost lights the charge early wears its pistons on top of the ordinary wear above, from 4% of the burn lost, and is heard
from 7%. Hours for knock alone to take a standard piston from new to spent; a forged piston lasts 2.5 times as long. It breaks nothing by itself.

| Burn lost | 32 RPM | 64 RPM | 128 RPM | 192 RPM | 256 RPM |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 7% | 569.5 | 142.4 | 35.6 | 15.8 | 8.9 |
| 15% | 42.4 | 10.6 | 2.6 | 1.2 | 0.7 |
| 25% | 11.6 | 2.9 | 0.7 | 0.3 | 0.2 |
| 35% | 5.3 | 1.3 | 0.3 | 0.1 | 0.1 |

## Repair

A Service Kit has 24 uses. It sets wear above 0.10 and up to 0.35 back to 0.10; heavier wear needs a new part.

## Running dry

A running engine whose oil film is below 0.15 counts a cylinder towards seizing, and oil takes the count down 2 seconds for each second. A seized piston is left at least 0.75 worn and its cylinder drags 4 times as hard. It cries out from half way, more often the nearer it is. A forged (precision) piston lasts 2.5 times as long.

Seconds of running dry before a piston seizes:

| Piston | 32 RPM | 64 RPM | 100 RPM | 128 RPM | 192 RPM | 256 RPM |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Standard | 240 | 240 | 154 | 120 | 80 | 60 |
| Forged (precision) | 600 | 600 | 384 | 300 | 200 | 150 |
