# Catalogo de codigos de erro

Todo erro tratado pela aplicacao tem um codigo unico, definido em
[`ErrorCode`](../src/main/java/com/porto/testecnae/error/ErrorCode.java). O mesmo codigo aparece:

- **na resposta HTTP**, no campo `code` do [Problem Details (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457);
- **no log**, no formato `errorCode=<codigo> status=<http> path=<uri> detail="<mensagem>"`.

Assim, um codigo visto em log, alerta ou metrica pode ser consultado nesta pagina.

## Formato

```
<DOMINIO>-<CATEGORIA>-<NNNN>
```

| Parte | Valores | Significado |
|---|---|---|
| DOMINIO | `GEN` | Generico, vale para qualquer endpoint |
| | `CNAE` | Atividades economicas (CNAE) |
| CATEGORIA | `VAL` | Entrada invalida enviada pelo cliente (4xx) |
| | `NEG` | Regra de negocio violada ou recurso inexistente (4xx) |
| | `INT` | Falha interna ou de integracao (5xx) |
| NNNN | `0001`... | Sequencial dentro de dominio + categoria |

Regras:
- Um codigo publicado **nunca e reaproveitado** para outro significado. Se deixar de existir, fica reservado.
- Novos codigos entram no enum `ErrorCode` **e** nesta pagina. O teste `ErrorMapperTest` garante formato, unicidade e coerencia entre categoria e status HTTP.
- Filtros uteis: `errorCode=CNAE-*` (tudo de CNAE), `errorCode=*-INT-*` (falhas internas), `errorCode=*-VAL-*` (erros de entrada).

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

## Codigos

### Genericos (`GEN`)

| Codigo | HTTP | Significado | Causa provavel | Acao |
|---|---|---|---|---|
| `GEN-VAL-0001` | 400 | Parametro obrigatorio nao informado | Requisicao sem um parametro obrigatorio (ex.: `/api/cnaes/buscar` sem `termo`). O `detail` informa qual. | Cliente: enviar o parametro. |
| `GEN-VAL-0002` | 400 | Parametro com tipo ou formato invalido | Valor que nao pode ser convertido (ex.: `page=abc`). O `detail` informa qual. | Cliente: corrigir o valor. |
| `GEN-VAL-0003` | 400 | Numero da pagina invalido | `page` negativo. | Cliente: usar `page >= 0`. |
| `GEN-VAL-0004` | 400 | Tamanho da pagina invalido | `size` menor que 1 ou maior que 100. | Cliente: usar `size` entre 1 e 100. |
| `GEN-INT-0001` | 500 | Erro interno inesperado | Excecao nao tratada (banco indisponivel, bug, etc.). O cliente recebe mensagem generica; o detalhe tecnico e o stack trace ficam apenas no log, em nivel `ERROR`. | Time: investigar o log pelo `path` e horario. Alta frequencia indica incidente. |

### CNAE (`CNAE`)

| Codigo | HTTP | Significado | Causa provavel | Acao |
|---|---|---|---|---|
| `CNAE-VAL-0001` | 400 | Termo de busca nao informado | O servico de busca recebeu `termo` nulo. Pela API, a ausencia do parametro gera `GEN-VAL-0001`; este codigo protege chamadas internas ao servico. | Time: verificar quem chama o servico sem termo. |
| `CNAE-VAL-0002` | 400 | Termo de busca vazio | `termo` vazio ou so com espacos (ex.: `?termo=`, `?termo=%20`). Para listar todos os CNAEs, use `GET /api/cnaes/paginado`. | Cliente: informar um termo ou usar a listagem paginada. |
| `CNAE-VAL-0003` | 400 | Termo de busca acima do tamanho maximo | `termo` com mais de 180 caracteres, o tamanho da coluna `descricao`. Um termo maior nunca encontraria resultado. | Cliente: reduzir o termo. |
