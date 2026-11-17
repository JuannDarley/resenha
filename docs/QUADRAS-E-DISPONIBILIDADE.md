# Quadras e disponibilidade

## Configurar quadra

`POST /courts` (OWNER):

```json
{
  "name": "Quadra de vôlei",
  "type": "VOLEI",
  "venueId": 1,
  "minimumParticipants": 12
}
```

`PUT /courts` permite alterar nome, tipo e mínimo; exige `id`. O mínimo é inteiro positivo e obrigatório no cadastro; na edição, omitir mantém o valor anterior. A resposta inclui `id`, `name`, `type`, `minimumParticipants` e `venueId`.

Tipos: `SOCIETY`, `CAMPO` (Futebol), `VOLEI`, `BASQUETE` e o legado `FUTSAL`. A configuração atual tem uma modalidade por quadra, com seu próprio mínimo. Não há mínimo global imposto por modalidade nem configuração de múltiplas modalidades simultâneas na mesma quadra.

A migration V11 preserva quadras antigas, deixando o mínimo nulo até o proprietário configurá-lo. Não atribui valores de negócio arbitrários. A API responde 409 ao proprietário que consulta slots de quadra ainda não configurada.

## Funcionamento e slots

- `openingTime` e `closingTime` do estabelecimento devem ter formato HH:mm válido. Abertura e fechamento iguais são rejeitados; o intervalo deve permitir pelo menos 1 hora completa.
- Os horários atuais são usados diariamente, para todas as quadras do estabelecimento. Esta etapa não adiciona calendário semanal, feriados ou exceções de funcionamento.
- Os slots são calculados na consulta, com duração de 1 hora, a partir da abertura. Uma sobra menor que 1 hora no fechamento não gera slot.
- Exemplo: 08:30–11:45 gera 08:30–09:30, 09:30–10:30 e 10:30–11:30.
- Fechamento anterior à abertura significa funcionamento após a meia-noite: 22:00–02:00 gera quatro slots, terminando no dia seguinte.
- A data consultada é a **data de abertura**. Para a madrugada de um funcionamento iniciado ontem, consultar a data de ontem.
- Usa o fuso `America/Sao_Paulo`, independentemente do fuso do servidor. Os horários enviados/retornados são datas e horas locais, sem offset.
- Slots já iniciados não são oferecidos; datas anteriores a ontem retornam 400. Ontem é aceito para consultar eventual funcionamento que atravessou a meia-noite, retornando só os slots futuros.

## Consultar e bloquear

| Método e rota | Quem acessa | Resultado |
| --- | --- | --- |
| GET /courts/{id}/availability?date=2026-11-20 | OWNER da quadra | Slots futuros, inclusive em estabelecimento pendente |
| PUT /courts/{id}/availability | OWNER da quadra | Bloqueia/libera um slot; HTTP 204 |
| GET /availability/courts/{id}?date=2026-11-20 | PLAYER | Slots de quadra configurada em estabelecimento aprovado |

Exemplo de resposta:

```json
[
  {"startsAt":"2026-11-20T08:00:00","endsAt":"2026-11-20T09:00:00","available":true}
]
```

Exemplo de bloqueio:

```json
{"startsAt":"2026-11-20T08:00:00","available":false}
```

Enviar `available: true` libera o horário. Não é permitido bloquear fora da grade, com segundos extras ou no passado. O início deve coincidir exatamente com um slot futuro completo.

Os bloqueios duram uma hora e são persistidos por quadra/data/hora. Repetir o bloqueio é idempotente, inclusive com requisições simultâneas. A exclusão da quadra/estabelecimento remove os bloqueios no banco. Uma mudança de funcionamento não apaga bloqueios existentes: qualquer sobreposição com um bloqueio torna o novo slot indisponível. Ao liberar um slot, os bloqueios que o sobrepõem são removidos.

OWNER de outro estabelecimento recebe 404. PLAYER não pode bloquear/liberar nem consultar a rota administrativa de disponibilidade. Estabelecimentos pendentes/rejeitados e quadras sem mínimo retornam 404 na rota de jogador. Revogar a aprovação retira imediatamente o acesso do jogador.

`available` agora considera também reservas ativas. O campo `status` distingue AVAILABLE, BLOCKED e RESERVED; um bloqueio manual não pode liberar um agendamento. A criação de reservas, RN01, cota diária e concorrência estão documentadas em [AGENDAMENTOS.md](AGENDAMENTOS.md).

## Interface

No frontend `../resenha-front`, a área de quadras oferece:

- Cadastro com Vôlei/Basquete e mínimo obrigatório.
- Botão CONFIGURAR para editar modalidade e mínimo, inclusive de quadras antigas.
- Botão HORÁRIOS para escolher a data e bloquear/liberar slots.

O frontend está fora deste checkout Git; as alterações de `app.js`, `index.html` e `styles.css` devem ser incluídas no versionamento que contém essa pasta. O backend e seus testes permanecem neste repositório.

## Validação

`CourtAvailabilityServiceTest` verifica grade, intervalo incompleto, meia-noite, fuso, passado, sobreposições, aprovação e isolamento. `ApiIntegrationTest` verifica configuração, validações, persistência de bloqueios, requisições simultâneas, exclusão em cascata e permissões com HTTP/JWT reais. `MigrationUpgradeTest` preserva dados antigos e o mínimo não configurado. O E2E do navegador verifica edição da quadra e bloqueio/liberação pela interface.
