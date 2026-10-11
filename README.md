# Teste Tecnico - Microservico CNAE

Projeto Spring Boot criado para avaliacao de candidatos a vagas de Lider Tecnico e Analista.

## Stack

- Java 25
- Spring Boot 4.1.0
- Spring Web
- Spring Data JPA
- H2 Database
- Lombok
- Maven

## Como executar

```bash
mvn spring-boot:run
```

H2 Console:

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:cnaedb
User: sa
Password:
```

## Endpoints

```http
GET /api/cnaes
GET /api/cnaes/paginado?page=0&size=20
GET /api/cnaes/buscar?termo=programas
GET /api/cnaes/codigo?codigo=6201-5/01
GET /api/cadastros-secundarios
GET /api/cadastros-secundarios/validar-cnae?codigoCnae=6201-5/01
POST /api/cadastros-secundarios
```

Comportamento esperado:

- `GET /api/cnaes` deve retornar todas as atividades cadastradas.
- `GET /api/cnaes/paginado?page={n}&size={n}` deve listar todas as atividades paginadas, ordenadas por codigo (`page` padrao 0, `size` padrao 20, maximo 100).
- `GET /api/cnaes/buscar?termo={texto}` deve buscar CNAEs que contenham o texto informado em qualquer parte da descricao, ignorando maiusculas e minusculas. Espacos no inicio e no fim do termo sao desconsiderados. Os caracteres `%` e `_` sao tratados como texto literal. Termo vazio ou com mais de 180 caracteres e rejeitado com 400.
- `GET /api/cnaes/codigo?codigo={codigo}` deve retornar o CNAE do codigo informado.
- Codigos CNAE inexistentes devem retornar uma resposta HTTP adequada para recurso nao encontrado (404 `CNAE-NEG-0001`).
- O codigo CNAE deve seguir o formato `NNNN-N/NN` (ex.: `6201-5/01`); espacos nas pontas sao desconsiderados. Fora do formato: 400 `CNAE-VAL-0006`.
- `POST /api/cadastros-secundarios` deve criar um cadastro vinculado a um CNAE existente.
- `GET /api/cadastros-secundarios/validar-cnae?codigoCnae={codigo}` deve validar se o CNAE informado pode ser usado no cadastro (inexistente: 404 `CNAE-NEG-0001`).
- Cadastros secundários com CNAE inexistente nao devem ser criados (422 `CADSEC-NEG-0001`).
- Validacao do `POST /api/cadastros-secundarios` (espacos das pontas sao removidos antes):
  - `nomeFantasia`: obrigatorio, ate 120 caracteres.
  - `documento`: obrigatorio, CNPJ com 14 caracteres sem mascara, numerico ou alfanumerico (letras minusculas sao convertidas). CPF e rejeitado com erro especifico (`CADSEC-VAL-0005`). O digito verificador nao e validado.
  - `codigoCnae`: obrigatorio, formato `NNNN-N/NN`.
  - Campos invalidos retornam 400 com a lista `errors` (um item por campo).
  - Nao pode haver dois cadastros com o mesmo CNPJ (409 `CADSEC-NEG-0002`).

Erros sao retornados no formato Problem Details (RFC 9457), com um codigo no campo `code`. Veja o significado de cada codigo em [docs/error-codes.md](docs/error-codes.md).

Exemplo:

```bash
curl http://localhost:8080/api/cnaes
curl "http://localhost:8080/api/cnaes/buscar?termo=programas"
curl "http://localhost:8080/api/cnaes/codigo?codigo=6201-5/01"
curl -X POST http://localhost:8080/api/cadastros-secundarios \
  -H "Content-Type: application/json" \
  -d '{"nomeFantasia":"Tech Porto","documento":"12345678000199","codigoCnae":"6201-5/01"}'
```

## Desafio para o candidato

Objetivo:

1. Fazer a aplicacao subir corretamente.
2. Validar os endpoints disponiveis.
3. Identificar e corrigir problemas encontrados durante a execucao.
4. Explicar as causas dos problemas e as decisoes tomadas.
5. Adicionar ou ajustar testes, quando fizer sentido.

## Entrega esperada

- Codigo corrigido em um branch ou pull request.
- Breve explicacao tecnica das alteracoes.
- Evidencias de execucao, como comandos usados, respostas dos endpoints ou testes.
