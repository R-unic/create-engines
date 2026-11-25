# AI use and what is guaranteed

This page says plainly how the mod is made, what you can count on, and what you can't. The rules it summarises live in
[STABILITY.md](STABILITY.md), [COMPATIBILITY.md](COMPATIBILITY.md) and [API.md](API.md). Where they disagree with this page,
they are right and this page is stale.

## How AI is used

**Nearly everything is written by an AI (Claude) at my direction:** the Java code, the block models, textures, sounds,
Ponder scenes, tests, docs, changelogs, and the trailer's music. Almost every commit carries a `Co-Authored-By: Claude` line,
so the history shows it.

**What I do:**

- Decide what the mod is: which features go in, how progression works, what gets cut, and how it should look and sound.
- Play it. I have played, tested and reworked almost all of it over many passes, and I turn down work that doesn't fit
  (an earlier 3D pixel-art style was thrown out, for example).
- Approve anything that changes how an existing build behaves. A red test is never that approval; only my say-so, naming
  the change, is, and it is written into that release's record.
- Do the hands-on pass before every release (build an engine, listen to it, look at the new blocks, open an old world)
  and sign the release record. The AI never ticks those boxes, and tags a release only when I ask, after I have signed.

**What the AI does:**

- Writes the code and assets, and the tests that come with them, in the same change.
- Runs the release gate (unit tests, in-world GameTests, a full asset and Ponder audit, mutation testing, compatibility
  checks against saves from earlier versions) and fills in the machine results.
- Reports what actually ran. "Compiled" is not "tested", "tests passed" is not "works in game". Something built but not yet
  seen in a real game is marked that way in the roadmap and the release record, not passed off as done.

**What the AI does not do:**

- Run inside the mod. The jar you download contains no AI model and calls no AI service. Nothing is sent anywhere.
- Ship anything on its own. Every release is merged, played and signed by me.

**Generated assets:** models, textures and sounds are produced by scripts in `tools/` (for example `tools/generate_models.py`)
that the AI writes and I review in game. Textures are built from Create's own so the mod looks like Create.

## What is guaranteed

Within a major version (2.x, until 3.0):

- **Your world keeps loading.** No block, item, fluid, block state, saved key or recipe that an earlier 2.x release put in a
  world is renamed or removed. Saves from 1.x still load through 2.0's migration. Tests load engines saved by earlier
  releases on every build ([COMPATIBILITY.md](COMPATIBILITY.md#what-guards-it) lists which).
- **Your factory keeps working the same.** Power, fuel burn, boost, flow rates and recipe results for an existing build do not
  change. New behaviour comes as a new block, item or config option your existing builds don't use. The rare exception
  needs my explicit approval and is named in the changelog and the release record. A test suite pins the torque curves and
  tick-by-tick behaviour of hundreds of builds, so a change that moves them fails the build.
- **Your settings and integrations keep working.** Config keys and their ranges, advancement IDs, data-map formats and the
  public API stay stable. The API has its own version number and rules ([API.md](API.md)).
- **Every release goes through the same gate,** patch releases included ([STABILITY.md](STABILITY.md)). The publish workflow
  refuses to upload a release without a complete, signed record in `docs/releases/`, so you can read exactly what was
  checked for any version, and what wasn't.
- **The changelog is written from the real diff,** not from commit names. Every changed behaviour and every new ID, key and
  file format is listed.
- **A reported regression gets a test that reproduces it before it gets a fix,** and the fix ships through the same gate.

2.0 was the one release that renamed things (advancement IDs, config files). It carried your advancements, config and
casing dye across. A future 3.0 may rebalance or rename again; that will be said plainly when it comes.

## What is not guaranteed

- **Internal code.** Anything outside `com.createengines.api` may change in any release. Addons should use only the API.
- **Every setup.** Other GPUs, drivers, operating systems, Java versions and mod combinations beyond the supported ones are
  not tested. Supported versions are in the [README](../README.md).
- **Long sessions and big servers.** The automated runs are short. A slow leak, or a bug that needs two players or hours of
  play, can get through.
- **Taste.** Sound that is technically correct can still be unpleasant, and a model can pass every check and still look off.
  Only a person catches that, and I am one person.
- **Bugs nobody knew to look for.** A green gate means nothing is broken in a way we know how to check. When something gets
  through anyway, the check that would have caught it is added first, then the fix.

If something breaks, please report it on the [issue tracker](https://github.com/R-unic/create-engines/issues) or the
[Discord](https://discord.gg/QYVpy2dqGE).
