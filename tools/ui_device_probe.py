"""Bounded HWUI probes on disposable emulators, before any reminder is seeded."""
from pathlib import Path
import subprocess

PACKAGE = 'com.beemind.personal'
OUT = Path('app/build/reports/ui-device')
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args, timeout=45):
    return subprocess.check_output(['adb', *args], text=True, stderr=subprocess.STDOUT, timeout=timeout)

assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1'
adb('root')
adb('wait-for-device')
adb('logcat', '-c')
with (OUT / 'live-logcat.txt').open('w', encoding='utf-8') as log:
    logger = subprocess.Popen(['adb', 'logcat', '-v', 'threadtime'], stdout=log, stderr=subprocess.STDOUT)
    try:
        for component in ['empty', 'background', 'rainbow', 'fab', 'text', 'home-empty', 'home-plain', 'home-glow', 'home', 'main']:
            print('NATIVE VISUAL PROBE:', component, flush=True)
            result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'component', component,
                         '-e', 'class', PACKAGE + '.NativeVisualProbeTest',
                         PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=90)
            (OUT / (component + '.txt')).write_text(result)
            print(result, flush=True)
            assert 'OK (1 test)' in result
            adb('pull', '/data/user/0/' + PACKAGE + '/files/native-ui-' + component + '.png', str(OUT / (component + '.png')))
    finally:
        logger.terminate()
        try:
            logger.wait(timeout=5)
        except subprocess.TimeoutExpired:
            logger.kill()
            logger.wait(timeout=5)
