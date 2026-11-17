# Agendamentos e criação inicial de partidas

## Organização do código

As regras ficam em `src/main/java/br/com/resenha/Resenha/validation/booking/`:

| Classe | Regra |
| --- | --- |
| AdvanceNoticeValidator | RN01: futuro, antecedência e corte das 18h |
| CourtEligibilityValidator | Estabelecimento aprovado, quadra configurada e esporte compatível |
| DailyCreationLimitValidator | RF08: no máximo 3 criações por usuário/dia, incluindo canceladas |
| SlotAvailabilityValidator | Funcionamento, grade de 1h, bloqueio e ocupação |
| ReservedSlotValidator | Bloqueio/liberação manual não pode sobrescrever uma reserva |
| BookingContext | Dados da tentativa e horário de referência da operação |
| BookingValidator | Contrato para as validações executadas na criação |

`BookingController` trata HTTP, autenticação e DTOs. `BookingService` coordena a transação, os locks, as validações e a persistência. As classes que implementam `BookingValidator` são componentes Spring, executados pela ordem de `@Order`; para acrescentar uma regra, criar uma implementação nesse pacote e seu teste correspondente. `ReservedSlotValidator` é chamado pelo serviço de disponibilidade.

## Regras implementadas

- Agendamento feito por usuário autenticado com papel PLAYER. A identidade vem do JWT; não é aceita do corpo da requisição.
- Mesmo dia: início com **ao menos 8 horas** de antecedência. Exatamente 8 horas é permitido.
- Após 18:00:00, não é permitido criar para o mesmo dia. Às 18:00:00 a regra de 8h continua válida e, na prática, já impede horários desse dia.
- D+1 em diante: sem exigência de 8h, sempre no futuro, dentro da grade e do funcionamento.
- Datas e contagem diária usam `America/Sao_Paulo`. A referência é capturada após obter os locks, para não validar com o horário anterior a uma espera.
- Até 3 partidas **criadas** por dia por usuário, independentemente da data para a qual foram agendadas. Canceladas também contam. Tentativas recusadas não são gravadas e não consomem cota.
- Somente quadras configuradas em estabelecimentos aprovados. A modalidade enviada deve coincidir com a modalidade da quadra.
- Um agendamento ocupa exatamente 1h. Não aceita início fora da grade nem segundos extras.
- Slots após meia-noite usam a data de abertura anterior quando o estabelecimento funciona durante a madrugada.
- Não permite sobreposição com bloqueio manual ou reserva ativa. Intervalos adjacentes são permitidos.
- Visibilidade `PUBLIC` ou `PRIVATE`; estado inicial `OPEN` (ABERTA). Esporte e mínimo de participantes são copiados para o agendamento, preservando a configuração da criação.

O registro `Booking` representa o agendamento com os dados iniciais da partida. Nesta etapa não há um segundo fluxo independente de criação de partida: reservar e criar seus dados iniciais ocorrem atomicamente.

## Concorrência e banco

A migration V12 cria a tabela `bookings`, o índice de consulta por usuário/data e uma constraint PostgreSQL de exclusão de intervalos sobrepostos na mesma quadra. Reservas canceladas não ocupam o horário. A duração de 1h e o mínimo positivo também são garantidos pelo banco.

A migration requer a extensão PostgreSQL `btree_gist`: executa `CREATE EXTENSION IF NOT EXISTS btree_gist`. Em ambientes onde o usuário da aplicação não tem essa permissão, a extensão deve ser provisionada previamente por quem administra o banco.

A criação bloqueia primeiro o usuário e depois a quadra (`PESSIMISTIC_WRITE`), dentro de uma única transação. O lock no usuário protege a cota mesmo ao criar em quadras diferentes. O lock na quadra serializa agendamentos com bloqueios manuais. A constraint no banco protege também sobreposições quando o funcionamento foi alterado.

A exclusão física de uma quadra ou estabelecimento com histórico de agendamentos é recusada com HTTP 409, preservando os registros vinculados.

## API

| Método e rota | Acesso | Resultado |
| --- | --- | --- |
| GET /availability/courts | PLAYER | Quadras aprovadas/configuradas, paginadas; sem identidade do proprietário |
| GET /availability/courts/{id}?date=2026-11-20 | PLAYER | Grade, bloqueios e reservas |
| POST /bookings | PLAYER | Criação; HTTP 201 com Location |
| GET /bookings | PLAYER | Histórico paginado do próprio criador |
| GET /bookings/{id} | PLAYER criador | Detalhe; retorna 404 para outro usuário |

Exemplo de criação:

```json
{
  "courtId": 1,
  "startsAt": "2026-11-20T18:00:00",
  "sport": "VOLEI",
  "visibility": "PRIVATE"
}
```

O início é data/hora local de São Paulo, sem offset. IDs de criador, estado, mínimo, duração e data de criação não são controlados pelo cliente.

Resposta: `id`, `courtId`, `startsAt`, `endsAt`, `createdAt`, `sport`, `visibility`, `status` e `minimumParticipants`. O histórico é privado ao criador nesta entrega; isso não é ainda a listagem pública de partidas.

A disponibilidade agora informa `status`: `AVAILABLE`, `BLOCKED` ou `RESERVED`, além do booleano `available`. O proprietário vê o horário reservado, sem botão para liberá-lo como bloqueio manual. A reserva deixa imediatamente de aparecer entre os slots livres do jogador.

Respostas de erro: 400 para RN01, grade/modalidade/dados inválidos; 403 para acesso sem permissão; 404 para recurso ausente ou agendamento de outro criador; 409 para cota diária, quadra não habilitada, bloqueio e conflito. Erros de negócio retornam uma mensagem legível no campo `message`.

## Interface

A área Agendamentos do frontend permite escolher uma quadra aprovada, consultar os horários, definir visibilidade, criar e consultar o próprio histórico. Listas de quadras e agendamentos têm paginação. A interface apresenta erros de negócio e não repete automaticamente uma criação após conflito.

A disponibilidade pode mostrar horários com menos de 8h: RN01 é uma regra da **criação**. A API é a autoridade para validar a tentativa.

Os arquivos do frontend ficam em `../resenha-front`, fora deste checkout Git, como na etapa anterior.

## Testes

- Testes unitários das validações em `src/test/java/br/com/resenha/Resenha/validation/booking`.
- `BookingIntegrationTest`: HTTP/JWT/PostgreSQL reais, relógio fixo para RN01 e limite diário; inclui concorrência entre jogadores, limite em quadras diferentes, disputa reserva × bloqueio, sobreposição após mudança de funcionamento e restrições do banco.
- E2E: jogador cria partida privada, recarrega e vê seu histórico; o horário deixa de estar livre; proprietário vê a indicação RESERVADO sem ação de liberação.
- `mvn test` executa unitários e integração. `mvn -Pbrowser-e2e test` inclui a interface. Os testes usam PostgreSQL temporário e não alteram o banco de desenvolvimento.

## Próximas etapas

Não foram implementados: participação pública/privada, solicitações/aprovações de entrada, convites, pagamentos, confirmação/cancelamento automático, sorteio e WhatsApp. Criar não garante automaticamente uma participação paga.

`CANCELLED` existe para a modelagem da ocupação e da cota, mas não há endpoint de cancelamento voluntário. As regras de cancelamento, estorno e taxas ainda precisam ser definidas. Não há transição automática para confirmação nesta etapa.
