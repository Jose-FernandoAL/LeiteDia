# LeiteDia — versões incrementais de teste

Este guia registra as versões preservadas durante a rodada de melhorias da cooperativa. Nenhum dado existente foi apagado e os módulos de qualidade do leite e pagamentos continuam fora do escopo.

## Versões

| Versão | APK | Melhoria principal | Situação |
|---|---|---|---|
| 1.7 | `LeiteDia-Teste-1.7-Seguro.apk` | Sessão offline segura, sincronização e auditoria | Pública estável |
| 1.8 | `LeiteDia-1.8-Validacao.apk` | Bloqueio de lançamento duplicado online e offline | Teste salvo |
| 1.9 | `LeiteDia-1.9-Painel-Admin.apk` | Painel consolidado diário do administrador | Teste salvo |
| 2.0 RC | `LeiteDia-2.0-RC-Relatorios.apk` | Resumo CSV por período de até 366 dias | Candidata |
| 2.1 RC | `LeiteDia-2.1-RC-Controles.apk` | Fechamentos, backup, auditoria e painel de sincronização | Candidata |
| 2.2 Entrega RC | `LeiteDia-2.2-Entrega-RC-Novo-Visual.apk` | Novo visual, confirmações e backup diário sem duplicação | Candidata de entrega |

## Ordem de validação

1. Instalar a 1.8 sobre a versão atual e testar duplicidade para o mesmo produtor, data e turno, online e offline.
2. Instalar a 1.9 sobre a 1.8 e conferir totais gerais, manhã, tarde, lançamentos e usuários ativos no painel administrativo.
3. Instalar a 2.0 RC sobre a 1.9 e exportar o resumo de um período como usuário comum e como administrador para o usuário selecionado.
4. Confirmar que a planilha oficial de oito dias continua disponível.
5. Se houver erro impeditivo, retornar à versão 1.7 preservada.
6. Depois de aplicar a migração operacional, instalar a 2.1 RC e seguir `docs/VERSAO_2_1_RC.md`.

## Regras preservadas

- Cada usuário cadastra e administra os próprios produtores.
- O administrador controla usuários e pode consultar e exportar os dados do usuário selecionado.
- A sessão offline protegida exige um primeiro login conectado e tem validade máxima de sete dias.
- A instalação deve ser feita sobre a anterior, sem desinstalar, quando for necessário preservar dados locais.
- Dados de teste só devem ser removidos depois de backup e conferência antes da versão oficial.

O documento completo, com roteiro detalhado e hashes dos APKs, é gerado em `outputs/LeiteDia-Guia-de-Versoes-e-Testes.docx` no workspace de desenvolvimento.
