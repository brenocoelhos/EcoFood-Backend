# Publicar catálogo compartilhado — 22/10/2026

Status: implementação pronta e `mvnw.cmd package` aprovado (42 testes).
Publicação e validação no ambiente AWS ainda pendentes. Não foi possível
autenticar pelo navegador disponível; a publicação será feita pelo usuário.

## Arquivo

`target/ecofood-shared-catalog-20261022.jar` — JAR executável Java 21, sem
`config/local.properties`, arquivos `.env` ou testes. Não envie `.jar.original`
nem compacte a pasta inteira do projeto. As variáveis de ambiente existentes
da AWS devem ser preservadas. A porta padrão 5000 configurada localmente pelo
usuário foi preservada, assim como o override `SERVER_PORT`.

## Console AWS

1. Entre no console da AWS e selecione **Ohio / us-east-2**.
2. Abra **Elastic Beanstalk → Environments → EcoFood-Backend-env**.
3. Anote a versão atualmente em execução para eventual rollback.
4. Selecione **Upload and deploy / Carregar e implantar**.
5. Escolha o JAR acima. Use o rótulo `ecofood-shared-catalog-20261022-v1`.
   Caso o rótulo já exista, use um novo sufixo; não substitua uma versão anterior.
6. Clique em **Deploy / Implantar**. Aguarde o ambiente voltar a Ready e a
   atualização concluir em Events. Confirme o rótulo da nova versão em execução
   e a saúde do ambiente. Não considere a transferência do arquivo como deploy concluído.

O Flyway aplica V7 automaticamente; ela só adiciona dois campos internos para o
rate limit em `ecofood.markets`, sem remover dados. O servidor executa a primeira
desativação de vencidos durante a inicialização e repete a cada minuto. Não é
preciso executar SQL manual, instalar CLI nem mudar o frontend.

## Confirmar na AWS após publicar

Use o frontend já configurado para o ambiente hospedado e contas de teste, sem
copiar tokens para chats, logs, screenshots ou arquivos versionados.

1. Como dono A, busque um nome/marca com pelo menos dois caracteres; confirme a
   lista e as fotos, incluindo um cadastro inativo/vencido de B. A busca deve ser
   feita na rota `/api/owner/product-catalog?q=cafe&limit=8`, retornando os cinco campos.
2. Crie uma oferta de teste usando a sugestão de B e valores de lote novos. Confirme
   status 201, UUID novo, vínculo com A e `imageUri` do produto novo.
3. Em dados de teste descartáveis, troque a foto ou exclua o cadastro fonte; confira
   que a cópia continua mostrando a foto original. Faça upload substituto na cópia.
4. Confirme que visitante recebe 401 e cliente sem mercado recebe 403 nas rotas
   de sugestão, imagem específica do catálogo e criação por catálogo.
5. Confira que ofertas vencidas ficam inativas, continuam na lista do próprio dono
   com a foto e não aparecem publicamente. Atualizar a validade não deve ativá-las:
   a reativação precisa ser explícita.

Os testes automatizados locais já cobrem falha de cópia/rollback, virada de data,
idempotência, preservação de bytes/metadados, validações e limites. Não simule
falhas de armazenamento ou altere a data do servidor de produção para repeti-los.

Para rollback do código, selecione a versão anterior em Application versions e
implante no mesmo ambiente. V7 é aditiva e compatível com a versão anterior;
não remova as colunas nem altere a tabela de histórico do Flyway.

Referência oficial: https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-platform.html
