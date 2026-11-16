# Records world-corpus fixtures (docs/ROADMAP.md, Verification track item 4): a running diesel, spark and rotary engine,
# saved by an OLD release's own code, into src/test/resources/compat/world/.
#
#   scripts/record-corpus.ps1 v1.0.0 v1.1.0 v1.2.0
#
# For each tag it unpacks that tag's source (git archive, so this checkout and its history are untouched) into a folder
# next to the checkout, drops in scripts/CorpusRecorder.java, runs that release's GameTest server with the recorder
# switched on, and copies the three files out. A fixture that already exists is never overwritten: once a release
# has shipped, its saves are history. Needs the same offline Gradle cache as the main build.
param(
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
    [string[]] $Tags
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$parent = Split-Path -Parent $repo
$out = Join-Path $repo 'src/test/resources/compat/world'
New-Item -ItemType Directory -Force $out | Out-Null

foreach ($tag in $Tags) {
    $version = $tag.TrimStart('v').Replace('.', '_')
    $kinds = 'diesel', 'spark', 'rotary'
    $missing = $kinds | Where-Object { -not (Test-Path (Join-Path $out "engine_v${version}_$_.snbt")) }
    if (-not $missing) { Write-Host "$tag : all fixtures exist, skipping"; continue }

    $work = Join-Path $parent "create-engines-corpus-$tag"
    if (Test-Path $work) { Remove-Item -Recurse -Force $work }
    New-Item -ItemType Directory $work | Out-Null
    $zip = Join-Path $work 'source.zip'
    git -C $repo archive --format=zip -o $zip $tag
    Expand-Archive $zip -DestinationPath $work
    Remove-Item $zip
    Copy-Item (Join-Path $PSScriptRoot 'CorpusRecorder.java') (Join-Path $work 'src/main/java/com/createengines/gametest/CorpusRecorder.java')

    $staging = Join-Path $work 'corpus-out'
    $env:CE_CORPUS_OUT = $staging
    $env:CE_CORPUS_VERSION = "v$version"
    Push-Location $work
    try {
        & (Join-Path $repo 'gradlew.bat') runGameTestServer --offline
        # An old release may fail one of its own tests (v1.0.0 does); only the files the recorder wrote matter here
        if ($LASTEXITCODE -ne 0) { Write-Warning "$tag : its GameTest run exited $LASTEXITCODE (its own tests); judging by the recorded files" }
    } finally {
        Pop-Location
        Remove-Item Env:CE_CORPUS_OUT, Env:CE_CORPUS_VERSION -ErrorAction SilentlyContinue
    }
    foreach ($kind in $missing) {
        $file = Join-Path $staging "engine_v${version}_$kind.snbt"
        if (-not (Test-Path $file)) { throw "$tag : the recorder wrote no $file" }
        Copy-Item $file (Join-Path $out (Split-Path -Leaf $file))
        Write-Host "$tag : recorded $kind"
    }
}
