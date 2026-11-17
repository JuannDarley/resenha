# Resenha Front

Interface web independente para testar o backend Resenha. É estática, sem etapa de build ou dependências adicionais.

## Executar

1. Inicie o backend Spring Boot na porta `8080` e o PostgreSQL.
2. Dentro desta pasta, rode um servidor estático na porta `5500`, por exemplo: `python3 -m http.server 5500`.
3. Acesse `http://localhost:5500`.

A origem `localhost:5500` já está permitida pelo CORS configurado no backend. A URL da API padrão é `http://localhost:8080`; é possível alterá-la no console do navegador com `localStorage.setItem('resenha-api', 'http://localhost:8080')`.

As sessões e o perfil escolhido no login ficam no `localStorage` do navegador. Como a resposta de login do backend contém apenas o token, a interface pede que o usuário selecione o perfil (jogador, proprietário ou administrador). O perfil precisa corresponder à função realmente atribuída à conta.

## O que a interface cobre

- Cadastro e login.
- Cadastro e consulta do perfil de jogador.
- Cadastro de estabelecimentos e quadras para proprietários.
- Consulta e desativação de jogadores para administradores.

O backend atual não possui busca pública de estabelecimentos, partidas, reservas ou agenda. A interface mostra somente os recursos disponíveis nas rotas existentes.

## Configuração de quadras e horários

O cadastro exige mínimo de participantes positivo. Os tipos também incluem Vôlei e Basquete. Na lista de quadras, CONFIGURAR permite editar tipo e mínimo; HORÁRIOS abre os slots de 1 hora por data, com bloqueio/liberação persistidos no backend.

O funcionamento diário vem dos horários de abertura/fechamento do estabelecimento. Os horários usam o fuso de São Paulo; quando atravessam a meia-noite, a data consultada é a de abertura. Os slots representam funcionamento e bloqueios manuais, sem reservar a quadra.

## Agendamentos

A área Agendamentos, para PLAYER, lista quadras aprovadas/configuradas e permite selecionar slot e visibilidade, criar uma partida aberta e consultar o próprio histórico. O backend valida RN01, limite de 3 criações/dia e conflitos. Horários reservados são identificados na área do proprietário sem opção de liberação manual. Não há ainda participação, pagamento ou cancelamento voluntário.
