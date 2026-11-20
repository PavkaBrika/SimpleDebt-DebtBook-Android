#!/usr/bin/env python3
"""Verify OS alarms across reboot/time-zone changes on an explicitly selected disposable AVD.

Never selects a device implicitly. Stores/restores its SQLite and auth/ads preferences.
The current debug app must have been opened once, with notifications allowed and no screen lock.
"""
from contextlib import closing
import argparse
import datetime as dt
import os
import pathlib
import re
import sqlite3
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo

p = argparse.ArgumentParser()
p.add_argument('--device', required=True)
p.add_argument('--disposable-emulator', action='store_true', required=True)
p.add_argument('--adb', default=os.path.join(os.environ['ANDROID_HOME'], 'platform-tools', 'adb')
               if 'ANDROID_HOME' in os.environ else 'adb')
p.add_argument('--output', type=pathlib.Path, required=True)
p.add_argument('--delivery', action='store_true', help='Also advance disposable emulator clock to test actual alarm delivery')
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+', a.device):
    p.error('--device must identify a disposable Android emulator')
out = a.output
out.mkdir(parents=True)
pkg = 'com.breckneck.debtbook'
record = 900067
bangkok = ZoneInfo('Asia/Bangkok')
los_angeles = ZoneInfo('America/Los_Angeles')

def adb(*args, check=True, binary=False):
    r = subprocess.run([a.adb, '-s', a.device, *args], capture_output=True, check=check, timeout=60)
    return r.stdout if binary else r.stdout.decode().strip()

def sh(*args, **kwargs):
    return adb('shell', *args, **kwargs)

def wait_for(message, fn, seconds=50):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        result = fn()
        if result: return result
        time.sleep(1)
    raise AssertionError(message)

def snapshot(label):
    text = sh('dumpsys', 'alarm')
    (out / (label + '-alarms.txt')).write_text(text)
    return text

def boot_id():
    return sh('cat', '/proc/sys/kernel/random/boot_id', check=False)

def triggers(text):
    section = text.partition('Pending user blocked background alarms:')[0]
    return [int(epoch) for epoch in re.findall(
        r'\bRTC_WAKEUP #\d+: Alarm\{[^\n]*origWhen (\d+) [^\n]*\bcom\.breckneck\.debtbook\}', section)]

def save_db(label):
    dest = out / (label + '.db')
    for suffix in ['', '-wal', '-shm']:
        data = adb('exec-out', 'run-as', pkg, 'cat', 'databases/HumanDataBase' + suffix, check=False, binary=True)
        if data and not data.startswith(b'cat:'):
            dest.with_name(dest.name + suffix).write_bytes(data)
    return dest

def install_db(path):
    coherent = out / 'install.db'
    coherent.unlink(missing_ok=True)
    with closing(sqlite3.connect(path)) as source, closing(sqlite3.connect(coherent)) as target:
        source.backup(target)
    adb('push', str(coherent), '/data/local/tmp/r22-fixture.db')
    sh('run-as', pkg, 'rm', '-f', 'databases/HumanDataBase-wal', 'databases/HumanDataBase-shm')
    sh('run-as', pkg, 'cp', '/data/local/tmp/r22-fixture.db', 'databases/HumanDataBase')

def save_prefs(name):
    path = out / name
    raw = adb('exec-out', 'run-as', pkg, 'cat', 'shared_prefs/' + name, check=False, binary=True)
    if raw.startswith(b'<?xml'):
        ET.fromstring(raw)
        path.write_bytes(raw)
    return path

def install_prefs(path, name):
    dest = 'shared_prefs/' + name
    if path.exists():
        adb('push', str(path), '/data/local/tmp/r22-prefs.xml')
        sh('run-as', pkg, 'mkdir', '-p', 'shared_prefs')
        sh('run-as', pkg, 'cp', '/data/local/tmp/r22-prefs.xml', dest)
    else:
        sh('run-as', pkg, 'rm', '-f', dest)

