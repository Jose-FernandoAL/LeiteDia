# LeiteDia 2.2.0 — entrega para uso

Data da geração: 21/07/2026

## Arquivos oficiais

- `LeiteDia-2.2.0-Producao.apk`: instalação direta em aparelhos Android.
- `LeiteDia-2.2.0-Producao.aab`: publicação futura na Google Play.
- Pacote: `coop.macambira.leitedia`
- Código da versão: `14`
- Android mínimo: Android 8.0 (API 26)
- Android alvo: API 36

## O que foi preparado para produção

- callbacks de rede retornam à thread principal antes de atualizar a interface;
- APK e AAB usam uma chave permanente RSA de 4096 bits;
- release otimizado com R8 e remoção de recursos não usados;
- tráfego HTTP sem criptografia continua bloqueado;
- backup automático do Android foi desativado para não copiar sessão ou dados locais sensíveis;
- análise Lint concluída sem erros bloqueantes;
- testes unitários concluídos com sucesso;
- APK validado com os esquemas de assinatura Android v2 e v3;
- AAB assinado e verificado.

## Integridade dos arquivos

- APK SHA-256: `F31347097D39C144B5057A5A46ED5F9BFD9AC25A6C0169232AA27CB91EFAA911`
- AAB SHA-256: `B4869CF1E4A627E7F3BA76794486F5833620FC87AFA41971F084A6954CD4924B`
- Certificado SHA-256: `CFBD14ED26182912B743843BAA8CD5D413B39A7FC7F07E965F3336BBE6ED66F3`

## Instalação para uso direto

1. Envie somente o arquivo APK ao aparelho.
2. No Android, permita a instalação por esta fonte quando solicitado.
3. Instale o APK e faça o primeiro login com internet.
4. Cadastre um produtor e uma entrada pequena, confirme no painel e exporte uma planilha de teste.
5. Confirme que o administrador vê somente os usuários e que cada usuário vê apenas os próprios produtores.

O AAB não é instalável diretamente; ele deve ser enviado à Google Play quando houver uma conta de desenvolvedor.

## Chave de atualização

A pasta local `private-signing` contém a única chave capaz de publicar atualizações compatíveis com esta versão. Ela está ignorada pelo Git. Faça duas cópias privadas em locais seguros. Não envie essa pasta junto com o APK e não publique sua senha.

## Verificação operacional recomendada

Antes de distribuir para toda a cooperativa, faça um piloto com um administrador e dois usuários em aparelhos diferentes durante um dia. Teste login, isolamento dos produtores, lançamento, edição, exclusão com confirmação, funcionamento temporariamente sem internet, sincronização posterior, backup e exportação por período.
