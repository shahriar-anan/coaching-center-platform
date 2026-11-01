# Checks a database built with the sample seed (SEED_SAMPLE=true). Read-only: it only calls GET endpoints and logs in.
#
# Usage (Windows PowerShell, from the repository root), after the API has started on that database:
#   $env:SEED_SAMPLE_PASSWORD = "<the sample password you set>"
#   $env:MASTER_ADMIN_PASSWORD = "<the Master Admin password from api/.env>"
#   .\docs\exam-site\qa\scripts\check-sample-db.ps1 -BaseUrl http://localhost:8092
#
# Secrets come only from the environment variables above. They are never printed, and the script never writes them to a file.
# The Master Admin phone defaults to +8801700000001; set MASTER_ADMIN_PHONE to override it.
# Each check prints PASS or FAIL with the expected and the actual value. The exit code is the number of failed checks (0 = all pass).

param(
	[string] $BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

$samplePassword = $env:SEED_SAMPLE_PASSWORD
$masterPassword = $env:MASTER_ADMIN_PASSWORD
$masterPhone = if ($env:MASTER_ADMIN_PHONE) { $env:MASTER_ADMIN_PHONE } else { "+8801700000001" }

if ([string]::IsNullOrWhiteSpace($samplePassword)) {
	Write-Host "SEED_SAMPLE_PASSWORD is not set in this window. Set it, then run again." -ForegroundColor Red
	exit 99
}
if ([string]::IsNullOrWhiteSpace($masterPassword)) {
	Write-Host "MASTER_ADMIN_PASSWORD is not set in this window. Set it, then run again." -ForegroundColor Red
	exit 99
}

# The fixed ids of the sample (docs/exam-site/qa/seed-map.md).
$Ids = @{
	Exam1          = "e0000000-0000-4000-8000-000000000001"
	Package2       = "b0000000-0000-4000-8000-000000000002"
	Package1       = "b0000000-0000-4000-8000-000000000001"
	Exam17         = "e0000000-0000-4000-8000-000000000017"
}

$failed = 0

function Get-Token([string] $identifier, [string] $password) {
	$body = @{ identifier = $identifier; password = $password } | ConvertTo-Json
	$reply = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -Headers @{ "X-Client-Type" = "web" } `
		-ContentType "application/json" -Body $body
	return $reply.accessToken
}

function Invoke-Api([string] $path, [string] $token) {
	$headers = @{}
	if ($token) { $headers["Authorization"] = "Bearer $token" }
	return Invoke-RestMethod -Method Get -Uri "$BaseUrl$path" -Headers $headers
}

function Item-Count($value) {
	# An empty JSON list comes back as nothing (or a single null) in PowerShell: both mean zero items.
	if ($null -eq $value) { return 0 }
	return @($value | Where-Object { $null -ne $_ }).Count
}

function Report([string] $name, [bool] $ok, [string] $expected, [string] $actual) {
	if ($ok) {
		Write-Host ("PASS  {0}  expected {1}, got {2}" -f $name, $expected, $actual) -ForegroundColor Green
	}
	else {
		$script:failed++
		Write-Host ("FAIL  {0}  expected {1}, got {2}" -f $name, $expected, $actual) -ForegroundColor Red
	}
}

function Check-Count([string] $name, [int] $expected, [scriptblock] $get) {
	try {
		$actual = [int] (& $get)
		Report $name ($actual -eq $expected) "$expected" "$actual"
	}
	catch {
		$script:failed++
		Write-Host ("FAIL  {0}  expected {1}, got an error: {2}" -f $name, $expected, $_.Exception.Message) -ForegroundColor Red
	}
}

Write-Host "Checking $BaseUrl" -ForegroundColor Cyan

# Sign in. A failed sign-in is one FAIL and the checks that need that account are skipped.
$tokens = @{}
$logins = @(
	@{ Key = "Student1"; Who = "Student 1"; Identifier = "+8801711000011"; Password = $samplePassword },
	@{ Key = "Student2"; Who = "Student 2"; Identifier = "+8801711000012"; Password = $samplePassword },
	@{ Key = "Student6"; Who = "Student 6"; Identifier = "+8801711000016"; Password = $samplePassword },
	@{ Key = "Teacher1"; Who = "Teacher 1"; Identifier = "+8801711000003"; Password = $samplePassword },
	@{ Key = "Teacher2"; Who = "Teacher 2"; Identifier = "+8801711000004"; Password = $samplePassword },
	@{ Key = "Master"; Who = "Master Admin"; Identifier = $masterPhone; Password = $masterPassword }
)
foreach ($login in $logins) {
	try {
		$tokens[$login.Key] = Get-Token $login.Identifier $login.Password
		Report "sign in as $($login.Who)" $true "a token" "a token"
	}
	catch {
		Report "sign in as $($login.Who)" $false "a token" ("HTTP " + $_.Exception.Response.StatusCode.value__)
	}
}

# Catalog: two roots, and the empty Short Courses category under Bar Council Enrolment.
try {
	$tree = Invoke-Api "/api/v1/public/live-exam-categories"
	Check-Count "public category tree has two roots" 2 { @($tree).Count }
	$bar = @($tree) | Where-Object { $_.titleEn -eq "Bar Council Enrolment" } | Select-Object -First 1
	$short = @($bar.children) | Where-Object { $_.titleEn -eq "Short Courses" } | Select-Object -First 1
	Report "Short Courses exists and is empty" ($null -ne $short -and @($short.exams).Count -eq 0) "present and empty" `
		$(if ($null -eq $short) { "missing" } else { "present, $(@($short.exams).Count) exams" })
}
catch {
	$script:failed++
	Write-Host "FAIL  public category tree  error: $($_.Exception.Message)" -ForegroundColor Red
}

Check-Count "published packages in the public list" 3 {
	(Invoke-Api "/api/v1/public/live-exam-modules?size=50").totalElements
}

try {
	$popular = Invoke-Api "/api/v1/public/live-exam-modules?popular=true&size=50"
	$hasP2 = @($popular.content) | Where-Object { $_.id -eq $Ids.Package2 }
	Report "the popular filter shows package 2" ($null -ne $hasP2) "package 2" $(if ($hasP2) { "package 2" } else { "not found" })
}
catch {
	$script:failed++
	Write-Host "FAIL  the popular filter  error: $($_.Exception.Message)" -ForegroundColor Red
}

# Student 1: five results, in the two kinds of state.
if ($tokens.Student1) {
	Check-Count "Student 1 results list" 5 { (Invoke-Api "/api/v1/me/exam-results?size=50" $tokens.Student1).totalElements }
}

# Student 6: the empty states.
if ($tokens.Student6) {
	Check-Count "Student 6 results list is empty" 0 { (Invoke-Api "/api/v1/me/exam-results?size=50" $tokens.Student6).totalElements }
	Check-Count "Student 6 announcement inbox (the welcome one only)" 1 {
		(Invoke-Api "/api/v1/me/notices?group=ANNOUNCEMENTS&size=50" $tokens.Student6).totalElements
	}
}

# The package page shows an announcement to a holder only: Student 1 holds package 2, Student 2 does not.
if ($tokens.Student1 -and $tokens.Student2) {
	Check-Count "package 2 page announcement, Student 1 (holder)" 1 {
		Item-Count (Invoke-Api "/api/v1/me/announcements?kind=package&id=$($Ids.Package2)" $tokens.Student1)
	}
	Check-Count "package 2 page announcement, Student 2 (not a holder)" 0 {
		Item-Count (Invoke-Api "/api/v1/me/announcements?kind=package&id=$($Ids.Package2)" $tokens.Student2)
	}
}

# Staff: leaderboard, pending orders and the announcement history.
if ($tokens.Master) {
	Check-Count "leaderboard of exam 1 lists the three passers" 3 {
		@((Invoke-Api "/api/v1/live-exams/$($Ids.Exam1)/leaderboard" $tokens.Master).rows).Count
	}
	Check-Count "exam 1: two results below the pass mark (staff count)" 2 {
		(Invoke-Api "/api/v1/live-exams/$($Ids.Exam1)/leaderboard" $tokens.Master).belowPass
	}
	Check-Count "pending orders queue has one order" 1 {
		(Invoke-Api "/api/v1/live-exam-orders?status=INITIATED" $tokens.Master).totalElements
	}
	Check-Count "announcements sent (six, one withdrawn)" 6 {
		(Invoke-Api "/api/v1/announcements" $tokens.Master).totalElements
	}
}

# Review averages: exam 1 and package 2 each show 4.7 from three counted ratings.
foreach ($pair in @(@{ Name = "exam 1"; Path = "exam/$($Ids.Exam1)" }, @{ Name = "package 2"; Path = "package/$($Ids.Package2)" })) {
	try {
		$reviews = Invoke-Api "/api/v1/public/reviews/$($pair.Path)"
		$avg = [double] $reviews.ratingAverage
		Report "review average of $($pair.Name)" ([math]::Abs($avg - 4.7) -lt 0.001) "4.7 (three ratings)" `
			("{0} ({1} ratings)" -f $avg, $reviews.ratingCount)
	}
	catch {
		$script:failed++
		Write-Host "FAIL  review average of $($pair.Name)  error: $($_.Exception.Message)" -ForegroundColor Red
	}
}

# Marking queues: teacher 1 has one script awaiting (exam 13), teacher 2 has two (exams 14 and 17).
if ($tokens.Teacher1) {
	Check-Count "marking queue of Teacher 1" 1 {
		(Invoke-Api "/api/v1/live-exam-submissions?status=AWAITING" $tokens.Teacher1).totalElements
	}
}
if ($tokens.Teacher2) {
	Check-Count "marking queue of Teacher 2" 2 {
		(Invoke-Api "/api/v1/live-exam-submissions?status=AWAITING" $tokens.Teacher2).totalElements
	}
}

Write-Host ""
if ($failed -eq 0) {
	Write-Host "All checks passed." -ForegroundColor Green
}
else {
	Write-Host "$failed check(s) failed. See docs/exam-site/qa/build-sample-db.md, section 'If a check fails'." -ForegroundColor Red
}
exit $failed
