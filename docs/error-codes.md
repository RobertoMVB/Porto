# Catalogo de codigos de erro

Todo erro tratado pela aplicacao tem um codigo unico, definido em
[`ErrorCode`](../src/main/java/com/porto/testecnae/error/ErrorCode.java). O mesmo codigo aparece:

- **na resposta HTTP**, no campo `code` do [Problem Details (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457);
- **no log**, no formato `errorCode=<codigo> status=<http> path=<uri> detail="<mensagem>"`.
  Para erros de negocio e validacao, o `detail` do log e a mensagem fixa do catalogo: valores enviados pelo cliente (codigo digitado, query string) **nunca** vao para o log. Isso mantem a cardinalidade baixa para metricas e evita injecao de linhas no log. A resposta HTTP pode trazer um detalhe mais especifico.

Assim, um codigo visto em log, alerta ou metrica pode ser consultado nesta pagina.

## Formato

```
<DOMINIO>-<CATEGORIA>-<NNNN>
```

| Parte | Valores | Significado |
|---|---|---|
| DOMINIO | `GEN` | Generico, vale para qualquer endpoint |
| | `CNAE` | Atividades economicas (CNAE) |
| | `CADSEC` | Cadastro secundario |
| CATEGORIA | `VAL` | Entrada invalida enviada pelo cliente (4xx) |
| | `NEG` | Regra de negocio violada ou recurso inexistente (4xx) |
| | `INT` | Falha interna ou de integracao (5xx) |
| NNNN | `0001`... | Sequencial dentro de dominio + categoria |

Regras:
- **Toda resposta de erro tem `code`**, inclusive os erros HTTP genericos (404, 405, 406, 415) e qualquer outro erro padrao do Spring (rede de seguranca `GEN-VAL-0009` / `GEN-INT-0001`). O teste `ContratoErroIntegrationTest` garante isso para todos os endpoints.
- Um codigo publicado **nunca e reaproveitado** para outro significado. Se deixar de existir, fica reservado.
- Novos codigos entram no enum `ErrorCode` **e** nesta pagina. O teste `ErrorMapperTest` garante formato, unicidade e coerencia entre categoria e status HTTP.
- Filtros uteis: `errorCode=CNAE-*` (tudo de CNAE), `errorCode=CADSEC-*` (cadastro secundario), `errorCode=*-INT-*` (falhas internas), `errorCode=*-VAL-*` (erros de entrada).

## Exemplo de resposta

`Content-Type: application/problem+json`

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "O termo de busca nao pode ser vazio.",
  "instance": "/api/cnaes/buscar",
  "code": "CNAE-VAL-0002"
}
```

### Erros de validacao do corpo (`errors`)

Quando um ou mais campos do JSON sao invalidos, a resposta traz a lista `errors`, com **de 1 a N itens** (um por campo invalido):

- `code` (raiz) e o codigo do **primeiro** erro, na ordem de declaracao dos campos. Ex.: no cadastro secundario, `nomeFantasia` → `documento` → `codigoCnae`.
- `detail` e a mensagem do erro, quando ha um so; com mais de um, informa a quantidade.
- Cada campo aparece **uma vez**, com o erro mais basico (ex.: "obrigatorio" antes de "tamanho").
- Respostas que nao sao de validacao de campo (404, 409, 422, 500, JSON malformado) **nao** trazem `errors`.

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "A requisicao possui 2 campos invalidos.",
  "instance": "/api/cadastros-secundarios",
  "code": "CADSEC-VAL-0001",
  "errors": [
    { "campo": "nomeFantasia", "code": "CADSEC-VAL-0001", "mensagem": "O nome fantasia deve ser informado." },
    { "campo": "documento",    "code": "CADSEC-VAL-0005", "mensagem": "O documento informado e um CPF. O cadastro secundario aceita apenas CNPJ." }
  ]
}
```

No log, todos os codigos aparecem em `errors=` (sem os valores enviados), para metricas por campo:
```
errorCode=CADSEC-VAL-0001 status=400 path=/api/cadastros-secundarios detail="O nome fantasia deve ser informado." errors=CADSEC-VAL-0001,CADSEC-VAL-0005
```

## Codigos

### Genericos (`GEN`)

