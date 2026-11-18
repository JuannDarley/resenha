#!/usr/bin/env python3
"""Browser regression against the real frontend, API and temporary PostgreSQL.
Invoked by BrowserE2ETest; requires the Orca desktop app and port 5510 available.
"""
import argparse
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
import functools
import http.server
import json
import os
from pathlib import Path
import socket
import subprocess
import threading
import time
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--api', required=True)
parser.add_argument('--admin-email', required=True)
parser.add_argument('--booking-court', required=True)
parser.add_argument('--booking-owner-email', required=True)
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

def check_menu(views):
    expected = json.dumps(sorted(views))
    wait('JSON.stringify(Array.from(document.querySelectorAll(".nav-link[data-view]:not(.hidden)"), '
         'link => link.dataset.view).sort()) === JSON.stringify(' + expected + ')')
    wait('Array.from(document.querySelectorAll("[data-go]:not(.hidden)")).every('
         'button => ' + expected + '.includes(button.dataset.go))')

def register(role):
    email = f'e2e-{uuid.uuid4()}@example.com'
    click('[data-auth="register"]')
    fill('#register-form', {'email': email, 'password': 'password123', 'role': role})
    submit('#register-form')
    wait('document.querySelector("#auth-message").textContent.includes("Conta criada")')
    submit('#login-form')
    wait('!document.querySelector("#dashboard").classList.contains("hidden")')
    check_menu(['inicio', 'matches', 'bookings', 'jogador'] if role == 'PLAYER' else ['inicio', 'quadras'])
    return email

def logout():
    click('#logout')
    wait('!document.querySelector("#auth-screen").classList.contains("hidden")')

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *unused):
        pass

class IPv6Server(http.server.ThreadingHTTPServer):
    address_family = socket.AF_INET6

handler = functools.partial(QuietHandler, directory=str(front))
try:
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 5510), handler)
    origin = 'http://127.0.0.1:5510'
except OSError as error:
    import errno
    if error.errno != errno.EADDRINUSE:
        raise
    server = IPv6Server(('::1', 5510), handler)
    origin = 'http://localhost:5510'

