# EcoFood Backend

API Java 21 / Spring Boot 4. O aplicativo Expo chama a API REST; o backend
acessa o PostgreSQL do Supabase via JDBC.

## Etapa 1 — conectar ao Supabase

Criar o projeto no Supabase já provisiona um banco PostgreSQL. Não é necessário
instalar PostgreSQL no computador para usar esse banco.

1. No painel do projeto, abra **Connect** e selecione **Session pooler**,
   porta **5432**. Essa opção funciona em redes IPv4. Copie o host, o nome
   do banco e o usuário exatamente como aparecem no painel.
2. Na raiz deste repositório, copie o modelo (apenas na primeira configuração):

   ```powershell
   Copy-Item config/local.properties.example config/local.properties
   ```

3. Preencha `config/local.properties`:

   ```properties
   DB_URL=jdbc:postgresql://HOST_DO_PAINEL:5432/postgres?sslmode=require
   DB_USERNAME=postgres.REFERENCIA_DO_PROJETO
   DB_PASSWORD=SENHA_DO_BANCO
   ```

   Use a senha do banco definida ao criar o projeto. Não use as chaves
   `anon`, `publishable` ou `service_role`. Não coloque aspas ao redor dos
   valores. Se a senha tiver uma barra invertida, escreva duas barras
   invertidas para representá-la no arquivo `.properties`.
   Esse arquivo é ignorado pelo Git: não envie a senha pelo chat nem a coloque no app Expo.

4. Execute a partir da raiz do repositório:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

5. Verifique os logs: `HikariPool-1 - Start completed` indica que o pool abriu
   uma conexão; `Started DemoApplication` indica que a aplicação terminou
   de iniciar. Interrompa com `Ctrl+C` quando terminar.

O Spring lê esse arquivo automaticamente. Como alternativa, configure as variáveis
de ambiente `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`; não é necessário um arquivo
local nesse caso. `PORT` é opcional e assume 8080.

O pool usa no máximo 5 conexões por instância. A URL de exemplo exige SSL.
O Hibernate está configurado para validar o schema, sem criar ou alterar tabelas
automaticamente. O catálogo usa SQL via Spring JDBC e migrações Flyway no schema ecofood.

### Se a conexão falhar

- `password authentication failed`: confira usuário e senha do banco.
- `UnknownHostException` ou timeout: confira o host copiado, o estado do projeto,
  a rede e eventuais restrições de acesso no Supabase. Use o Session pooler.
- `Could not resolve placeholder`: falta preencher uma das três configurações.
- Porta 8080 ocupada: defina `$env:PORT='8081'` antes de executar.

Referência: [Supabase com Spring Boot](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot).

## Testes

```powershell
.\mvnw.cmd test
```

Os testes usam o perfil `test` com H2 isolado no modo PostgreSQL, sem acessar o Supabase.
Eles verificam as rotas públicas e a autenticação usando tokens assinados e um
servidor JWKS local: assinatura, emissor, audiência, validade e identidade.
Passar nesses testes não comprova um login real no Supabase.

## Etapa 2 — Supabase Auth

O Expo cadastra o usuário no Supabase Auth. O login com e-mail ou username passa
pelo Java, que envia a senha ao Supabase Auth e devolve os tokens de sessão.
O Java recebe
`Authorization: Bearer ACCESS_TOKEN` e valida a assinatura com as chaves públicas
do projeto, além de emissor, audiência `authenticated`, expiração, role e UUID.
O projeto atual usa ES256; RS256 também é aceito. Não é necessário compartilhar
JWT secret, senha do banco ou service_role com o app.

`SUPABASE_URL` pode substituir a URL do projeto configurada em `application.properties`.
Os usuários são gerenciados em **Authentication > Users**, no schema `auth` do
Supabase. Não criamos tabela própria de senhas.

