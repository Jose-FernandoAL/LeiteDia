# LeiteDia 2.1 RC — controles operacionais

Arquivo de teste: `LeiteDia-2.1-RC-Controles.apk`

- `versionName`: `2.1-rc`
- `versionCode`: `12`
- SHA-256: `b8a24fe7fb7ab506fb20e8da3c961c0282a58ce37d2f9a0c5cbd335e29a12b84`

## Melhorias

- Fechamento e reabertura de períodos pelo administrador.
- Bloqueio no aplicativo e no banco para lançamentos ou correções em datas fechadas.
- Cache local dos fechamentos para manter o bloqueio durante o uso offline.
- Backup restaurável por usuário, com acesso restrito à cooperativa.
- Auditoria visível para o dono do lançamento e para o administrador.
- Painel de sincronização com registros pendentes, tentativas e último erro.
- Migração segura do SQLite local da versão 1 para a 3, sem apagar pendências.

## Banco de dados necessário

Antes de testar estas funções, execute no SQL Editor do Supabase o arquivo:

`supabase/migrations/20260716_operational_controls.sql`

A migração cria novas tabelas e políticas sem remover tabelas ou registros existentes.

## Roteiro de teste

1. Entre como administrador e abra **Controles**.
2. Feche um período curto usando datas de teste.
3. Entre como usuário e confirme que o botão de lançamento fica bloqueado para a data fechada.
4. Reabra o período e confirme que o lançamento volta a ser permitido.
5. Corrija um lançamento e abra **Auditoria** para conferir os valores anteriores e novos.
6. Crie um backup para o usuário selecionado.
7. Use **Restaurar último backup** e confirme a mensagem com as quantidades restauradas.
8. Sem internet, crie um lançamento e abra **Sincronização**.
9. Confirme produtor, data, turno, litros, tentativas e eventual mensagem de erro.
10. Reative a internet e toque em **Tentar agora**.

## Validação local

- Oito testes unitários executados.
- Zero falhas e zero erros.
- APK validado com pacote `coop.macambira.leitedia`.
- As versões 1.7, 1.8, 1.9 e 2.0 RC permanecem preservadas.

## Validação no Supabase — 16/07/2026

- Migração operacional aplicada com sucesso.
- Acesso administrativo às tabelas de fechamentos, auditoria e backups confirmado.
- Backup técnico criado e restaurado; a restauração não duplicou dados existentes.
- Período técnico de `2099-01-01` a `2099-01-02` fechado e reaberto imediatamente.
- Nenhum período atual ficou fechado e nenhum lançamento real foi alterado durante a validação.