threading.Thread(target=server.serve_forever, daemon=True).start()
try:
    created = command('tab', 'create', '--url', origin)
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
    click('[data-view="bookings"]')
    wait('document.querySelector("#booking-slot").options.length > 0')
    fill('#booking-form', {'courtId': args.booking_court, 'visibility': 'PRIVATE'})
    evaluate('document.querySelector("#booking-court").dispatchEvent(new Event("change"))')
    wait('document.querySelector("#booking-slot").options.length > 0')
    first_slot = evaluate('document.querySelector("#booking-slot").value')
    submit('#booking-form')
    wait('document.querySelector("#payment-dialog").open && !document.querySelector("#submit-payment").disabled')
    submit('#payment-form')
    wait('!document.querySelector("#payment-form").checkValidity()')
    command('reload', '--page', page)
    command('snapshot', '--page', page, '--interactive')
    click('[data-view="bookings"]')
    wait('document.querySelector("#booking-slot").value === ' + json.dumps(first_slot.get('result', first_slot.get('value'))))
    wait('document.querySelector("#bookings-list").textContent.includes("ainda não criou")')
    fill('#booking-form', {'visibility': 'PRIVATE'})
    submit('#booking-form')
    wait('document.querySelector("#payment-dialog").open && !document.querySelector("#submit-payment").disabled')
    fill('#payment-form', {'paymentMethod': 'PIX', 'paymentAmount': '50.00'})
    submit('#payment-form')
    wait('document.querySelector("#bookings-list").textContent.includes("Privada")')
    wait('document.querySelector("#bookings-list").textContent.includes("Aberta")')
    # A successful reservation must also disappear from the available options.
    first_value = first_slot.get('result', first_slot.get('value'))
    wait('!Array.from(document.querySelector("#booking-slot").options).some(option => option.value === ' + json.dumps(first_value) + ')')
    command('reload', '--page', page)
    command('snapshot', '--page', page, '--interactive')
    click('[data-view="bookings"]')
    wait('document.querySelector("#bookings-list").textContent.includes("Privada")')
    created_match = evaluate('document.querySelector("[data-manage-match]").dataset.manageMatch')
    match_id = created_match.get('result', created_match.get('value'))
    click('[data-manage-match]')
    wait('document.querySelector("#match-manage-title").textContent.includes(' + json.dumps(str(match_id)) + ')')
    wait('!document.querySelector("#generate-invitation").disabled')
    click('#generate-invitation')
    wait('document.querySelector("#match-invitation-link").value.includes("#invite=")')
    invitation_result = evaluate('document.querySelector("#match-invitation-link").value')
    invitation_link = invitation_result.get('result', invitation_result.get('value'))
    logout()
    print('PASS: perfil e agendamento privado persistidos; reserva retira o horário livre', flush=True)

    applicant_email = register('PLAYER')
    click('[data-view="matches"]')
    fill('#invitation-form', {'code': invitation_link})
    submit('#invitation-form')
    wait('document.querySelector("#matches-list").textContent.includes("identidades ocultas")')
    wait('document.querySelector(' + json.dumps('[data-join-match="' + str(match_id) + '"]') + ') !== null')
    click('[data-join-match="' + str(match_id) + '"]')
    wait('document.querySelector("#participations-list").textContent.includes("Aguardando aprovação")')
    logout()
    fill('#login-form', {'email': player_email, 'password': 'password123'})
    submit('#login-form')
    wait('!document.querySelector("#dashboard").classList.contains("hidden")')
    click('[data-view="bookings"]')
    wait('document.querySelector("[data-manage-match]") !== null')
    click('[data-manage-match="' + str(match_id) + '"]')
    wait('document.querySelector("[data-decide-request][data-approved=true]") !== null')
    click('[data-decide-request][data-approved=true]')
    wait('document.querySelector("#match-requests").textContent.includes("Aguardando pagamento")')
    logout()
    fill('#login-form', {'email': applicant_email, 'password': 'password123'})
    submit('#login-form')
    wait('!document.querySelector("#dashboard").classList.contains("hidden")')
    click('[data-view="matches"]')
    wait('document.querySelector("#participations-list").textContent.includes("Aguardando pagamento")')
    logout()
    print('PASS: convite privado, solicitação, aprovação e estado pendente de pagamento pela interface', flush=True)

    fill('#login-form', {'email': args.booking_owner_email, 'password': 'password123'})
    submit('#login-form')
    wait('!document.querySelector("#dashboard").classList.contains("hidden")')
    click('[data-view="quadras"]')
    wait('document.querySelector("[data-court-slots]") !== null')
    click('[data-court-slots="' + args.booking_court + '"]')
    evaluate('document.querySelector("#slots-date").value=' + json.dumps(first_value.split('T')[0]) + '; document.querySelector("#slots-date").dispatchEvent(new Event("change"))')
    wait('document.querySelector("#slots-list").textContent.includes("RESERVADO")')
    wait('document.querySelector(' + json.dumps('[data-slot-start="' + first_value + '"]') + ') === null')
    click('#close-slots')
    logout()
    print('PASS: proprietário vê a reserva e não recebe ação de liberar um agendamento', flush=True)

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
    fill('#court-form', {'name': 'Quadra E2E', 'type': 'SOCIETY', 'minimumParticipants': 10})
    submit('#court-form')
    wait('document.querySelector("#courts-list").textContent.includes("Quadra E2E")')
    click('[data-court-edit]')
    fill('#court-form', {'type': 'VOLEI', 'minimumParticipants': 12})
    submit('#court-form')
    wait('document.querySelector("#courts-list").textContent.includes("12 participante")')
    wait('document.querySelector("#courts-list").textContent.includes("VOLEI")')
    click('[data-court-slots]')
    tomorrow = (datetime.now(ZoneInfo('America/Sao_Paulo')).date() + timedelta(days=1)).isoformat()
    evaluate('document.querySelector("#slots-date").value=' + json.dumps(tomorrow) + '; document.querySelector("#slots-date").dispatchEvent(new Event("change"))')
    wait('document.querySelectorAll("[data-slot-start]").length === 14')
    click('[data-slot-start]')
    wait('document.querySelector("[data-slot-start]")?.dataset.slotAvailable === "false"')
    click('#close-slots')
    click('[data-court-slots]')
    evaluate('document.querySelector("#slots-date").value=' + json.dumps(tomorrow) + '; document.querySelector("#slots-date").dispatchEvent(new Event("change"))')
    wait('document.querySelector("[data-slot-start]")?.dataset.slotAvailable === "false"')
    click('[data-slot-start]')
    wait('document.querySelector("[data-slot-start]")?.dataset.slotAvailable === "true"')
    click('#close-slots')
    logout()
    print('PASS: proprietário cadastra e configura quadra, bloqueia e libera horários persistidos', flush=True)

    fill('#login-form', {'email': args.admin_email, 'password': 'password123'})
    submit('#login-form')
    wait('!document.querySelector("#dashboard").classList.contains("hidden")')
    check_menu(['inicio', 'admin'])
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
