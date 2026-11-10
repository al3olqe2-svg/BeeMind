"""Native checks on disposable emulators only. No Activity; ordinary process kill."""
import pathlib
import subprocess
import time
OUT = pathlib.Path('app/build/reports/reminder-device')
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = 'com.beemind.personal'
RUNNER = PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'
FIXTURE = PACKAGE + '.ReminderDeviceFixture'

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, stderr=subprocess.STDOUT)

def phase(method, note):
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', FIXTURE + '#' + method,
                 '-e', 'noteId', note, RUNNER)
    (OUT / (note + '-' + method + '.txt')).write_text(result)
    print(result, flush=True)
    if 'OK (1 test)' not in result:
        raise AssertionError('Native phase failed: ' + method)

def kill_cached():
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    adb('shell', 'am', 'kill', PACKAGE)  # NOT force-stop
    time.sleep(2)
    result = subprocess.run(['adb', 'shell', 'pidof', PACKAGE], capture_output=True, text=True)
    if result.stdout.strip():
        raise AssertionError('Target process did not die: ' + result.stdout)
    print('Confirmed: no BeeMind process or Activity', flush=True)

def wait_seconds(seconds):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        print('Waiting for real Android alarm; seconds left:', round(end - time.monotonic()), flush=True)
        time.sleep(min(30, max(0, end - time.monotonic())))

try:
    sdk = int(adb('shell', 'getprop', 'ro.build.version.sdk').strip())
    if sdk >= 33:
        adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    if sdk >= 31:
        adb('shell', 'appops', 'set', PACKAGE, 'SCHEDULE_EXACT_ALARM', 'allow')
    adb('logcat', '-c')
    notes = ['device-relative', 'device-calendar']
    for note in notes:
        phase('seed', note)
        kill_cached()
    adb('shell', 'dumpsys', 'battery', 'unplug')
    adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    wait_seconds(120)
    for note in notes:
        phase('verifyDeliveryAndSnooze', note)
        kill_cached()
    (OUT / 'snoozed-alarms.txt').write_text(adb('shell', 'dumpsys', 'alarm'))
    adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    # Actual ten minutes: no fake clock or shortened production constants.
    wait_seconds(660)
    for note in notes:
        phase('verifySnoozedDeliveryAndDone', note)
        kill_cached()
    print('PASS: cold relative/calendar delivery, ten-minute snooze, Done retaining note', flush=True)
finally:
    (OUT / 'logcat.txt').write_text(adb('logcat', '-d'))
    (OUT / 'alarms.txt').write_text(adb('shell', 'dumpsys', 'alarm'))
    (OUT / 'notifications.txt').write_text(adb('shell', 'dumpsys', 'notification', '--noredact'))
    adb('shell', 'dumpsys', 'battery', 'reset')
