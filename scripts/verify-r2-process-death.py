#!/usr/bin/env python3
"""Verify repayment state across a real background-process kill in the ordinary app on a disposable AVD.

Requires installed debug APK. Seeds reserved person/operation 900030, disables PIN
and ads on this test AVD. Never targets a phone. The next PartialRepaymentScreenTest
run cleans the fixture. No instrumentation remains active during process termination.
"""
import argparse
from decimal import Decimal
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default='adb')
parser.add_argument('--output', type=Path, default=Path('/tmp/simpledebt-r2/process-death'))
parser.add_argument('--phase', choices=['pending', 'committed', 'both'], default='both')
args = parser.parse_args()
if not re.fullmatch(r'emulator-[0-9]+', args.serial):
    parser.error('Use a disposable emulator serial; physical devices are refused.')
args.output.mkdir(parents=True, exist_ok=True)
adb = [args.adb, '-s', args.serial]
pkg = 'com.breckneck.debtbook'


def call(*parts, check=True, binary=False):
    return subprocess.run(adb + list(parts), check=check, capture_output=True,
                          text=not binary, timeout=30)


def process_pid():
    result = call('shell', 'pidof', pkg, check=False).stdout.strip()
    if result.isdigit():
        return result
    # Android 6 toybox can return every PID. Its ps output includes the full process name.
    for line in call('shell', 'ps').stdout.splitlines():
        columns = line.split()
        if len(columns) >= 2 and columns[-1] == pkg:
            return columns[1]
    return ''


def read_private(path):
    return call('exec-out', 'run-as', pkg, 'cat', path, binary=True).stdout


def write_private(path, content):
    # Paths are fixed test constants, never supplied by a backup or UI content.
    subprocess.run(adb + ['exec-in', 'run-as', pkg, 'sh', '-c', 'cat > ' + path],
                   input=content, check=True, capture_output=True, timeout=30)


def copy_database(folder):
    target = Path(folder) / 'HumanDataBase'
    for suffix in ['', '-wal']:
        result = call('exec-out', 'run-as', pkg, 'cat', 'databases/HumanDataBase' + suffix,
                      check=False, binary=True)
        if result.returncode == 0:
            Path(str(target) + suffix).write_bytes(result.stdout)
    return target


def seed():
    call('shell', 'am', 'force-stop', pkg)
    with tempfile.TemporaryDirectory(prefix='r2-fixture-') as folder:
        target = copy_database(folder)
        with sqlite3.connect(target) as db:
            db.execute('PRAGMA journal_mode=DELETE')
            db.execute('DELETE FROM Debt WHERE idHuman=900030')
            db.execute('DELETE FROM Human WHERE id=900030')
            db.execute("INSERT INTO Human(id,name,sumDebt,currency,openingAdjustment) VALUES(900030,'R2 test','100','EUR','0')")
            db.execute("INSERT INTO Debt(id,sum,idHuman,info,date,cancelled) VALUES(900030,'100',900030,'Process fixture','2026-11-16',0)")
        db.close()
        call('shell', 'run-as', pkg, 'rm', '-f', 'databases/HumanDataBase-wal', 'databases/HumanDataBase-shm')
        write_private('databases/HumanDataBase', target.read_bytes())
    path = 'shared_prefs/shared_prefs_settings.xml'
    prefs = ET.fromstring(read_private(path))
    for name, value in [('pin_code_is_enabled', 'false'), ('ads_disabled', 'true')]:
        node = next((item for item in prefs if item.get('name') == name), None)
        if node is None:
            node = ET.SubElement(prefs, 'boolean', name=name)
        node.set('value', value)
    currency = next((item for item in prefs if item.get('name') == 'default_currency'), None)
    if currency is None:
        currency = ET.SubElement(prefs, 'string', name='default_currency')
    currency.text = 'EUR'
    write_private(path, ET.tostring(prefs, encoding='utf-8', xml_declaration=True))


def window():
    call('shell', 'uiautomator', 'dump', '/sdcard/r2-window.xml')
    content = call('exec-out', 'cat', '/sdcard/r2-window.xml').stdout
    return content, ET.fromstring(content)


def until(description, predicate, seconds=45):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = predicate()
        if result:
            return result
        time.sleep(0.25)
    raise AssertionError(description)


def find(resource=None, text=None):
    try:
        content, tree = window()
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
        return None
    nodes = [node for node in tree.iter('node')
             if (resource is None or node.get('resource-id') == resource)
             and (text is None or text in node.get('text', ''))]
    return (content, nodes) if nodes else None


