# PaiCLI PowerShell 入口函数
# 用法: 在 PowerShell profile 中 dot-source 此文件
#
# 添加到 $PROFILE:
#   Add-Content -Path $PROFILE -Value "`n. D:\Code\MyProject\paicli\bin\paicli.ps1"

$Script:PAICLI_DIR = Split-Path $PSScriptRoot -Parent
$Script:PAICLI_JAR = Join-Path $Script:PAICLI_DIR "target\paicli-1.0-SNAPSHOT.jar"
$Script:PAICLI_ENV = Join-Path $Script:PAICLI_DIR ".env"

# 自动加载项目 .env 到进程环境变量，使 API Key 不受运行目录影响
if (Test-Path $Script:PAICLI_ENV) {
    Get-Content $Script:PAICLI_ENV | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $eq = $line.IndexOf('=')
            if ($eq -gt 0) {
                $key = $line.Substring(0, $eq).Trim()
                $value = $line.Substring($eq + 1).Trim()
                if ($key -and $value) {
                    # 去除引号包裹
                    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                        $value = $value.Substring(1, $value.Length - 2)
                    }
                    # 不覆盖已存在的环境变量（用户显式设置优先级更高）
                    if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
                        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
                    }
                }
            }
        }
    }
}

function paicli {
    if (-not (Test-Path $Script:PAICLI_JAR)) {
        Write-Error "PaiCLI jar not found at $Script:PAICLI_JAR"
        Write-Host "Please run 'mvn clean package' first."
        return
    }
    java -jar $Script:PAICLI_JAR @args
}
