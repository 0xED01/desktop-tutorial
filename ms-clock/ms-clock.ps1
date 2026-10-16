# 毫秒级时钟（抢购用）
# - 置顶小窗，显示到毫秒
# - 可与 阿里云NTP / 淘宝服务器 / 拼多多服务器 对时，显示校准后的时间
# - 目标时间倒计时，可在到点前 N 毫秒响提示音
# 运行：双击同目录下的 “启动时钟.bat”

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# 把系统定时器精度提到 1ms，让刷新更顺滑
try {
    Add-Type -Namespace Win32 -Name WinMM -MemberDefinition '[DllImport("winmm.dll")] public static extern uint timeBeginPeriod(uint p);'
    [void][Win32.WinMM]::timeBeginPeriod(1)
} catch {}

# ---------- 时间基准：开机时取一次系统时间，之后用高精度 Stopwatch 递增 ----------
$script:sw = [Diagnostics.Stopwatch]::StartNew()
$script:baseMs = [double]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
$script:offset = 0.0          # 服务器时间 - 本机时间 (ms)
$script:source = '本机时间'
$script:delay = $null
$script:syncing = $false

function Get-LocalMs { $script:baseMs + $script:sw.Elapsed.TotalMilliseconds }
function Get-NowMs  { (Get-LocalMs) + $script:offset }

# ---------- NTP 对时 ----------
function Read-NtpMs([byte[]]$b, [int]$o) {
    $sec  = [double]$b[$o] * 16777216 + [double]$b[$o+1] * 65536 + [double]$b[$o+2] * 256 + [double]$b[$o+3]
    $frac = [double]$b[$o+4] * 16777216 + [double]$b[$o+5] * 65536 + [double]$b[$o+6] * 256 + [double]$b[$o+7]
    ($sec - 2208988800) * 1000 + $frac * 1000 / 4294967296
}

function Sync-Ntp([string[]]$servers) {
    foreach ($server in $servers) {
        $udp = $null
        try {
            $udp = New-Object Net.Sockets.UdpClient
            $udp.Client.ReceiveTimeout = 1500
            $udp.Connect($server, 123)
            $best = $null
            for ($i = 0; $i -lt 6; $i++) {
                $req = New-Object byte[] 48
                $req[0] = 0x1B
                $t1 = Get-LocalMs
                [void]$udp.Send($req, 48)
                $ep = New-Object Net.IPEndPoint([Net.IPAddress]::Any, 0)
                $resp = $udp.Receive([ref]$ep)
                $t4 = Get-LocalMs
                $t2 = Read-NtpMs $resp 32
                $t3 = Read-NtpMs $resp 40
                $off = (($t2 - $t1) + ($t3 - $t4)) / 2
                $dly = ($t4 - $t1) - ($t3 - $t2)
                if ($best -eq $null -or $dly -lt $best.Delay) { $best = @{ Offset = $off; Delay = $dly } }
                Start-Sleep -Milliseconds 50
            }
            return @{ Offset = $best.Offset; Delay = $best.Delay; Source = "NTP $server" }
        } catch {
        } finally {
            if ($udp) { $udp.Close() }
        }
    }
    throw 'NTP 服务器均无法连接（可能被网络屏蔽 UDP 123 端口）'
}

# ---------- HTTP 接口对时（淘宝 / 拼多多），取往返延迟最小的一次 ----------
function Sync-Http([string]$url, [string]$pattern, [string]$name) {
    $best = $null
    for ($i = 0; $i -lt 6; $i++) {
        $sep = if ($url.Contains('?')) { '&' } else { '?' }
        $req = [Net.HttpWebRequest]::Create($url + $sep + '_=' + [Guid]::NewGuid().ToString('N'))
        $req.Timeout = 3000
        $req.UserAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
        $req.KeepAlive = $true
        $t1 = Get-LocalMs
        $resp = $req.GetResponse()
        $reader = New-Object IO.StreamReader($resp.GetResponseStream())
        $body = $reader.ReadToEnd()
        $t4 = Get-LocalMs
        $reader.Close(); $resp.Close()
        if ($i -eq 0) { continue }   # 第一次含 DNS/TLS 握手，丢弃
        if ($body -notmatch $pattern) { throw "$name 接口返回格式变了，暂时无法使用" }
        $server = [double]$matches[1]
        $rtt = $t4 - $t1
        $off = $server - ($t1 + $rtt / 2)
        if ($best -eq $null -or $rtt -lt $best.Delay) { $best = @{ Offset = $off; Delay = $rtt } }
    }
    @{ Offset = $best.Offset; Delay = $best.Delay; Source = $name }
}