def tap(node):
    bounds = [int(value) for value in re.findall(r'[0-9]+', node.get('bounds'))]
    call('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))


def tap_when(resource=None, text=None):
    _, nodes = until('UI element not found: ' + str((resource, text)), lambda: find(resource, text))
    assert len(nodes) == 1, [(node.get('text'), node.get('resource-id')) for node in nodes]
    tap(nodes[0])


def launch(fresh=False):
    call('shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c',
         'android.intent.category.LAUNCHER', '-f', '0x10008000' if fresh else '0x10200000', '-n',
         pkg + '/.auth.AuthorizationActivity')


def ledger(label):
    # No writer is active at these UI checkpoints.
    with tempfile.TemporaryDirectory(prefix='r2-ledger-') as folder:
        with sqlite3.connect(copy_database(folder)) as connection:
            balance = Decimal(connection.execute('SELECT sumDebt FROM Human WHERE id=900030').fetchone()[0])
            rows = connection.execute('SELECT id,sum,repaymentOfId,operationId,cancelled FROM Debt WHERE idHuman=900030 ORDER BY id').fetchall()
        (args.output / (label + '-ledger.txt')).write_text(repr((balance, rows)))
        return balance, rows


phases = ['pending', 'committed'] if args.phase == 'both' else [args.phase]
for phase in phases:
    print(phase + ': seeding the disposable AVD and launching the ordinary app', flush=True)
    seed()
    launch(fresh=True)
    tap_when(resource='person-900030')
    print(phase + ': opening the original record', flush=True)
    tap_when(resource='record-900030')
    tap_when(resource='repaymentButton')
    tap_when(resource='repaymentAmountInput')
    call('shell', 'input', 'text', '20')
    if phase == 'committed':
        tap_when(resource='repaymentSaveButton')
        until('Committed form did not close', lambda: not find(resource='repaymentAmountInput'))
        balance, rows = ledger(phase + '-before-kill')
        assert balance == 80 and len(rows) == 2, (balance, rows)
    print(phase + ': saving background activity state', flush=True)
    call('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    def state_saved():
        dump = call('shell', 'dumpsys', 'activity', 'activities').stdout
        blocks = re.split(r'\* Hist\s+#', dump)[1:]
        saved = any(pkg + '/.core.MainActivity' in block.splitlines()[0] and
                    re.search(r'\b(?:mHaveState|haveState)=true', block) is not None and 'state=STOPPED' in block for block in blocks)
        if saved:
            (args.output / (phase + '-saved-activity.txt')).write_text(dump)
        return saved
    until('Android has not received the stopped activity state', state_saved)
    old_pid = process_pid()
    assert old_pid.isdigit(), old_pid
    print(phase + ': killing stopped PID ' + old_pid, flush=True)
    call('shell', 'am', 'kill', pkg)
    until('Old process has not exited', lambda: process_pid() != old_pid)
    launch()
    print(phase + ': checking the restored task', flush=True)
    if phase == 'pending':
        content, nodes = until('Pending form did not restore', lambda: find(resource='repaymentAmountInput'))
        assert nodes[0].get('text') == '20', nodes[0].attrib
        balance, rows = ledger(phase + '-before-save')
        assert balance == 100 and len(rows) == 1, (balance, rows)
        (args.output / (phase + '-restored.xml')).write_text(content)
        (args.output / (phase + '-restored.png')).write_bytes(call('exec-out', 'screencap', '-p', binary=True).stdout)
        tap_when(resource='repaymentSaveButton')
        until('Restored form did not save', lambda: not find(resource='repaymentAmountInput'))
    else:
        content, _ = until('History did not restore', lambda: find(resource='record-900030'))
        assert not find(resource='repaymentAmountInput')
        (args.output / (phase + '-restored.xml')).write_text(content)
    new_pid = process_pid()
    assert new_pid.isdigit() and new_pid != old_pid, (old_pid, new_pid)
    balance, rows = ledger(phase + '-after-restore')
    assert balance == 80 and len(rows) == 2, (balance, rows)
    assert Decimal(rows[0][1]) == 100 and rows[1][2] == rows[0][0] and rows[1][3] and not rows[1][4], rows
    print(f'{phase}: killed {old_pid} -> new PID {new_pid}; one repayment, original 100, balance 80', flush=True)
print('PASS: ordinary-app process death checked: ' + ', '.join(phases) + '; no duplicate repayment.', flush=True)
