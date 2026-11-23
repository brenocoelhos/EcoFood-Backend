# Catálogo compartilhado e expiração

## Autorização e contratos

As novas rotas exigem JWT válido do Supabase e um mercado persistido cujo
`owner_id` seja o `sub` autenticado. Esse vínculo é a definição de dono no modelo
atual; `user_metadata`, nome de usuário ou IDs enviados pelo cliente não concedem
permissão. Visitante recebe 401; usuário sem mercado recebe 403.

`GET /api/owner/product-catalog?q=cafe&limit=8` retorna somente
`[{ id, name, categoryId, imageUri, marketName }]`. `id` identifica o produto fonte.
Busca por trecho do nome completo normalizado (sem acentos, em minúsculas),
incluindo marca/embalagem. Não há campo separado de marca. Mínimo 2 caracteres
após normalização, máximo 160. `limit` padrão 8, intervalo 1–8; entradas inválidas
retornam 400. Ordem: correspondência exata, prefixo, trecho; desempate por nome
normalizado e UUID. Inclui ativos, inativos, vencidos e sem estoque de todos os
mercados. Não retorna preço, estoque, datas ou dados pessoais do dono.

`imageUri` é `null` sem foto ou `/api/owner/product-catalog/{id}/image`. Essa URL
também exige Bearer token e vínculo de dono, inclusive para fotos de fontes
inativas/vencidas. A resposta tem MIME original, `nosniff` e `Cache-Control: no-store`.
A rota pública de imagem e as permissões de edição continuam iguais.

Busca e leitura de fotos compartilham limite de 120 chamadas por dono a cada
60 segundos. O contador fica no PostgreSQL (migração V7), compartilhado entre
instâncias, atualizado atomicamente em transação independente. Exceder retorna
429 com mensagem para aguardar um minuto. Isso não impede cadastro manual.

`POST /api/owner/products/from-catalog` recebe os campos planos do `ProductInput`
mais `sourceProductId` UUID obrigatório; retorna 201 e o `OwnerProduct` completo.
Nome completo/categoria são os enviados pelo frontend, editáveis antes de salvar.
Preço, estoque e datas são obrigatoriamente os novos valores validados da requisição;
nenhum desses campos é herdado do lote fonte. `manufactureDate` continua opcional.

A foto é lida junto com a existência da fonte em um único snapshot SQL e copiada
para uma nova linha de imagem do novo UUID. Não há referência ao arquivo original,
URL arbitrária nem transferência de propriedade. Exclusão ou alteração posterior
da fonte não afeta a cópia. Sem foto na origem, cria sem foto. Fonte ausente: 404.
Falha de leitura/gravação: 503 com mensagem clara e rollback de toda a criação.
Upload posterior pelo endpoint existente substitui a foto da cópia normalmente.

## Expiração

No início do servidor e a cada minuto (`0 * * * * *`, America/Sao_Paulo), a tarefa
executa `UPDATE products SET is_active=false WHERE is_active=true AND expiration_date<hoje`.
Ela não altera `updated_at`, metadados, preços, estoque, arquivos nem qualquer outro
campo. É idempotente e segura com múltiplas instâncias. Funciona sem o aplicativo
aberto; uma indisponibilidade do servidor é coberta na próxima inicialização.
Se o banco falhar durante a tarefa periódica, a execução seguinte tenta novamente.

As consultas públicas filtram validade independentemente da tarefa. A data de
validade é inclusiva: um produto válido em 22/10 vence às 00h de 23/10 em Brasília.
Fotos e consultas do próprio dono continuam acessíveis. Reativar validade passada
retorna 400. Editar produto vencido (mesmo antes da tarefa rodar) mantém/desativa
`is_active=false`; uma validade nova permite reativação explícita, nunca automática.

## Verificação

`./mvnw.cmd test` usa banco H2 isolado. `SharedCatalogTests` verifica os novos
contratos, permissões, relevância/acento/marca, limites, cópia entre donos,
independência dos bytes, exclusão da origem, upload substituto, rollback após
falha de armazenamento, relógio de Brasília, idempotência e preservação de todos
os demais campos na expiração. O agendamento fica desabilitado apenas no perfil
de teste para não executar contra um relógio diferente do controlado pelos testes.

Nenhuma credencial ou token é necessário no pacote de implantação. Os segredos
continuam sendo lidos das variáveis de ambiente já configuradas no Elastic Beanstalk.
