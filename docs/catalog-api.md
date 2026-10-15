# API de mercados e produtos

Base local: `http://localhost:8080/api`. Datas de produtos usam `YYYY-MM-DD`.
O frontend converte de/para `DD/MM/AAAA`. Valores monetários são números com até
2 casas decimais; o banco usa NUMERIC(10,2). Não envie preços formatados em reais.

## Banco

Migrações Flyway em `src/main/resources/db/migration` criam o schema **ecofood**:

| Tabela | Dados |
| --- | --- |
| categories | Categorias oferecidas na interface, mais Outros |
| markets | Dono (UUID do Supabase Auth), CNPJ, contato e endereço |
| market_hours | Períodos de abertura por dia da semana |
| products | Mercado, categoria, preços, estoque, fabricação, validade e status |
| product_images | Foto JPEG/PNG por produto, limitada a 5 MB e 16 MP |

`flyway_schema_history` é a tabela de controle de versões. Não edite migrações
já aplicadas; crie uma nova versão. Não há carga de mercados ou produtos fictícios.
Selecione o schema `ecofood` no Table Editor/SQL Editor do Supabase para consultar.
O schema não é exposto à API de dados do Supabase. As tabelas têm RLS ativado e
acesso de anon/authenticated revogado; o backend atual conecta com o usuário
administrativo do banco e aplica a autorização pelo dono em cada operação.

As fotos ficam no PostgreSQL nesta etapa e são servidas pela API. O catálogo
consulta só seus metadados, sem carregar os bytes das fotos junto com a lista.

## Consultas públicas

| Método | Rota | Função |
| --- | --- | --- |
| GET | /categories | Categorias |
| GET | /markets | Mercados cadastrados |
| GET | /markets/{id} | Mercado, endereço e horários públicos |
| GET | /markets/{id}/products | Ofertas disponíveis do mercado |
| GET | /products | Ofertas de todos os mercados |
| GET | /products/search?q=maca&category=frutas-legumes | Busca por nome, sem acentos, e categoria opcional |
| GET | /products/{id} | Uma oferta disponível |
| GET | /products/{id}/image | Foto da oferta |

Listagens de mercados/produtos aceitam `limit` (1–100, padrão 50) e `offset`
(padrão 0). Retornam arrays, como esperado pelo app. O frontend carrega páginas
até completar a lista. Ofertas inativas, vencidas ou sem estoque não aparecem
nas consultas públicas. Validade considera o dia em America/Sao_Paulo.

Desconto e contagem de ofertas são calculados a partir dos dados persistidos.
A localização mostra cidade/UF; distâncias em metros/km aguardam geolocalização.
Contatos pessoais do dono, email e CNPJ não são retornados nas consultas públicas.

## Operações do dono

Todas exigem `Authorization: Bearer <access_token do Supabase>`.
O dono vem do token validado: não há campo `ownerId` controlável no corpo.
Um usuário pode cadastrar um mercado; cada alteração de produto verifica esse
vínculo. Um ID pertencente a outro dono retorna 404.

| Método | Rota | Função |
| --- | --- | --- |
| POST | /owner/market | Criar o mercado do usuário logado |
| GET | /owner/market | Ler dados privados e horários do próprio mercado |
| PUT | /owner/market | Atualizar dados do próprio mercado |
| PUT | /owner/market/hours | Substituir a semana completa de horários |
| GET | /owner/products | Listar produtos próprios, inclusive inativos/vencidos/zerados |
| GET | /owner/products/{id} | Consultar um produto próprio |
| POST | /owner/products | Cadastrar produto (201) |
| PUT | /owner/products/{id} | Atualizar produto |
| PATCH | /owner/products/{id}/status | Ativar/desativar |
| DELETE | /owner/products/{id} | Excluir produto e foto (204) |
| PUT | /owner/products/{id}/image | Enviar foto multipart no campo `file` |

O cadastro de mercado recebe os campos já existentes no formulário do frontend:
marketName, marketPhone, document (CNPJ com 14 dígitos, aceitando pontuação),
postalCode, state, city, neighborhood, address, addressNumber, complement,
name, lastName, username, email, phone, referralSource.
O CNPJ deve ser único; a validação atual é de formato, não consulta a Receita.

