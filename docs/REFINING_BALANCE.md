# Refining balance (1.3)

The figures behind the shipped column recipes in `data/create_engines/recipe/refining/`. `RefiningBalanceTest` reads
those files and holds every statement below; `ProgressionBalanceTest` holds the column sizes against engine thirst.
If a number here moves, a test fails first.

## What a bucket costs, by route

A bucket is 1000 mB of product. Basin figures are what ships today and, for diesel and gasoline, what ships in 1.3: no
basin recipe changes except the two ethanol ones.

| Fuel | Route | Feed (mB) | Catalyst items |
|---|---|---|---|
| Diesel | Basin (today and 1.3) | 1000 plant oil | 4 coal |
| Diesel | Column | 909 plant oil | 0.91 coal |
| Gasoline | Basin (today and 1.3) | 1000 diesel | 4 blaze powder |
| Gasoline | Column | 909 diesel | 0.91 blaze powder |
| Gasoline from seed oil | Basin, both steps | 1000 plant oil | 4 coal + 4 blaze powder |
| Gasoline from seed oil | Column, both steps | 826 plant oil | 0.91 coal + 0.91 blaze powder |
| Ethanol, heated | Basin today | 5000 sugar mash | 20 apples or mushrooms |
| Ethanol, heated | Basin in 1.3 (35 mB per 250) | 7143 sugar mash | 29 apples or mushrooms |
| Ethanol, heated | Column | 5882 sugar mash | 5.9 nether wart |
| Ethanol, superheated | Basin today | 2500 sugar mash | 10 apples or mushrooms, 10 sugar |
| Ethanol, superheated | Basin in 1.3 (70 mB per 250) | 3571 sugar mash | 14 apples or mushrooms, 14 sugar |
| Ethanol, superheated | Column | 2941 sugar mash | 2.9 nether wart |

Read it as: diesel and gasoline factories keep what they make, and a column makes the same bucket from about a tenth less
feed and a quarter of the catalyst. Ethanol is the one fuel that gets dearer: the column sits between today's basin and
the weakened basin, so a player who moves to the column makes more than the basin gives from 1.3 on, and a little less
than the old basin gave. Nether wart is the catalyst, so ethanol also asks for a Nether trip.

The basin ethanol recipes were cut in 1.3.0 together with the column, and biodiesel asks for 1.43 times less ethanol to
match (25 mB became 17), so Glycerin, and the coolant and engine oil made from it, cost what they did before.

## Speed

A column passes `0.25 mB/tick x width x width x height` of feed at a route's full speed (width 1 to 3, so a 1x1x3 column
is 0.75 mB/tick). Each route has its own speed against that: diesel 1.0, gasoline 0.75, ethanol 0.5.

Measured engine thirst, mB per second, from the dynamometer harness in `ProgressionBalanceTest`:

| Engine | Plant oil | Diesel | Gasoline | Ethanol |
|---|---|---|---|---|
| 6 cylinders, crude | 2.0 | 2.1 | 2.4 | 3.9 |
| 12 cylinders, crude | 4.0 | 4.2 | 4.7 | 7.7 |
| 12 cylinders, precision, two turbos, intercooler | | | 26.0 | 39.3 |

Product per second from a column (feed rate times speed times yield):

| Column | Diesel | Gasoline | Ethanol, heated | Ethanol, superheated |
|---|---|---|---|---|
| 1 wide, 3 tall | 18 | 12 | 3.2 | 6.4 |
| 2 wide, 3 tall | 73 | 49 | 13 | 26 |
| 3 wide, 6 tall | 297 | 198 | 52 | 103 |

(The 3x6 ethanol figures assume feed to match: 100 mB/s or more of mash is the real limit, not the column.)

What the tests hold:

- The narrowest column (1x3) feeds an early 12-cylinder on diesel or gasoline twice over, and not a boosted precision
  V12. A 2-wide column does feed the boosted V12 on gasoline.
- A 2x3 superheated column feeds an unboosted 12 on ethanol, and a heated one does not do it twice over. A 3x6
  superheated column feeds the boosted V12 on ethanol, and the same column heated cannot.
- So superheat matters for ethanol, a boosted ethanol engine wants a big column, and nothing is ever starved by
  gasoline's or diesel's column rate.

## Not done

The tier engines were not rerun on column fuel: an engine burns what it is given and the fuel's own numbers do not
change, so the tiers cannot move. What changes is what the fuel costs to make, and that is above.
