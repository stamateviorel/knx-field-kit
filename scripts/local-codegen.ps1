param(
    [Parameter(Mandatory = $true)]
    [string]$PromptFile,

    [Parameter(Mandatory = $true)]
    [string]$OutFile,

    [Parameter(Mandatory = $false)]
    [string[]]$ContextFiles = @(),

    [Parameter(Mandatory = $false)]
    [string]$Model = "qwen2.5-coder:7b"
)

$ErrorActionPreference = "Stop"
$starter = "C:\Users\stama\.gemini\antigravity\scratch\local-ai\start-headless-ollama.ps1"
if (Test-Path $starter) {
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $starter | Out-Null
}

$pyArgs = @("$PSScriptRoot\local_codegen.py", "--prompt-file", $PromptFile, "--out-file", $OutFile, "--model", $Model)
if ($ContextFiles.Count -gt 0) {
    $pyArgs += "--context"
    $pyArgs += $ContextFiles
}
& python.exe @pyArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
