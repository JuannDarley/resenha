# Descoberta, participação e convites

## Escopo desta etapa

O jogador pode buscar partidas, solicitar participação, acompanhar seu estado e, como criador, aprovar/rejeitar solicitações privadas e gerar convites. A vaga continua condicionada à aprovação de pagamento na próxima etapa.

- Pública: a entrada cria `PENDING_PAYMENT`.
- Privada: a entrada de outro jogador cria `REQUESTED`. Aprovação pelo criador muda para `PENDING_PAYMENT`; rejeição muda para `REJECTED`.
- O criador também precisa solicitar sua própria participação se desejar jogar; ela vai diretamente para `PENDING_PAYMENT`, inclusive em partida privada. Criar o jogo não cria participação paga automática.
- Não há endpoint para um cliente transformar participação em `CONFIRMED`. Esse estado fica reservado à integração de pagamento. Testes podem preparar esse estado diretamente no banco temporário para verificar contagem e privacidade.
- Apenas `CONFIRMED` é contado como participante garantido. Solicitações/aprovações não atingem automaticamente o mínimo nem alteram o estado OPEN da partida.

## Regras e organização

Novas validações em `src/main/java/br/com/resenha/Resenha/validation/participation/`:

| Classe | Regra |
| --- | --- |
| ParticipationWindowValidator | Partida OPEN em local aprovado; operações estritamente antes de T−1h |
| CreatorPermissionValidator | Somente criador gerencia solicitações e convites |
| DuplicateParticipationValidator | Uma participação/solicitação por usuário e partida |
| RequestDecisionValidator | Apenas REQUESTED pode ser aprovada ou rejeitada |
| InvitationValidator | Código opcional válido para a partida privada; não dispensa aprovação |
| MatchSearchValidator | Validade dos filtros de local e horário |

`MatchDiscoveryService` faz a busca e constrói DTOs seguros. `ParticipationService` coordena transações, locks e decisões. As mutations bloqueiam a linha da partida e avaliam o relógio depois do lock. A constraint UNIQUE(booking_id,user_id) da V13 também impede duplicidade no banco. Disputas entre aprovação e rejeição só podem produzir uma decisão.

Todas as rotas exigem papel PLAYER. Não se aceita identidade do solicitante nem status controlado pelo cliente. Datas e o limite T−1h usam `America/Sao_Paulo`. Exatamente em T−1h já não é permitido solicitar, decidir ou renovar convite. A confirmação/cancelamento automático nesse instante continua para a etapa de automação.

Uma solicitação rejeitada continua registrada: reenviar retorna 409. Não foi criada política de reaplicação ou endpoint de saída/cancelamento nesta etapa. Não há teto global de participantes inventado; configuração de capacidade real permanece pendente.

## Descoberta e privacidade

`GET /matches` lista partidas OPEN futuras em estabelecimentos aprovados, públicas e privadas, com paginação e filtros combináveis:

- `date=2026-11-20`: data de início da partida, mesmo em funcionamento que atravessa a meia-noite.
- `time=10:30`: horário exato HH:mm.
- `venueId=1`: estabelecimento.
- `city=São Paulo`: cidade, sem diferenciar maiúsculas/minúsculas.
- `sport=VOLEI`: modalidade da partida.

`GET /matches/{id}` retorna o mesmo DTO seguro. Inclui local, quadra, data/hora, esporte, mínimo, visibilidade, quantidade confirmada, `createdByMe`, `joinable` e `myParticipationStatus` (somente o estado do próprio chamador). Não inclui nomes de jogadores/criador, identificadores de usuário, solicitações de outros jogadores, senha, e-mail ou hash/código de convite.

A identidade dos participantes privados é visível somente ao criador nas rotas de gerenciamento; nem um participante confirmado recebe essa lista. Em partidas públicas, a rota de participantes retorna apenas confirmados, com identificador da participação e nome de perfil ativo; sem perfil ativo, usa "Jogador". Não retorna dados de autenticação ou contato.

O criador vê nomes dos solicitantes para decidir nas próprias partidas. Outros jogadores recebem 403 nessa rota. Histórico individual só retorna registros do próprio usuário.

## Rotas

| Método e rota | Uso |
| --- | --- |
| GET /matches | Busca paginada, com filtros |
| GET /matches/{id} | Dados públicos seguros da partida |
| POST /matches/{id}/participations | Solicitar participação; HTTP 201 |
| GET /matches/{id}/participation/me | Estado da própria solicitação |
| GET /participations/me | Histórico paginado das próprias solicitações |
| GET /matches/{id}/requests | Gerenciamento paginado, exclusivo do criador |
| PATCH /matches/{id}/requests/{requestId} | Aprovar/rejeitar, exclusivo do criador |
| GET /matches/{id}/participants | Confirmados de partida pública; em privada, só o criador |
| POST /matches/{id}/invitation | Gerar/renovar convite privado, exclusivo do criador |
| POST /match-invitations/resolve | Resolver código em dados seguros da partida |

Solicitação sem convite (válida também para privada):

```json
{}
```

Solicitação com convite:

```json
{"invitationCode":"codigo-recebido"}
```

Decisão:

```json
{"approved":true}
```

Resolver convite:

```json
{"code":"codigo-recebido"}
```

Retornos: 400 para payload/filtro/convite inválido, 403 para papel/permissão inadequados, 404 para recurso/convite ausente, 409 para janela encerrada, duplicidade, partida não habilitada ou decisão já tomada.

## Convites

O código usa 32 bytes de SecureRandom em Base64 URL-safe. Somente SHA-256 é persistido; o código original é retornado na geração. Renovar substitui o hash e revoga o código anterior. Um código não permite localizar outra partida nem obter identidades. Mesmo com convite válido, outro jogador precisa de aprovação do criador.

A interface gera um link `#invite=...`, permite compartilhamento manual e aceita código ou link completo. O link pode ser aberto antes do login; depois da autenticação como PLAYER a interface resolve o convite. O código não é enviado a terceiros por ferramentas de mensagem.

## Interface e testes

A área Partidas oferece filtros, listagem paginada, solicitação de entrada, histórico individual, gestão de solicitações pelo criador e convites. A interface usa o estado retornado pelo backend e explica que aprovação não garante vaga.

`ParticipationValidatorsTest` cobre as regras isoladas; `ParticipationIntegrationTest` exercita HTTP/JWT/PostgreSQL reais, privacidade, filtros, estados, janela exata, convites revogados e concorrência de solicitações/decisões. O E2E estende o fluxo anterior com convite, solicitação de outro jogador, aprovação pelo criador e consulta de PENDING_PAYMENT pela interface. O banco de desenvolvimento não é utilizado.

Executar: `mvn test`; para incluir a interface, `mvn -Pbrowser-e2e test` (Docker, Orca e Python, conforme docs/TESTES.md).

## Próxima etapa

Integração de pagamentos para transformar aprovação em vaga garantida, tratar pagamento integral/individual e créditos corporativos. Depois, confirmação/cancelamento automático e demais ações em T−1h. Políticas de saída, cancelamento voluntário, taxas e capacidade real ainda exigem definição. Não houve cobrança, confirmação de pagamento ou reembolso nesta entrega.