def open_app():
    sh('am', 'start', '-n', pkg + '/.auth.AuthorizationActivity')
    wait_for('schedule not recreated', lambda: len(triggers(sh('dumpsys', 'alarm'))) == 1)

zone = sh('getprop', 'persist.sys.timezone')
auto_time = sh('settings', 'get', 'global', 'auto_time')
auto_zone = sh('settings', 'get', 'global', 'auto_time_zone')
start_device = int(sh('date', '+%s')); start_host = time.monotonic()
due_date = dt.datetime.fromtimestamp(start_device, bangkok).date() + dt.timedelta(days=7)

def due_at(zone):
    return int(dt.datetime.combine(due_date, dt.time(9), tzinfo=zone).timestamp() * 1000)

settings_name = 'shared_prefs_settings.xml'
schedule_name = 'debt_reminder_schedule.xml'
sh('am', 'force-stop', pkg)
original = save_db('original')
if not original.is_file():
    raise RuntimeError('Open the current debug app once before running the lifecycle check')
original_settings = save_prefs(settings_name)
original_schedule = save_prefs(schedule_name)
clock_changed = False
try:
    # Fixture lives only on this disposable AVD; original files are restored in finally.
    dbpath = out / 'fixture.db'
    with closing(sqlite3.connect(original)) as source, closing(sqlite3.connect(dbpath)) as target:
        source.backup(target)
    with closing(sqlite3.connect(dbpath)) as db, db:
        db.execute('DELETE FROM Debt WHERE idHuman=?', (record,))
        db.execute('DELETE FROM Human WHERE id=?', (record,))
        db.execute('INSERT INTO Human(id,name,sumDebt,currency,openingAdjustment) VALUES(?,?,?,?,?)', (record, 'R22 lifecycle', '100', 'EUR', '0'))
        db.execute('INSERT INTO Debt(id,sum,idHuman,info,date,cancelled,dueDay,reminderMinute,reminderToken,reminderDelivered) VALUES(?,?,?,?,?,0,?,?,?,0)',
                   (record, '100', record, 'OS lifecycle fixture', '2026-11-16', due_date.isoformat(), 540, 'external-lifecycle-900067'))
    tree = ET.parse(original_settings).getroot() if original_settings.exists() else ET.Element('map')
    for name, value in [('pin_code_is_enabled', 'false'), ('ads_disabled', 'true')]:
        item = next((x for x in tree if x.get('name') == name), None)
        if item is None: item = ET.SubElement(tree, 'boolean', name=name)
        item.set('value', value)
    test_prefs = out / 'test-settings.xml'; ET.ElementTree(tree).write(test_prefs, encoding='utf-8', xml_declaration=True)
    install_prefs(test_prefs, settings_name)
    install_prefs(out / 'empty-schedule.xml', schedule_name)
    install_db(dbpath)
    sh('settings', 'put', 'global', 'auto_time_zone', '0')
    sh('cmd', 'alarm', 'set-timezone', bangkok.key)
    open_app(); time.sleep(2)
    before = snapshot('before-zone')
    assert triggers(before) == [due_at(bangkok)], 'expected one Bangkok trigger'
    sh('input', 'keyevent', 'KEYCODE_HOME')
    sh('cmd', 'alarm', 'set-timezone', los_angeles.key)
    wait_for('timezone broadcast not applied', lambda: triggers(sh('dumpsys', 'alarm')) == [due_at(los_angeles)])
    snapshot('after-zone')
    print('PASS timezone: calendar date fixed, local 09:00 preserved, one alarm', flush=True)
    before_boot = boot_id()
    assert before_boot
    print(f'Before reboot: boot ID={before_boot}, standby={sh("am", "get-standby-bucket", pkg)}', flush=True)
    adb('reboot')
    wait_for('emulator did not start a new kernel', lambda: (current := boot_id()) and current != before_boot, 180)
    wait_for('boot incomplete', lambda: sh('getprop', 'sys.boot_completed', check=False) == '1', 180)
    sh('input', 'keyevent', 'KEYCODE_WAKEUP'); sh('wm', 'dismiss-keyguard')
    # Some emulators take the host zone again when booting. Use Android's current zone.
    boot_zone = ZoneInfo(sh('getprop', 'persist.sys.timezone'))
    print(f'After reboot: boot ID={boot_id()}, timezone={boot_zone.key}', flush=True)
    def matching_alarm():
        alarms = sh('dumpsys', 'alarm')
        return alarms if triggers(alarms) == [due_at(boot_zone)] else None
    rebooted = wait_for('BOOT_COMPLETED did not recreate one alarm at the expected time', matching_alarm, 240)
    (out / 'after-reboot-alarms.txt').write_text(rebooted)
    print('PASS reboot: actual device reboot recreated one alarm without opening app', flush=True)
    if a.delivery:
        sh('am', 'force-stop', pkg)
        current = save_db('pre-delivery')
        local = dt.datetime.fromtimestamp(start_device, boot_zone).replace(hour=8, minute=0, second=0)
        now = int(local.timestamp())
        target = local + dt.timedelta(minutes=2)
        sh('settings', 'put', 'global', 'auto_time', '0')
        clock_changed = True
        sh('cmd', 'alarm', 'set-time', str(now * 1000))
        with closing(sqlite3.connect(current)) as db, db:
            db.execute('UPDATE Debt SET dueDay=?, reminderMinute=?, reminderToken=?, reminderDelivered=0 WHERE id=?',
                       (target.strftime('%Y-%m-%d'), target.hour*60 + target.minute, 'external-delivery-' + str(time.time_ns()), record))
        install_db(current); open_app(); sh('input', 'keyevent', 'KEYCODE_HOME')
        sh('am', 'kill', pkg)
        sh('cmd', 'alarm', 'set-time', str((now + 70*60)*1000))
        def posted():
            dump = sh('dumpsys', 'notification', '--noredact')
            return re.search(r'NotificationRecord\([^\n]*pkg=com\.breckneck\.debtbook[^\n]*tag=personal-debt', dump)
        wait_for('actual inexact alarm was not delivered', posted, 90)
        (out / 'delivered-notification.txt').write_text(sh('dumpsys', 'notification', '--noredact'))
        sh('cmd', 'statusbar', 'expand-notifications')
        (out / 'actual-notification.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        sh('cmd', 'statusbar', 'collapse')
        print('PASS actual inexact alarm delivered after clock advance with app in background', flush=True)
finally:
    wait_for('device not ready for fixture cleanup', lambda: sh('getprop', 'sys.boot_completed', check=False) == '1', 180)
    try:
        snapshot('before-cleanup')
        (out / 'broadcasts.txt').write_text(sh('dumpsys', 'activity', 'broadcasts'))
        (out / 'logcat.txt').write_text(adb('logcat', '-d', '-t', '2500'))
    except subprocess.SubprocessError as error:
        print(f'Could not capture emulator diagnostics: {error}', file=sys.stderr)
    sh('am', 'force-stop', pkg, check=False)
    install_db(original)
    install_prefs(original_settings, settings_name)
    install_prefs(original_schedule, schedule_name)
    sh('rm', '-f', '/data/local/tmp/r22-fixture.db', '/data/local/tmp/r22-prefs.xml')
    sh('cmd', 'alarm', 'set-timezone', zone, check=False)
    if clock_changed: sh('cmd', 'alarm', 'set-time', str((start_device + int(time.monotonic() - start_host))*1000))
    sh('settings', 'put', 'global', 'auto_time', auto_time)
    sh('settings', 'put', 'global', 'auto_time_zone', auto_zone)
    restored = adb('exec-out', 'run-as', pkg, 'cat', 'databases/HumanDataBase', binary=True)
    assert restored == (out / 'install.db').read_bytes(), 'original database was not restored'
    sh('am', 'start', '-n', pkg + '/.auth.AuthorizationActivity', check=False)
    print('Restored original app database, preferences and emulator time settings', flush=True)
