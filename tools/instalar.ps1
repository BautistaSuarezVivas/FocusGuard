# Instalador de FocusGuard: instala el APK en el celular conectado por USB (o depuración
# inalámbrica) y deja todo configurado, sin tener que entrar a los menús de Android:
#   1. Instala la app por ADB (así Android 13+ no marca el servicio como "ajuste restringido").
#   2. Habilita el servicio de accesibilidad de FocusGuard (conserva los otros que tengas).
#   3. Excluye FocusGuard de la optimización de batería.
#   4. Otorga WRITE_SECURE_SETTINGS para que FocusGuard pueda reactivar su servicio solo.
# Uso: doble clic en instalar.bat (o: powershell -File tools\instalar.ps1 [-Apk ruta.apk] [-Serial id])
param(
    [string]$Apk,
    [string]$Serial,
    [switch]$SinPausa
)

# 'Continue': los comandos de adb escriben avisos en stderr; los resultados se verifican a mano.
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
$pkg = 'com.personal.focusguard'
$svc = "$pkg/$pkg.service.FocusAccessibilityService"

function Paso($n, $texto) { Write-Host ""; Write-Host "[$n] $texto" -ForegroundColor Cyan }
function Ok($texto) { Write-Host "    OK  $texto" -ForegroundColor Green }
function Aviso($texto) { Write-Host "    !!  $texto" -ForegroundColor Yellow }
function Fallo($texto) {
    Write-Host "    ERROR  $texto" -ForegroundColor Red
    if (-not $SinPausa) { Read-Host "Presioná Enter para salir" }
    exit 1
}

# --- ADB -------------------------------------------------------------------------------------
function Find-Adb {
    $candidatos = @(
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
        "$env:USERPROFILE\Android\Sdk\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$root\tools\platform-tools\adb.exe"
    )
    foreach ($c in $candidatos) { if ($c -and (Test-Path $c)) { return $c } }
    $enPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($enPath) { return $enPath.Source }
    return $null
}

Paso 1 "Buscando ADB (Android Platform Tools)"
$adb = Find-Adb
if (-not $adb) {
    Aviso "No se encontró ADB en esta PC."
    $r = Read-Host "    ¿Descargarlo de Google (dl.google.com, ~7 MB) en la carpeta tools? (S/N)"
    if ($r -notmatch '^[sS]') { Fallo "Sin ADB no se puede instalar. Instalá 'Android SDK Platform-Tools' y volvé a intentar." }
    $zip = Join-Path $env:TEMP 'platform-tools.zip'
    Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
    Expand-Archive $zip -DestinationPath "$root\tools" -Force
    $adb = "$root\tools\platform-tools\adb.exe"
}
Ok $adb

# --- APK -------------------------------------------------------------------------------------
if (-not $Apk) {
    $Apk = Get-ChildItem "$root\dist\FocusGuard-*.apk" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'debug' } | Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $Apk -or -not (Test-Path $Apk)) { Fallo "No se encontró el APK en la carpeta dist." }
Ok "APK: $Apk"

# --- Dispositivo -----------------------------------------------------------------------------
Paso 2 "Conectando con el celular"
Write-Host "    Si es la primera vez:"
Write-Host "      a) Ajustes > Acerca del teléfono > tocá 7 veces 'Número de compilación'."
Write-Host "      b) Ajustes > Sistema > Opciones de desarrollador > activá 'Depuración por USB'."
Write-Host "      c) Conectá el cable y aceptá '¿Permitir depuración USB?' en el celular."
& $adb start-server | Out-Null
while ($true) {
    $lineas = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }
    $listos = @($lineas | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] })
    $sinPermiso = @($lineas | Where-Object { $_ -match "unauthorized" })
    if ($Serial) { $listos = @($listos | Where-Object { $_ -eq $Serial }) }
    if ($listos.Count -eq 1) { $Serial = $listos[0]; break }
    if ($listos.Count -gt 1) {
        Write-Host "    Hay varios dispositivos conectados:"; $listos | ForEach-Object { Write-Host "      $_" }
        $Serial = Read-Host "    Escribí el id del que querés usar"
        continue
    }
    if ($sinPermiso.Count -gt 0) { Aviso "Aceptá el aviso de depuración USB en la pantalla del celular..." }
    else { Write-Host "    Esperando un celular conectado..." }
    Start-Sleep 3
}
function Adb { & $adb -s $Serial @args }
$modelo = (Adb shell getprop ro.product.model).Trim()
$version = (Adb shell getprop ro.build.version.release).Trim()
Ok "$modelo (Android $version)"

# --- Instalación -----------------------------------------------------------------------------
Paso 3 "Instalando FocusGuard"
$out = Adb install -r $Apk 2>&1 | Out-String
if ($out -notmatch 'Success') {
    if ($out -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Fallo "Ya hay un FocusGuard firmado con otra clave. Desinstalalo del celular y volvé a ejecutar."
    }
    Fallo "La instalación falló:`n$out"
}
Ok "Instalado"

# --- Permisos --------------------------------------------------------------------------------
Paso 4 "Permiso de reactivación automática (WRITE_SECURE_SETTINGS)"
$g = Adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS 2>&1 | Out-String
if ($g -match 'Exception|Error|denied') {
    Aviso "No se pudo otorgar (opcional). En Xiaomi activá también 'Depuración USB (ajustes de seguridad)'."
} else { Ok "Otorgado: FocusGuard podrá volver a encender su servicio si Android lo apaga" }

Paso 5 "Habilitando el servicio de accesibilidad"
$actual = (Adb shell settings get secure enabled_accessibility_services | Out-String).Trim()
$lista = @()
if ($actual -and $actual -ne 'null') { $lista = @($actual -split ':' | Where-Object { $_ -and $_ -ne $svc }) }
# Quitar y volver a poner fuerza a Android a enlazar el servicio (necesario tras actualizar).
$sinFG = ($lista -join ':')
if ($sinFG) { Adb shell settings put secure enabled_accessibility_services "$sinFG" | Out-Null }
else { Adb shell settings delete secure enabled_accessibility_services | Out-Null }
Start-Sleep 1
$conFG = (@($lista) + $svc) -join ':'
Adb shell settings put secure enabled_accessibility_services "$conFG" | Out-Null
Adb shell settings put secure accessibility_enabled 1 | Out-Null
$enlazado = $false
for ($i = 0; $i -lt 10 -and -not $enlazado; $i++) {
    Start-Sleep 1
    $enlazado = (Adb shell dumpsys accessibility | Out-String) -match 'Bound services:\{Service\[label=FocusGuard'
}
if ($enlazado) { Ok "Servicio activo" } else { Aviso "Habilitado, pero Android todavía no lo inició. Abrí FocusGuard para verificar." }

Paso 6 "Excluyendo FocusGuard de la optimización de batería"
Adb shell dumpsys deviceidle whitelist "+$pkg" | Out-Null
Ok "Listo (en Xiaomi/Samsung/Huawei revisá además las opciones propias de batería, ver docs)"

Paso 7 "Abriendo FocusGuard"
Adb shell am start -n "$pkg/.ui.MainActivity" 2>&1 | Out-Null
Ok "Listo. Ya podés desconectar el cable y desactivar la depuración USB si querés."
Write-Host ""
if (-not $SinPausa) { Read-Host "Presioná Enter para cerrar" }
