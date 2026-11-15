# Registro dos testes

Execução em 15/11/2026, sobre o código deste repositório:

| Verificação | Comando | Resultado |
| --- | --- | --- |
| Testes unitários dos critérios e da idempotência/retentativa | `mvn test` | 8 testes aprovados |
| Sintaxe e resolução do Docker Compose | `docker compose config -q` | Aprovado |
| Construção das imagens dos dois microsserviços | `docker compose build` | Aprovado |

Os testes automatizados verificam a janela de 24 horas (inclusive o limite),
tarefas vencidas ou sem prazo, entregas já enviadas, tarefas ocultas ou em
grupo, formato de mensagens e reprocessamento de notificações falhas/concluídas.
O teste de integração com uma instalação Moodle real e o envio SMTP externo
dependem de token, disciplina e servidor configurados e não foram executados;
Mailpit está incluído para essa validação manual.
