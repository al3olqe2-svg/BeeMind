"""Destructive synthetic fixtures on DISPOSABLE EMULATORS ONLY, never owner devices.
After seed/initial scheduling: no Activity, no instrumentation process, real alarms.
"""
from contextlib import closing
import pathlib
import re
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET
OUT = pathlib.Path('app/build/reports/reminder-device')
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = 'com.beemind.personal'

def adb(*args):
    # An Android shell command must not consume the whole CI job if adbd/AMS stalls.
    timeout = 180 if 'instrument' in args else 45
    try:
        return subprocess.check_output(['adb', *args], text=True, stderr=subprocess.STDOUT, timeout=timeout)
    except subprocess.CalledProcessError as failure:
        print(failure.output, flush=True)
        raise

def evidence(name, *args):
    try:
        (OUT / name).write_text(adb(*args))
    except (subprocess.SubprocessError, OSError) as error:
        (OUT / name).write_text(str(error))

def snapshot():
    # Read private synthetic Room/WAL after each short phase without opening BeeMind.
    # Never retain a host SQLite connection/SHM mapping while replacing copied WAL files.
    for suffix in ['-shm', '-wal']:
        path = OUT / ('beemind.db' + suffix)
        if path.exists():
            path.unlink()
    for name in ['beemind.db', 'beemind.db-wal']:
        result = subprocess.run(['adb', 'exec-out', 'run-as', PACKAGE, 'cat', 'databases/' + name], capture_output=True, timeout=45)
        path = OUT / name
        if result.returncode == 0:
            path.write_bytes(result.stdout)
        elif path.exists():
            path.unlink()
    with closing(sqlite3.connect('file:' + str(OUT / 'beemind.db') + '?mode=ro', uri=True)) as db:
        db.row_factory = sqlite3.Row
        return {r['noteId']: dict(r) for r in db.execute('SELECT * FROM reminders')}

def kill_cached():
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    adb('shell', 'am', 'kill', PACKAGE)  # deliberately NOT force-stop
    time.sleep(2)
    result = subprocess.run(['adb', 'shell', 'pidof', PACKAGE], capture_output=True, text=True, timeout=45)
    if result.stdout.strip():
        # am kill can decline while the recently hidden Activity still has foreground
        # importance. Kill only this debuggable fixture UID; do not mark package stopped.
        pid = result.stdout.strip()
        assert pid.isdigit()
        adb('shell', 'kill', '-9', pid)
        time.sleep(2)
        check = subprocess.run(['adb', 'shell', 'pidof', PACKAGE], capture_output=True, text=True, timeout=45)
        assert not check.stdout.strip(), 'Target process did not die: ' + check.stdout
    print('Confirmed: no BeeMind process or Activity', flush=True)

def wait_for(predicate, seconds, label):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if predicate():
            print('PASS:', label, flush=True)
            return
        time.sleep(5)
    raise AssertionError('Timed out: ' + label)

def tap_notification_action(text):
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'cmd', 'statusbar', 'expand-notifications')
    for attempt in range(8):
        time.sleep(1)
        adb('shell', 'uiautomator', 'dump', '/sdcard/notification.xml')
        raw = adb('shell', 'cat', '/sdcard/notification.xml')
        (OUT / ('shade-' + str(time.time_ns()) + '.xml')).write_text(raw)
        nodes = list(ET.fromstring(raw).iter('node'))
        matches = [n for n in nodes if text in n.get('text', '')]
        if matches:
            node = matches[0]
        else:
            # Expand only BeeMind's group/child rows. Do not toggle a Collapse arrow
            # or unrelated Android System notifications after the group shrinks.
            rows = [n for n in nodes if n.get('resource-id', '').endswith('/expandableNotificationRow')
                    and any('BeeMind' in child.get('text', '') for child in n.iter('node'))]
            buttons = [child for row in rows for child in row.iter('node')
                       if child.get('resource-id', '').endswith('/expand_button')
                       and child.get('content-desc') == 'Expand']
            if not buttons:
                # A wake-up/first SystemUI frame can ignore the initial expansion.
                # Poll the actual shade; never synthesize the PendingIntent action.
                adb('shell', 'cmd', 'statusbar', 'expand-notifications')
                continue
            node = buttons[0]
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
        adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
        if matches:
            return
    raise AssertionError('Could not find notification action: ' + text)

assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1', 'Emulator required'
print(adb('root'), flush=True)
adb('wait-for-device')
assert adb('shell', 'id', '-u').strip() == '0', 'Rootable Google APIs emulator required'
live_log = None
live_log_file = None
try:
    sdk = int(adb('shell', 'getprop', 'ro.build.version.sdk').strip())
    if sdk >= 33:
        adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    if sdk >= 31:
        adb('shell', 'appops', 'set', PACKAGE, 'SCHEDULE_EXACT_ALARM', 'allow')
    adb('logcat', '-c')
    live_log_file = (OUT / 'live-logcat.txt').open('w', encoding='utf-8')
    live_log = subprocess.Popen(['adb', 'logcat', '-v', 'threadtime'], stdout=live_log_file, stderr=subprocess.STDOUT)
    seed = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
               PACKAGE + '.ReminderDeviceFixture#seed',
               PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner')
    (OUT / 'seed.txt').write_text(seed)
    print(seed, flush=True)
    assert 'OK (1 test)' in seed
    # Android finishes instrumentation by force-stopping the target. Explicitly open ONCE
    # to restore its alarms; never restart instrumentation during delivery verification.
    print('Opening Activity for initial scheduling', flush=True)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    print('Checking persisted alarm scheduling', flush=True)
    wait_for(lambda: all(r['scheduling'] == 'EXACT' for r in snapshot().values()), 15, 'initial scheduling')
    initial = snapshot()
    assert len(initial) == 2 and all(r['delivery'] == 'PENDING' for r in initial.values())
    assert all(r['dueAt'] > time.time() * 1000 + 5000 for r in initial.values())
    kill_cached()
    adb('shell', 'dumpsys', 'battery', 'unplug')
    adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    wait_for(lambda: all(r['delivery'] == 'DELIVERED' for r in snapshot().values()), 180,
             'relative AND calendar alarms delivered with cold process and screen off')
    before = int(time.time() * 1000)
    tap_notification_action('تأجيل 10 دقائق')
    wait_for(lambda: any(r['status'] == 'SNOOZED' and r['scheduling'] == 'EXACT' for r in snapshot().values()),
             15, 'notification Snooze persisted and scheduled')
    rows = snapshot()
    snoozed = next(r for r in rows.values() if r['status'] == 'SNOOZED')
    assert before + 600000 <= snoozed['dueAt'] <= int(time.time() * 1000) + 600000
    assert snoozed['generation'] != initial[snoozed['noteId']]['generation']
    tap_notification_action('إنجاز')  # complete the other notification before Doze
    wait_for(lambda: any(r['status'] == 'DONE' for r in snapshot().values()), 15, 'Done without Activity')
    (OUT / 'snoozed-alarms.txt').write_text(adb('shell', 'dumpsys', 'alarm'))
    kill_cached()
    adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    idle = adb('shell', 'dumpsys', 'deviceidle', 'force-idle')
    (OUT / 'idle.txt').write_text(idle)
    print(idle, flush=True)
    wait_for(lambda: snapshot()[snoozed['noteId']]['delivery'] == 'DELIVERED', 720,
             'real ten-minute Snooze delivered after process death in Doze')
    adb('shell', 'dumpsys', 'deviceidle', 'unforce')
    tap_notification_action('إنجاز')
    wait_for(lambda: all(r['status'] == 'DONE' for r in snapshot().values()), 15, 'both reminders done')
    with closing(sqlite3.connect('file:' + str(OUT / 'beemind.db') + '?mode=ro', uri=True)) as db:
        assert db.execute('SELECT count(*) FROM notes').fetchone()[0] == 2
        assert db.execute("SELECT count(*) FROM notes WHERE expressiveTrait = 'GLOW'").fetchone()[0] == 2
    print('PASS: native cold delivery, actual ten-minute snooze in Doze, notes retained', flush=True)
finally:
    evidence('logcat.txt', 'logcat', '-d')
    evidence('alarms.txt', 'shell', 'dumpsys', 'alarm')
    evidence('notifications.txt', 'shell', 'dumpsys', 'notification', '--noredact')
    evidence('activities.txt', 'shell', 'dumpsys', 'activity', 'activities')
    evidence('idle-cleanup.txt', 'shell', 'dumpsys', 'deviceidle', 'unforce')
    evidence('battery-cleanup.txt', 'shell', 'dumpsys', 'battery', 'reset')
    if live_log is not None:
        live_log.terminate()
        try:
            live_log.wait(timeout=5)
        except subprocess.TimeoutExpired:
            live_log.kill()
            live_log.wait(timeout=5)
    if live_log_file is not None:
        live_log_file.close()
