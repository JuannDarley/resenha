#!/usr/bin/env python3
"""Browser regression against the real frontend, API and temporary PostgreSQL.
Invoked by BrowserE2ETest; requires the Orca desktop app and port 5500 available.
"""
import argparse
import functools
import http.server
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--api', required=True)
parser.add_argument('--admin-email', required=True)
parser.add_argument('--front', default='../resenha-front')
args = parser.parse_args()
front = Path(args.front).resolve()
if not (front / 'index.html').is_file():
    raise SystemExit(f'Frontend ausente: {front}. Configure -Dresenha.frontend=/caminho/resenha-front')
cli = os.environ.get('ORCA_CLI_COMMAND') or ('orca-dev' if os.environ.get('ORCA_DEV_REPO_ROOT') else 'orca')
page = None

def command(*items):
    result = subprocess.run([cli, *items, '--json'], capture_output=True, text=True, timeout=40)
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    output = json.loads(result.stdout)
    if output.get('ok') is False:
        raise AssertionError(output)
    return output.get('result', output)

def evaluate(expression):
    return command('eval', '--page', page, '--expression', expression)

def wait(expression):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        result = evaluate(f'Boolean({expression})')
        # CLI versions wrap evaluation values differently. Keep extraction explicit.
        value = result
        for key in ('value', 'result'):
            if isinstance(value, dict) and key in value:
                value = value[key]
        if value is True or value == 'true':
            return
        time.sleep(.25)
    raise AssertionError(f'Timed out: {expression}; last result={result}')

def click(selector):
    evaluate(f'document.querySelector({json.dumps(selector)}).click()')

def fill(form, fields):
    evaluate('(() => {' + ''.join(
        f'document.querySelector({json.dumps(form + " [name=" + name + "]")}).value={json.dumps(str(value))};'
        for name, value in fields.items()) + '})()')

def submit(form):
    evaluate(f'document.querySelector({json.dumps(form)}).requestSubmit()')

def register(role):
    email = f'e2e-{uuid.uuid4()}@example.com'
    click('[data-auth="register"]')
    fill('#register-form', {'email': email, 'password': 'password123', 'role': role})
    submit('#register-form')
    wait('document.querySelector("#auth-message").textContent.includes("Conta criada")')
    submit('#login-form')
    wait('!document.querySelector("#dashboard").classList.contains("hidden")')
    return email

def logout():
    click('#logout')
    wait('!document.querySelector("#auth-screen").classList.contains("hidden")')

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *unused):
        pass

server = http.server.ThreadingHTTPServer(('127.0.0.1', 5500), functools.partial(QuietHandler, directory=str(front)))
threading.Thread(target=server.serve_forever, daemon=True).start()
try:
    created = command('tab', 'create', '--url', 'http://127.0.0.1:5500')
    page = created['browserPageId']
    command('snapshot', '--page', page, '--interactive')
    evaluate('localStorage.clear(); localStorage.setItem("resenha-api", ' + json.dumps(args.api) + ')')
    command('reload', '--page', page)
    command('snapshot', '--page', page, '--interactive')
    player_email = register('PLAYER')
    click('[data-view="jogador"]')
    wait('document.querySelector("#player-form [name=name]") !== null')
    fill('#player-form', {'name': 'Atleta E2E', 'age': 25, 'street': 'Rua E2E', 'number': 1,
                         'neighborhood': 'Centro', 'zipCode': '12345678', 'city': 'Cidade', 'state': 'SP'})
    submit('#player-form')
    wait('document.querySelector("#player-profile").textContent.includes("Atleta E2E")')
    fill('#player-form', {'name': 'Atleta atualizado'})
    submit('#player-form')
    wait('document.querySelector("#player-profile").textContent.includes("Atleta atualizado")')
    command('reload', '--page', page)
    command('snapshot', '--page', page, '--interactive')
    click('[data-view="jogador"]')
    wait('document.querySelector("#player-profile").textContent.includes("Atleta atualizado")')
    logout()
    print('PASS: cadastro, login, perfil, edição persistida, sessão e logout', flush=True)

    register('OWNER')
    click('[data-view="quadras"]')
    wait('!document.querySelector("#quadras-view").classList.contains("hidden")')
    click('#new-venue')
    fill('#venue-form', {'name': 'Arena E2E', 'description': 'Arena de teste', 'openingTime': '08:00',
                        'closingTime': '22:00', 'courtCount': 1, 'street': 'Rua E2E', 'number': 1,
                        'neighborhood': 'Centro', 'zipCode': '12345678', 'city': 'Cidade', 'state': 'SP'})
    submit('#venue-form')
    wait('document.querySelector("#venues-list").textContent.includes("Arena E2E")')
    click('#new-court')
    fill('#court-form', {'name': 'Quadra E2E', 'type': 'SOCIETY'})
    submit('#court-form')
    wait('document.querySelector("#courts-list").textContent.includes("Quadra E2E")')
    logout()
    print('PASS: proprietário cadastra estabelecimento pendente e quadra', flush=True)

    fill('#login-form', {'email': args.admin_email, 'password': 'password123', 'role': 'ADMIN'})
    submit('#login-form')
    wait('!document.querySelector("#dashboard").classList.contains("hidden")')
    click('[data-view="admin"]')
    wait('document.querySelector("#admin-list").textContent.includes("Atleta atualizado")')
    print('PASS: administrador consulta o jogador criado pela interface', flush=True)
finally:
    if page:
        try:
            evaluate('localStorage.clear()')
            command('tab', 'close', '--page', page)
        except Exception:
            pass
    server.shutdown()
    server.server_close()