function Invoke-Sync([string]$kind) {
    $script:syncing = $true
    $lblInfo.Text = '正在对时…'
    [Windows.Forms.Application]::DoEvents()
    try {
        switch ($kind) {
            'ntp' { $r = Sync-Ntp @('ntp.aliyun.com', 'ntp.tencent.com', 'cn.pool.ntp.org', 'time.windows.com') }
            'tb'  { $r = Sync-Http 'https://acs.m.taobao.com/h5/mtop.common.gettimestamp/1.0/' '"t"\s*:\s*"?(\d{13})' '淘宝服务器' }
            'pdd' { $r = Sync-Http 'https://api.pinduoduo.com/api/server/_stm' '"server_time"\s*:\s*"?(\d{13})' '拼多多服务器' }
        }
        $script:offset = $r.Offset
        $script:delay  = $r.Delay
        $script:source = $r.Source
    } catch {
        [Windows.Forms.MessageBox]::Show("对时失败：$($_.Exception.Message)", '毫秒时钟') | Out-Null
    } finally {
        $script:syncing = $false
    }
}

# ---------- 界面 ----------
$bg = [Drawing.Color]::FromArgb(24, 24, 28)
$fg = [Drawing.Color]::FromArgb(230, 230, 230)

$form = New-Object Windows.Forms.Form
$form.Text = '毫秒时钟'
$form.ClientSize = New-Object Drawing.Size(440, 250)
$form.FormBorderStyle = 'FixedSingle'
$form.MaximizeBox = $false
$form.StartPosition = 'CenterScreen'
$form.TopMost = $true
$form.BackColor = $bg
$form.ForeColor = $fg
$form.Font = New-Object Drawing.Font('Microsoft YaHei UI', 9)

$lblTime = New-Object Windows.Forms.Label
$lblTime.Font = New-Object Drawing.Font('Consolas', 40, [Drawing.FontStyle]::Bold)
$lblTime.ForeColor = [Drawing.Color]::FromArgb(80, 250, 120)
$lblTime.TextAlign = 'MiddleCenter'
$lblTime.SetBounds(0, 6, 440, 66)
$form.Controls.Add($lblTime)

$lblInfo = New-Object Windows.Forms.Label
$lblInfo.TextAlign = 'MiddleCenter'
$lblInfo.ForeColor = [Drawing.Color]::FromArgb(160, 160, 170)
$lblInfo.SetBounds(0, 74, 440, 20)
$form.Controls.Add($lblInfo)

$lblTarget = New-Object Windows.Forms.Label
$lblTarget.Text = '目标时间'
$lblTarget.SetBounds(14, 106, 60, 22)
$lblTarget.TextAlign = 'MiddleLeft'
$form.Controls.Add($lblTarget)

$txtTarget = New-Object Windows.Forms.TextBox
$txtTarget.SetBounds(76, 105, 110, 22)
$txtTarget.Font = New-Object Drawing.Font('Consolas', 10)
$txtTarget.Text = [DateTime]::Now.AddHours(1).ToString('HH:00:00')
$form.Controls.Add($txtTarget)

$chkBeep = New-Object Windows.Forms.CheckBox
$chkBeep.Text = '提示音，提前'
$chkBeep.Checked = $true
$chkBeep.SetBounds(200, 105, 100, 22)
$form.Controls.Add($chkBeep)

$numAdvance = New-Object Windows.Forms.NumericUpDown
$numAdvance.SetBounds(300, 105, 70, 22)
$numAdvance.Maximum = 10000
$numAdvance.Increment = 50
$numAdvance.Value = 300
$form.Controls.Add($numAdvance)

$lblMs = New-Object Windows.Forms.Label
$lblMs.Text = '毫秒'
$lblMs.SetBounds(374, 106, 50, 22)
$lblMs.TextAlign = 'MiddleLeft'
$form.Controls.Add($lblMs)