| Codigo | HTTP | Significado | Causa provavel | Acao |
|---|---|---|---|---|
| `GEN-VAL-0001` | 400 | Parametro obrigatorio nao informado | Requisicao sem um parametro obrigatorio (ex.: `/api/cnaes/buscar` sem `termo`). O `detail` informa qual. | Cliente: enviar o parametro. |
| `GEN-VAL-0002` | 400 | Parametro com tipo ou formato invalido | Valor que nao pode ser convertido (ex.: `page=abc`). O `detail` informa qual. | Cliente: corrigir o valor. |
| `GEN-VAL-0003` | 400 | Numero da pagina invalido | `page` negativo. | Cliente: usar `page >= 0`. |
| `GEN-VAL-0004` | 400 | Tamanho da pagina invalido | `size` menor que 1 ou maior que 100. | Cliente: usar `size` entre 1 e 100. |
| `GEN-VAL-0005` | 400 | Corpo da requisicao ausente ou malformado | POST sem corpo, JSON com erro de sintaxe, ou array no lugar de objeto. A mensagem tecnica do parser nao e devolvida nem logada (pode conter trechos do corpo). | Cliente: enviar um JSON de objeto valido. |
| `GEN-VAL-0006` | 405 | Metodo HTTP nao suportado | Metodo nao mapeado no endpoint (ex.: `DELETE /api/cnaes`, `PUT /api/cadastros-secundarios`). O header `Allow` e o `detail` informam os metodos aceitos. | Cliente: usar um dos metodos do header `Allow`. |
| `GEN-VAL-0007` | 415 | Content-Type nao suportado | Corpo enviado em formato diferente de JSON (ex.: `text/plain`, `application/xml`, formulario). | Cliente: enviar `Content-Type: application/json`. |
| `GEN-VAL-0008` | 406 | Formato de resposta nao disponivel | Header `Accept` sem JSON (ex.: `Accept: application/xml`). Recusado **antes** de executar o endpoint (`produces = application/json`): nenhum efeito colateral, nem gravacao. | Cliente: aceitar `application/json` (ou nao enviar `Accept`). |
| `GEN-VAL-0009` | 4xx | Requisicao invalida (rede de seguranca) | Erro 4xx padrao do Spring sem codigo especifico (ex.: upload grande demais, header obrigatorio ausente). **O status HTTP original e preservado** (pode ser 400, 409, 413...). No log, o `detail` traz o tipo da excecao. | Time: se aparecer com frequencia, criar um codigo especifico para o caso. |
| `GEN-NEG-0001` | 404 | Rota inexistente | Endereco nao mapeado (ex.: `/api/naoexiste`, `/api/cnaes/codigo/6201-5`). Diferente de `CNAE-NEG-0001` (rota existe, CNAE nao). | Cliente: conferir o endereco. Alta frequencia pode indicar cliente com URL antiga. |
| `GEN-INT-0001` | 500 | Erro interno inesperado | Excecao nao tratada (bug, falha de banco que nao seja indisponibilidade, etc.). Tambem e a rede de seguranca para erros 5xx padrao do Spring sem codigo especifico (status original preservado). O cliente recebe mensagem generica; o detalhe tecnico e o stack trace ficam apenas no log, em nivel `ERROR`. | Time: investigar o log pelo `path` e horario. Alta frequencia indica incidente. |
| `GEN-INT-0002` | 500 | Erro ao carregar dados relacionados | `LazyInitializationException`: um relacionamento `LAZY` foi acessado fora de transacao. Com `spring.jpa.open-in-view=false`, e **sempre bug de codigo** (falta `join fetch` ou `@Transactional(readOnly = true)` no service). | Time: o stack trace no log aponta a entidade e o ponto do acesso. Qualquer ocorrencia merece correcao; nao e erro de cliente. |
| `GEN-INT-0003` | 503 | Servico temporariamente indisponivel | Banco indisponivel: `CannotCreateTransactionException` (falha ao abrir transacao), `CannotGetJdbcConnectionException` ou outra `DataAccessResourceFailureException` (sem conexao, pool esgotado). | Cliente: tentar novamente mais tarde. Time: verificar saude do banco e do pool de conexoes. Pico de ocorrencias indica incidente de infraestrutura. |

### CNAE (`CNAE`)

