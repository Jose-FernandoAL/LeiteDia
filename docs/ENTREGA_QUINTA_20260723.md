# LeiteDia 2.2 — candidata de entrega

Entrega prevista: quinta-feira, 23/07/2026.

## Arquivo

- APK: `LeiteDia-2.2-Entrega-RC-Novo-Visual.apk`
- Pacote: `coop.macambira.leitedia`
- Versão: `2.2-entrega-rc`
- Código: `13`
- SHA-256: `a5b78bad5ce472085b125a0b5cce80476e6c4e9a9357acf371a2bf6d073ad252`

## Acabamento visual

- Identidade azul-marinho e azul, inspirada no painel de referência.
- Cabeçalho escuro com marca `LD` e navegação compacta para celular.
- Fundo claro, cartões brancos, bordas suaves e indicadores destacados.
- Painel administrativo dividido em produção, coletas, manhã e usuários.
- Login reorganizado em painel central com hierarquia visual mais clara.
- Tema fixo da marca, sem substituição automática pelas cores do aparelho.

## Segurança e estabilidade

- Confirmação antes de restaurar backup.
- Confirmação antes de fechar ou reabrir períodos.
- Mensagens de sucesso separadas das mensagens de erro.
- Backup automático diário no primeiro login online do dia.
- Função do Supabase impede backups automáticos duplicados.
- Teste online confirmou duas chamadas com o mesmo rótulo retornando o mesmo backup e somente uma linha no banco.
- Oito testes automatizados aprovados, sem falhas.

## Checklist de quinta-feira

1. Instalar o APK sobre a versão anterior, sem desinstalar.
2. Entrar como administrador e conferir os quatro cartões do painel.
3. Abrir um usuário e testar auditoria e criação de backup.
4. Conferir a confirmação antes da restauração.
5. Abrir **Controles** e conferir as confirmações de fechamento e reabertura.
6. Entrar como usuário e registrar uma coleta de teste.
7. Exportar a planilha oficial de oito dias e o resumo por período.
8. Testar um lançamento sem internet e conferir a tela **Sincronização**.
9. Reativar a internet e confirmar o envio do lançamento pendente.
10. Remover apenas os dados identificados como teste depois da conferência.

## Escopo preservado

- Cada usuário continua administrando apenas os próprios produtores.
- O administrador controla usuários e consulta os dados autorizados.
- Qualidade do leite e pagamentos permanecem fora desta entrega.
- As versões anteriores continuam preservadas como retorno seguro.