Produto:

```json
{
  "name": "Iogurte natural",
  "categoryId": "laticinios",
  "manufactureDate": "2026-10-10",
  "expirationDate": "2026-10-20",
  "originalPrice": 10.00,
  "offerPrice": 7.50,
  "stockQuantity": 8
}
```

Atualize as datas do exemplo antes de usá-lo. Oferta deve ser positiva e menor
que o preço original, estoque inteiro entre 0 e 1.000.000 e validade não pode
estar no passado. Fabricação é opcional, não pode ser futura nem após a validade.

Status: `{"isActive":false}`. Horários: `{"days":[...]}` com os sete dias (0=domingo,
6=sábado), `enabled` e `periods:[{"opensAt":"08:00","closesAt":"12:00"}]`.
Suporta múltiplos períodos e fechamento no dia seguinte; rejeita sobreposição,
inclusive na passagem de sábado para domingo. A substituição é transacional.

Fotos de produtos não públicos só são entregues ao dono autenticado. JPEG e PNG
são reconhecidos pelo conteúdo, não apenas pela extensão. Um erro no upload após
salvar o produto é mostrado no app como falha da foto, sem repetir o cadastro.

## Erros e verificação

### Foto do mercado

`PUT /api/owner/market/image` exige Bearer token do Supabase e recebe
`multipart/form-data` com o campo `file`. O mercado é identificado exclusivamente
pelo `sub` do token; IDs enviados pelo cliente não escolhem o destino.

Aceita JPEG/PNG até **5 MB (5.242.880 bytes)**, inclusive. Arquivo vazio, conteúdo
inválido, formato diferente, imagem incompleta/corrompida ou acima de 16 megapixels
retornam 400; acima de 5 MB retorna 413. O servidor detecta o formato real e
decodifica os pixels, sem confiar na extensão ou no MIME informado. Upload inválido
preserva a foto anterior. Sem mercado cadastrado retorna 404; sem token válido, 401.

Resposta: `{ "id": "UUID", "profile": { ... }, "businessHours": null,
"imageUri": "/api/markets/UUID/image?v=VERSAO" }`. `businessHours` mantém os horários
existentes quando configurados. `imageUri` também está presente em
`GET /api/owner/market`, `GET /api/markets` e `GET /api/markets/{id}`, com `null`
quando não há foto. O frontend deve resolver a URI relativa contra o servidor da API.

`GET /api/markets/{id}/image` é público e retorna os bytes atuais com o MIME
detectado, `X-Content-Type-Options: nosniff` e `Cache-Control: no-store`. Foto ausente
retorna 404. Cada substituição gera uma nova versão na URL, inclusive quando os
bytes são iguais, evitando também o cache de imagem mantido pelo app. O parâmetro
`v` invalida o cache; a rota sempre entrega a foto atual, sem histórico de versões.

Migrações V5/V6 criam `ecofood.market_images`, com uma foto por mercado, exclusão
em cascata, RLS e acesso direto revogado para anon/authenticated. A substituição é
transacional, com bloqueio do mercado para serializar uploads do mesmo dono.
O limite multipart permanece 5 MB por arquivo e 6 MB por requisição (inclui envelope).

Os testes cobrem JPEG/PNG, substituição/URL/cache, consultas, conteúdo inválido,
arquivo truncado, limite exato e excedido, autenticação, isolamento e exclusão em cascata.

400: campos/valores inválidos; 401: sem autenticação válida; 404: registro ausente
ou de outro dono; 409: cadastro duplicado; 413: foto excede o limite.

`./mvnw.cmd test` executa testes de integração em H2 no modo PostgreSQL com as
migrações comuns, isolados do Supabase. A migração PostgreSQL adicional protege
o schema e vincula markets.owner_id a auth.users. A aplicação das migrações e
as consultas públicas também foram verificadas no PostgreSQL real.

O fluxo de cadastro/login foi deixado para depois conforme solicitado. Não há
bypass de autenticação nas rotas de escrita. Teste manual de criação pelo app
precisa de uma sessão válida e de um mercado cadastrado por esse usuário.
Favoritos, cupons, compras/reservas e aprovação documental de parceiros não fazem
parte desta etapa: suas telas ainda não possuem fluxos completos no frontend.
