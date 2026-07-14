# LeiteDia

Aplicativo Android da Cooperativa Macambira para registrar entradas diárias de leite e sincronizar os dados entre diferentes dispositivos usando Supabase.

## Recursos

- Login individual por ID e senha.
- Cadastro diário de volume, turno, temperatura, gordura e observações.
- Histórico individual do produtor.
- Exportação e compartilhamento da planilha de acompanhamento de oito dias.
- Painel administrativo com consulta por usuário.
- Cadastro, edição, ativação e desativação de usuários.
- Redefinição de senha pelo administrador.

## Tecnologias

- Kotlin
- Jetpack Compose
- Supabase Auth, PostgreSQL e Edge Functions
- Gradle

## Compilação

Abra a pasta no Android Studio ou execute no Windows:

```powershell
.\gradlew.bat assembleDebug
```

Para gerar o Android App Bundle:

```powershell
.\gradlew.bat bundleRelease
```

## Segurança

Arquivos de assinatura, senhas, `local.properties`, APKs e AABs não são versionados. A chave publicável do Supabase pode estar no cliente Android; a chave `service_role` permanece somente nos segredos das Edge Functions.
