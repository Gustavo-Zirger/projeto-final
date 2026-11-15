# Serviço de reengajamento integrado ao Moodle

O projeto implementa o escopo dos slides 1–10: um serviço consulta uma disciplina
do Moodle e publica lembretes de tarefas pendentes com vencimento nas próximas
24 horas; outro serviço consome a fila RabbitMQ, envia e-mail por SMTP e registra
o resultado em SQLite.

## Componentes

- **Acompanhamento**: consulta `mod_assign_get_assignments`,
  `core_enrol_get_enrolled_users` e `mod_assign_get_submissions` no Moodle.
- **RabbitMQ**: transporta solicitações persistentes; falhas transitórias passam
  por uma fila com intervalo antes de uma nova tentativa.
- **Notificações**: envia e-mail SMTP e guarda tentativas, falhas e envios
  concluídos no volume `notification_data`. Uma chave por curso, tarefa e aluno
  impede reenvios depois de um envio concluído.
- **Mailpit**: recebe os e-mails de demonstração e disponibiliza uma caixa de
  entrada web.
- **Moodle e PostgreSQL**: instalação já definida no `docker-compose.yaml`.

O escopo cobre uma disciplina, tarefas individuais visíveis à turma e prazo
único. Tarefas em grupo, prorrogações individuais, tarefas sem prazo, vencidas
ou já enviadas não geram lembretes. A consulta usa o prazo comum retornado pelo
Moodle; prorrogações individuais não são calculadas, então selecione atividades
sem essas exceções na demonstração.

## Preparação

1. No Moodle, habilite os serviços web e o protocolo REST. Crie um serviço
   externo com as funções `mod_assign_get_assignments`,
   `core_enrol_get_enrolled_users` e `mod_assign_get_submissions`; gere um token
   para um usuário autorizado a consultar a disciplina, as matrículas e as
   entregas. Confira as permissões e a disponibilidade dos e-mails na
   documentação da instalação Moodle utilizada.
2. Copie `.env.example` para `.env` e informe `MOODLE_TOKEN`,
   `MOODLE_COURSE_ID` e `MOODLE_PUBLIC_URL`. A URL pública deve ser a mesma
   configurada para acesso dos estudantes ao Moodle. Use credenciais próprias
   para RabbitMQ fora de uma demonstração local.
3. Inicie os serviços:

   ```sh
   docker compose up --build -d
   ```

O Moodle pode exigir a conclusão da configuração inicial antes de emitir o
token. O intervalo padrão de consulta é uma hora; para uma demonstração, ajuste
`POLL_INTERVAL_SECONDS` no `.env`.

## Demonstração e operação

- Consulte os e-mails capturados em [http://localhost:8025](http://localhost:8025)
  (no Codespaces, abra/encaminhe a porta 8025).
- Acompanhe os serviços com `docker compose logs -f acompanhamento notificacoes`.
- A interface de administração do RabbitMQ fica na porta 15672.
- Para SMTP real, configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM` e, se
  necessário, `SMTP_USERNAME`, `SMTP_PASSWORD` e `SMTP_STARTTLS` no serviço
  `notificacoes` do Compose. Não coloque tokens ou senhas no controle de versão.
- Falhas são registradas no SQLite persistente e tentadas novamente até
  `MAX_RETRIES` vezes, com atraso `RETRY_DELAY_MS`. Mensagens inválidas ou que
  esgotem as tentativas vão para `reengagement.notifications.failed`.

## Testes

Com JDK 21 e Maven:

```bash
mvn test
```

O Maven também empacota os serviços em um JAR executável com
`mvn -DskipTests package`. Os testes cobrem a janela de 24 horas, entregas já
enviadas, tarefas em grupo/ocultas/sem prazo, idempotência, retentativa e o
contrato JSON compartilhado.