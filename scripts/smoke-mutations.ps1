<#
.SYNOPSIS
  Proves the smoke harness can fail: breaks a PRIVATE COPY of the mod in sixteen known ways and checks each break is caught.

.DESCRIPTION
  A harness that has only ever passed has proved nothing. This breaks things on purpose (a deleted loot table, a missing
  translation, a missing texture, a Ponder page that throws, a block entity that chokes on hostile data, a recipe whose
  ingredient matches nothing, a sound missing from sounds.json, a direct-memory leak like the 1.2.0 bug, a slow engine tick,
  a missing particle description, the multipart models no longer shared, a model face running outside its texture) and runs the smoke layers, which must go red with the right check named for each.

  It edits files in -Copy, never in a real checkout, and refuses to run where a .git folder exists.

  Make the copy (nothing else may use it while this runs):
    robocopy <checkout> <copy> /MIR /XD run build .gradle .git

  Then, from anywhere:
    ./scripts/smoke-mutations.ps1 -Copy <copy> -Set server    # GameTests: loot, hostile NBT, recipe, slow tick (about 6 min)
    ./scripts/smoke-mutations.ps1 -Copy <copy> -Set client    # client audit: lang, model, Ponder, sound, memory leak (about 3 min)
    ./scripts/smoke-mutations.ps1 -Copy <copy> -Set threads   # client audit: a voice that never comes back after a sound reload
    ./scripts/smoke-mutations.ps1 -Copy <copy> -Set jar       # jarServer: a class the server needs is missing from the jar
    ./scripts/smoke-mutations.ps1 -Copy <copy> -Set crash     # a missing particle sprite crashes the game; the cause must survive
  Read the output of each: every line starting EXPECT must appear among the failures. Re-run robocopy to undo.

  Last verified 2026-11-27: all caught. The slow-tick break of the server set can hold the run past the task's 20-minute limit; copy
  EngineController.java back and run the set again to see the others. If one stops being caught, a check has been weakened; fix the check, not this list.
#>
param(
    [Parameter(Mandatory = $true)][string]$Copy,
    [Parameter(Mandatory = $true)][ValidateSet('server', 'client', 'crash', 'threads', 'jar')][string]$Set
)
$ErrorActionPreference = 'Stop'
$d = (Resolve-Path $Copy).Path
if (Test-Path (Join-Path $d '.git')) { throw "Refusing to mutate ${d}: it has a .git folder, so it is a real checkout. Use a robocopy copy." }
if (-not (Test-Path (Join-Path $d 'build.gradle'))) { throw "${d} is not a copy of this project" }

function Edit-File($path, $find, $replace) {
    $text = [System.IO.File]::ReadAllText($path)
    # Source files are CRLF or LF depending on what last touched them; anchors are written with LF
    if ($text.Contains("`r`n")) {
        $find = $find.Replace("`r`n", "`n").Replace("`n", "`r`n")
        $replace = $replace.Replace("`r`n", "`n").Replace("`n", "`r`n")
    }
    if (-not $text.Contains($find)) { throw "mutation anchor not found in ${path}: $find" }
    [System.IO.File]::WriteAllText($path, $text.Replace($find, $replace))
}