| Codigo | HTTP | Significado | Causa provavel | Acao |
|---|---|---|---|---|
| `CNAE-VAL-0001` | 400 | Termo de busca nao informado | O servico de busca recebeu `termo` nulo. Pela API, a ausencia do parametro gera `GEN-VAL-0001`; este codigo protege chamadas internas ao servico. | Time: verificar quem chama o servico sem termo. |
| `CNAE-VAL-0002` | 400 | Termo de busca vazio | `termo` vazio ou so com espacos (ex.: `?termo=`, `?termo=%20`). Para listar todos os CNAEs, use `GET /api/cnaes/paginado`. | Cliente: informar um termo ou usar a listagem paginada. |
| `CNAE-VAL-0003` | 400 | Termo de busca acima do tamanho maximo | `termo` com mais de 180 caracteres (contados apos remover os espacos das pontas), o tamanho da coluna `descricao`. Um termo maior nunca encontraria resultado. | Cliente: reduzir o termo. |
| `CNAE-VAL-0004` | 400 | Codigo CNAE nao informado | `codigoCnae` ausente no JSON do `POST /api/cadastros-secundarios`, ou chamada interna sem codigo. Nos endpoints GET, a ausencia do parametro gera `GEN-VAL-0001`. | Cliente: enviar `codigoCnae`. |
| `CNAE-VAL-0005` | 400 | Codigo CNAE vazio | Codigo vazio ou so com espacos (`?codigo=`, `"codigoCnae": ""`). | Cliente: informar o codigo. Se for frequente, verificar campo vazio no front-end. |
| `CNAE-VAL-0006` | 400 | Codigo CNAE com formato invalido | Codigo fora do formato `NNNN-N/NN` depois de remover espacos das pontas. Ex.: `6201501` (sem pontuacao, nao e completado), `6201-5/1`, `abc`. O valor enviado nao aparece na resposta nem no log. | Cliente: enviar no formato `NNNN-N/NN` (ex.: `6201-5/01`). |
| `CNAE-NEG-0001` | 404 | CNAE nao encontrado | Codigo em formato valido, mas nao cadastrado. Ocorre em `GET /api/cnaes/codigo` e `GET /api/cadastros-secundarios/validar-cnae`. O `detail` da resposta informa o codigo. | Cliente: conferir o codigo. Time: se um codigo oficial aparecer com frequencia, avaliar se falta na base. |

### Cadastro secundario (`CADSEC`)

Campos do `POST /api/cadastros-secundarios`. Os valores passam por normalizacao antes da validacao: espacos das pontas sao removidos (`strip()`) e o documento vai para maiusculas.

| Codigo | HTTP | Significado | Causa provavel | Acao |
|---|---|---|---|---|
| `CADSEC-VAL-0001` | 400 | Nome fantasia nao informado | `nomeFantasia` ausente, nulo, vazio ou so com espacos. | Cliente: informar o nome. |
| `CADSEC-VAL-0002` | 400 | Nome fantasia acima de 120 caracteres | Mais de 120 caracteres (contados apos remover os espacos das pontas), o tamanho da coluna. | Cliente: reduzir o nome. |
| `CADSEC-VAL-0003` | 400 | Documento nao informado | `documento` ausente, nulo, vazio ou so com espacos. | Cliente: informar o CNPJ. |
| `CADSEC-VAL-0004` | 400 | Documento com formato invalido | Nao segue `[A-Z0-9]{12}[0-9]{2}`: tamanho diferente de 14, caractere especial, **mascara** (`12.345.678/0001-95`) ou letra nos 2 ultimos digitos. Aceita CNPJ numerico e **alfanumerico** (vigente desde jul/2026). O digito verificador **nao** e validado. | Cliente: enviar o CNPJ so com letras e numeros, sem pontuacao. |
| `CADSEC-VAL-0005` | 400 | Documento e um CPF | 11 digitos, com ou sem a mascara `000.000.000-00`. O cadastro secundario aceita apenas CNPJ. | Cliente: informar o CNPJ da empresa. Alta frequencia pode indicar formulario aceitando pessoa fisica. |
| `CADSEC-NEG-0001` | 422 | Cadastro rejeitado: CNAE inexistente | `POST /api/cadastros-secundarios` com `codigoCnae` em formato valido, mas nao cadastrado. **Nada e gravado.** E 422, e nao 404, porque o endpoint existe e o JSON e valido; o que falha e a referencia ao CNAE. | Cliente: validar o CNAE antes (`GET /api/cadastros-secundarios/validar-cnae`). Time: alta frequencia indica problema de integracao no cliente (tabela de CNAE desatualizada). |
| `CADSEC-NEG-0002` | 409 | CNPJ ja cadastrado | Ja existe cadastro secundario com o mesmo `documento` (comparado apos normalizacao: espacos e maiusculas). **Nada e gravado.** Garantido tambem por constraint `unique` no banco, para requisicoes simultaneas. O CNPJ nao aparece na resposta nem no log. | Cliente: consultar o cadastro existente em vez de criar outro. |
