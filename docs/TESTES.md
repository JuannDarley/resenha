# Testes automatizados do RESENHA

## Executar

Requisitos: JDK 17 ou superior, Maven e Docker em execução. O primeiro uso baixa a imagem `postgres:17-alpine`.

```sh
mvn test
```

A suíte cria um PostgreSQL temporário com Testcontainers e executa as migrations Flyway reais. O Hibernate valida o schema; não cria as tabelas. As propriedades do datasource são substituídas nos testes e não utilizam o banco de desenvolvimento nem exigem configurar suas variáveis de ambiente. O container é descartado ao encerrar a JVM.

Para executar também a interface:

```sh
mvn -Pbrowser-e2e test
```

Requisitos adicionais: Python 3, Orca aberto com CLI disponível e porta 5500 livre. O frontend deve estar em `../resenha-front`, ou ser indicado com `-Dresenha.frontend=/caminho/resenha-front`. A interface fica em outro repositório; os testes não copiam nem alteram seus arquivos.

O teste inicia o servidor estático, usa a API em porta aleatória com PostgreSQL temporário e abre uma aba própria no navegador do Orca. Essa aba usa o armazenamento da origem `http://127.0.0.1:5500`: a sessão local dessa origem é limpa no início e no fim. O teste fecha a aba e o servidor ao terminar. Não use essa origem para outra sessão durante a execução.

## Cobertura por fluxo implementado

| Fluxo | Cobertura | Arquivo |
| --- | --- | --- |
| Cadastro PLAYER/OWNER e bloqueio ADMIN | Unitário e HTTP; rejeição não persiste conta | AuthenticationControllerTest, ApiIntegrationTest |
| Email duplicado, campos inválidos/ausentes | HTTP 400 | ApiIntegrationTest |
| Login e senha com hash | HTTP e verificação BCrypt no banco | ApiIntegrationTest |
| JWT: identidade, expiração, assinatura e formato inválidos | Unitário; ausência/token inválido/usuário excluído por HTTP | TokenConfigTest, ApiIntegrationTest |
| Permissões PLAYER/OWNER/ADMIN | Contexto Spring com method security e HTTP com JWT real | VenueApprovalTest, ApiIntegrationTest |
| Perfil: criar, consultar, alterar, duplicidade, desativar e isolar usuários | HTTP com verificação de persistência | ApiIntegrationTest |
| Administração de jogadores: listar, consultar, editar e desativar | HTTP; inativos e ausentes também verificados | ApiIntegrationTest |
| Estabelecimentos: criar pendente, consultar, editar, excluir | HTTP com persistência e Location | ApiIntegrationTest |
| Aprovação/rejeição e filtro de pendentes | Spring e HTTP; OWNER não altera status no CRUD | VenueApprovalTest, ApiIntegrationTest |
| Quadras: criar em local pendente, consultar, editar e excluir | HTTP e banco; exclusão em cascata do estabelecimento | ApiIntegrationTest |
| Isolamento entre proprietários | Listas, consulta, edição, exclusão e cadastro de quadra em local alheio | ApiIntegrationTest |
| Validação de quadras e estabelecimentos | HTTP 400 para campos obrigatórios e enum inválido | ApiIntegrationTest |
| Banco novo e todas as migrations | Flyway ativo + Hibernate validate + constraint de status | ApiIntegrationTest, ResenhaApplicationTests |
| Atualização V9 → V10 com estabelecimento antigo | Schema temporário separado; preserva dados e atribui PENDING | MigrationUpgradeTest |
| Interface: cadastro/login de jogador, perfil, edição após recarga, sessão e logout | Navegador → frontend real → API real → PostgreSQL | BrowserE2ETest, scripts/browser_e2e.py |
| Interface: proprietário cadastra estabelecimento e quadra; ADMIN consulta jogador | Mesmo fluxo completo, sem simular respostas da API | BrowserE2ETest, scripts/browser_e2e.py |

Os testes HTTP usam servidor real em porta aleatória, sem repositories simulados. As contas ADMIN dos testes são preparadas diretamente no banco temporário; o cadastro público permanece bloqueado.

## Correções encontradas durante a ampliação

- A edição de `/players/me` respondia sem aplicar os dados: agora chama `updateInformation`.
- JWT de conta excluída causava erro interno: agora segue sem autenticar e recebe a recusa da segurança.
- Recursos ausentes ou de outro proprietário retornavam 500: agora retornam 404.
- Perfil duplicado e segunda desativação administrativa retornavam 500: agora retornam 409.

## Limites

Aprovação/rejeição ainda não têm tela no frontend: sua cobertura completa está na API. O E2E da interface não verifica aprovação, exclusões com confirmação, layout visual ou acessibilidade. Não há meta de cobertura de linhas nem alegação de cobrir todas as combinações possíveis.

Reservas, horários, partidas, pagamentos e regras futuras não são exercitados porque ainda não estão implementados. O segredo JWT permanece no código conforme a decisão atual do projeto.