$expect = @()
switch ($Set) {
    'server' {
        Remove-Item "$d\src\generated\resources\data\create_engines\loot_table\blocks\sump.json"
        $expect += 'EXPECT [data] block sump has a loot table'
        Edit-File "$d\src\main\java\com\createengines\content\battery\BatteryBlockEntity.java" 'float read = tag.getFloat("Charge");' 'if (tag.get("Charge") instanceof net.minecraft.nbt.StringTag) throw new IllegalStateException("mutation: hostile data"); float read = tag.getFloat("Charge");'
        $expect += 'EXPECT [lifecycle] battery [] - IllegalStateException: mutation: hostile data'
        Edit-File "$d\src\main\resources\data\create_engines\recipe\mixing\biodiesel.json" '"item": "minecraft:bone_meal"' '"tag": "create_engines:nothing_here"'
        $expect += 'EXPECT [data] recipe mixing/biodiesel has no empty ingredient'
        Edit-File "$d\src\main\java\com\createengines\engine\EngineController.java" "public void tick() {`n`t`tLevel level = primary.getLevel();" "public void tick() {`n`t`tlong mutationEnd = System.nanoTime() + 700_000L; while (System.nanoTime() < mutationEnd) { }`n`t`tLevel level = primary.getLevel();"
        $expect += 'EXPECT [perf] 16 engines cost under 2 ms a tick'
        Edit-File "$d\src\main\java\com\createengines\content\battery\BatteryBlockEntity.java" 'float read = tag.getFloat("Charge");' 'if (Float.isNaN(tag.getFloat("Charge"))) throw new IllegalStateException("mutation: bad number"); float read = tag.getFloat("Charge");'
        $expect += 'EXPECT [hostile] battery state 0 NOT_A_NUMBER - IllegalStateException: mutation: bad number'
        Edit-File "$d\src\main\java\com\createengines\engine\sim\Tuning.java" 'public static volatile boolean wearMode = false;' "public static volatile boolean wearMode = false;`n`tpublic static volatile boolean mutationUnswept = false;"
        $expect += 'EXPECT [tuning] every Tuning setting is swept'
        $task = 'runGameTestServer'
    }
    'client' {
        $lang = "$d\src\generated\resources\assets\create_engines\lang\en_us.json"
        [System.IO.File]::WriteAllLines($lang, ([System.IO.File]::ReadAllLines($lang) | Where-Object { $_ -notmatch '"block.create_engines.sump":' }))
        $expect += 'EXPECT [lang] block sump has a translation'
        Edit-File "$d\src\main\resources\assets\create_engines\models\block\sump\block.json" '"pan": "create_engines:block/black_oxide"' '"pan": "create_engines:block/does_not_exist"'
        $expect += 'EXPECT [models] block sump - state Block{create_engines:sump} has a face with the missing texture (unculled)'
        $expect += 'EXPECT [render] gallery_0 has no missing-texture pink'
        Edit-File "$d\src\main\java\com\createengines\ponder\EngineScenes.java" "CreateSceneBuilder scene = new CreateSceneBuilder(builder);`n`t`tscene.title(`"sump`", `"An Oil Sump`");" "CreateSceneBuilder scene = new CreateSceneBuilder(builder);`n`t`tif (builder != null) throw new IllegalStateException(`"mutation: broken storyboard`");`n`t`tscene.title(`"sump`", `"An Oil Sump`");"
        $expect += 'EXPECT [ponder] sump (sump) plays'
        Edit-File "$d\src\main\resources\assets\create_engines\sounds.json" '"exhaust_pop": {' '"exhaust_pop_renamed": {'
        $expect += 'EXPECT [sound] event exhaust_pop is in sounds.json'
        Edit-File "$d\src\main\java\com\createengines\client\SharedCasingModels.java" 'if (own != canonical) {' 'if (false) {'
        $expect += "EXPECT [models] every multipart state of the mod shares its block's one baked model"
        # A face whose texture coordinates run past the edge of its sprite shows the sprite beside it (the 1.4.0 purple rockers)
        Edit-File "$d\src\main\resources\assets\create_engines\models\block\cylinder_head\rocker.json" "`"uv`": [`n            0,`n            0,`n            16,`n            16`n          ]" "`"uv`": [`n            0,`n            0,`n            24,`n            16`n          ]"
        $expect += 'EXPECT [models] partial model ROCKER keeps its texture coordinates inside its sprites'
        Edit-File "$d\src\main\java\com\createengines\content\EngineNotePcm.java" 'ByteBuffer read(int size) {' "static final java.util.List<ByteBuffer> LEAK = new java.util.ArrayList<>();`n`tByteBuffer read(int size) {`n`t`tLEAK.add(ByteBuffer.allocateDirect(256 * 1024));"
        $expect += 'EXPECT [perf] direct memory does not climb while engines run'
        Edit-File "$d\src\main\java\com\createengines\content\WornPartItem.java" 'super.appendHoverText(stack, context, tooltip, flag);' 'super.appendHoverText(stack, context, tooltip, flag); tooltip.add(net.minecraft.network.chat.Component.translatable("create_engines.mutation.missing"));'
        $expect += 'EXPECT [gui] item piston tooltip'
        Edit-File "$d\src\main\java\com\createengines\content\muffler\SootTrapBlockEntity.java" 'public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {' 'public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) { if (tooltip != null) return true;'
        $expect += 'EXPECT [gui] soot_trap readouts in the gallery'
        $task = 'runSmokeAudit'
    }
    'threads' {
        # A note the sound engine dropped is never forgotten: the dead ones hold all three voices, silent after a reload.
        # (Only stopping an engine from replacing its own note is not enough: another engine's clean-up forgets it.)
        Edit-File "$d\src\main\java\com\createengines\content\EngineNoteSound.java" 'boolean dropped = VoiceClock.settled(now, note.startedAt) && (note.isStopped() || !MachineLoopSound.stillPlaying(sounds, note));' 'boolean dropped = false;'
        $expect += 'EXPECT [threads] an engine note comes back after the sound engine reloaded'
        $task = 'runSmokeAudit'
    }
    'jar' {
        # A package the server needs is left out of the jar: the development runs never notice, the jar's server does not start
        Edit-File "$d\build.gradle" "    exclude 'com/createengines/gametest/**', 'com/createengines/smoke/**'," "    exclude 'com/createengines/content/battery/**', 'com/createengines/gametest/**', 'com/createengines/smoke/**',"
        $expect += 'EXPECT [server] the run stopped'
        $task = 'jarServer'
    }
    'crash' {
        Rename-Item "$d\src\main\resources\assets\create_engines\particles\gas.json" 'gas.json.disabled'
        $expect += 'EXPECT [particles] gas has a particle description'
        $expect += 'EXPECT [run] the run did not complete'
        $task = 'runSmokeAudit'
    }
}
Write-Output "Mutated $d for set '$Set'. Now run, from the copy:  ./gradlew $task --offline"
Write-Output "Every one of these must appear among the failures:"
$expect | ForEach-Object { Write-Output "  $_" }