$lblCount = New-Object Windows.Forms.Label
$lblCount.Font = New-Object Drawing.Font('Consolas', 20, [Drawing.FontStyle]::Bold)
$lblCount.TextAlign = 'MiddleCenter'
$lblCount.SetBounds(0, 136, 440, 40)
$form.Controls.Add($lblCount)

function New-Btn([string]$text, [int]$x, [scriptblock]$onClick) {
    $b = New-Object Windows.Forms.Button
    $b.Text = $text
    $b.SetBounds($x, 196, 96, 32)
    $b.FlatStyle = 'Flat'
    $b.BackColor = [Drawing.Color]::FromArgb(45, 45, 52)
    $b.Add_Click($onClick)
    $form.Controls.Add($b)
}
New-Btn '阿里云NTP对时' 14  { Invoke-Sync 'ntp' }
New-Btn '淘宝对时'      120 { Invoke-Sync 'tb' }
New-Btn '拼多多对时'    226 { Invoke-Sync 'pdd' }

$chkTop = New-Object Windows.Forms.CheckBox
$chkTop.Text = '窗口置顶'
$chkTop.Checked = $true
$chkTop.SetBounds(338, 200, 90, 24)
$chkTop.Add_CheckedChanged({ $form.TopMost = $chkTop.Checked })
$form.Controls.Add($chkTop)

# ---------- 刷新 ----------
$script:beeped = $false
$script:lastTarget = ''
$weekNames = '日', '一', '二', '三', '四', '五', '六'
$formats = [string[]]@('HH:mm:ss.fff', 'HH:mm:ss', 'HH:mm', 'H:mm:ss', 'H:mm')

$timer = New-Object Windows.Forms.Timer
$timer.Interval = 1
$timer.Add_Tick({
    $nowMs = Get-NowMs
    $now = [DateTimeOffset]::FromUnixTimeMilliseconds([int64][Math]::Floor($nowMs)).LocalDateTime
    $lblTime.Text = $now.ToString('HH:mm:ss.fff')

    if (-not $script:syncing) {
        $info = '{0}  星期{1}   来源: {2}' -f $now.ToString('yyyy-MM-dd'), $weekNames[[int]$now.DayOfWeek], $script:source
        if ($script:delay -ne $null) {
            $info += '   本机偏差 {0:+0;-0;0}ms  延迟 {1:0}ms' -f (-$script:offset), $script:delay
        }
        $lblInfo.Text = $info
    }

    $t = [DateTime]::MinValue
    $ok = [DateTime]::TryParseExact($txtTarget.Text.Trim(), $formats, $null, [Globalization.DateTimeStyles]::None, [ref]$t)
    if ($txtTarget.Text -ne $script:lastTarget) { $script:beeped = $false; $script:lastTarget = $txtTarget.Text }
    if (-not $ok) {
        $lblCount.ForeColor = [Drawing.Color]::Gray
        $lblCount.Text = '目标时间格式：20:00:00'
        return
    }
    $target = $now.Date.Add($t.TimeOfDay)
    $left = ($target - $now).TotalMilliseconds
    if ($left -ge 0) {
        $ts = [TimeSpan]::FromMilliseconds($left)
        $lblCount.Text = '倒计时 {0:00}:{1:00}:{2:00}.{3:000}' -f [Math]::Floor($ts.TotalHours), $ts.Minutes, $ts.Seconds, $ts.Milliseconds
        if ($left -le 10000) { $lblCount.ForeColor = [Drawing.Color]::FromArgb(255, 90, 90) }
        else { $lblCount.ForeColor = [Drawing.Color]::FromArgb(255, 200, 80) }
        if ($chkBeep.Checked -and -not $script:beeped -and $left -le [double]$numAdvance.Value) {
            $script:beeped = $true
            [Media.SystemSounds]::Exclamation.Play()
        }
    } else {
        $ts = [TimeSpan]::FromMilliseconds(-$left)
        $lblCount.ForeColor = [Drawing.Color]::Gray
        $lblCount.Text = '已过 {0:00}:{1:00}:{2:00}.{3:000}' -f [Math]::Floor($ts.TotalHours), $ts.Minutes, $ts.Seconds, $ts.Milliseconds
    }
})

$form.Add_Shown({ Invoke-Sync 'ntp'; $timer.Start() })
$form.Add_FormClosed({ $timer.Stop() })
[Windows.Forms.Application]::EnableVisualStyles()
[void]$form.ShowDialog()