- `GET /api/markets` e suas subrotas continuam públicos e consultam o PostgreSQL.
- `GET /api/profile` exige token e retorna `id`, `email`, `name`, `lastName`, `username`.
  Nome e sobrenome vêm de `user_metadata`; username vem de `ecofood.user_logins`. Podem ser nulos. São dados
  editáveis pelo usuário, nunca fonte de permissões de administrador/dono.
- `POST /api/auth/login` aceita `{ "identifier": "email ou username", "password": "senha" }`.
  Retorna `access_token` e `refresh_token`, usados pelo Expo com `supabase.auth.setSession`.
  Configure `SUPABASE_PUBLISHABLE_KEY` no backend com a chave pública do mesmo projeto.
- `POST /api/auth/username-availability` aceita `{ "username": "seu_usuario" }` e retorna `available`.
- `POST /api/auth/register` continua retornando **410 Gone**; cadastro usa `supabase.auth.signUp`.
- As demais rotas exigem autenticação por padrão, sem sessão HTTP ou cookies.

O perfil não retorna mais os números fictícios de impacto ambiental. Os tokens
são validados localmente: logout encerra a sessão/renovação no cliente, mas um
access token já emitido pode continuar válido até expirar. Permissões de dono,
aceite auditável de termos e aprovação de parceiros ainda serão implementados.

Referências: [JWT do Supabase](https://supabase.com/docs/guides/auth/jwts) e
[Spring Security](https://docs.spring.io/spring-boot/4.0/reference/web/spring-security.html).

### Usernames

As migrações V3/V4 criam `ecofood.user_logins`, privada e com RLS, vinculada a
`auth.users`. Um trigger sincroniza cadastro, username, e-mail e exclusão. O índice
único impede dois cadastros simultâneos com o mesmo username. São aceitos 3–30
caracteres ASCII: letras, números, ponto e sublinhado, normalizados em minúsculas.
Contas antigas sem username válido ou com duplicidade permanecem acessíveis por
e-mail; a migração não atribui arbitrariamente um username a uma delas.

O endpoint de login nunca revela o e-mail de um username nem salva senhas. Retorna
erro genérico para credenciais inválidas e usa `Cache-Control: no-store` nas sessões.
O cadastro mantém a confirmação de e-mail e as restrições de SMTP do Supabase.
O login/consulta de disponibilidade têm limite local de 20 solicitações por IP a
cada cinco minutos. Na AWS, configure HTTPS e um limite compartilhado no gateway/WAF;
o limite local é por instância e usa o IP do socket, sem confiar em headers enviados
pelo cliente. Atrás de proxy, vários usuários podem compartilhar esse limite.
Não habilite logging de corpos das requisições/respostas de autenticação.

`UsernameLoginTests` usa um servidor Auth simulado: valida login pelos dois
identificadores, erros, limites e ausência de exposição de e-mail. O teste opt-in
`scripts/UsernamePostgresCheck.java` verifica o trigger no PostgreSQL real, com
dados sintéticos dentro de uma transação sempre revertida, sem envio de e-mails.
Execute com Java 21, adicionando o driver PostgreSQL ao classpath.

Referências: [Login por senha](https://supabase.com/docs/reference/javascript/auth-signinwithpassword)
e [dados de usuários e triggers](https://supabase.com/docs/guides/auth/managing-user-data).

## Próximas etapas

1. Cadastrar uma conta de consumidor no Expo, confirmar o e-mail e testar o login real.
2. Concluir o cadastro de conta e de mercado no aplicativo e validar a escrita com uma sessão real.
3. Validar o fluxo em um celular físico na mesma rede.
4. Preparar implantação na AWS com HTTPS e segredos configurados no ambiente.

Credenciais do PostgreSQL ficam somente no backend. A conexão com o banco foi
verificada. O catálogo já persiste mercados, horários, categorias, produtos e fotos.

## Catálogo persistente

Veja [contratos, tabelas e regras](docs/catalog-api.md) e [requisições de exemplo](docs/catalog-requests.http).
