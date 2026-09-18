# Estudo por capítulos

Implementado: login do aluno → matérias → escolha do capítulo → resumo com páginas → quiz de cinco questões com explicação e fonte. As matérias sem capítulos publicados exibem um estado vazio.

## Estratégia

O PHP envia o texto completo do capítulo selecionado ao Gemini, sem ferramentas de busca. Não basta enviar o link da pasta: é necessário extrair, organizar e revisar cada PDF. Para este TCC, contexto por capítulo é mais simples do que manter um banco vetorial e evita que a recuperação de poucos trechos deixe partes importantes do capítulo de fora.

O servidor exige cinco questões, quatro alternativas distintas, uma correta e referências literais existentes nas páginas do capítulo. Cada parágrafo do resumo também exige referência. Isso verifica a existência da evidência, mas **não prova que a interpretação da IA está correta**. Revisão pedagógica continua recomendada. Diagramas e fórmulas ilegíveis não são tratados adequadamente por extração de texto; precisam de transcrição/OCR revisado.

## Instalação

1. No banco `appest`, execute `php_appest/biblioteca.sql` depois das migrações existentes. Não substitua o banco atual.
2. Publique os novos endpoints PHP junto da API existente. Requisitos: PHP 8+, mysqli/mysqlnd e cURL; tabelas InnoDB e UTF-8.
3. Configure `GEMINI_API_KEY` e `GEMINI_MODEL` no ambiente do processo PHP/Apache. Use um modelo disponível na sua conta com suporte a `generateContent` e `responseSchema`. Reinicie o serviço após alterar o ambiente. A chave não deve ficar no Android nem em arquivo público. A configuração antiga em `local.properties` não é mais usada.
4. Prepare os livros conforme abaixo e execute a importação CLI no servidor. Somente capítulos com `revisado: true` aparecem aos alunos.
5. Compile o Android e mantenha `ApiConfig.BASE_URL` apontando para a API instalada. O proxy/Apache deve permitir ao menos 120 segundos para a geração; o PHP aguarda até 100 segundos pelo provedor. Os próximos acessos reutilizam o cache por capítulo, dificuldade e modelo.

Atualização do login: o banco local foi preparado pelo instalador e a API local está configurada. Consulte `EXECUTAR.md`. Ainda não há credencial Gemini configurada nem publicação de todos os livros. Não há geração de exemplo fingindo ser conteúdo do livro.

## Preparação dos livros

O arquivo `biblioteca/matematica-basica.manifesto.json` registra os nove capítulos encontrados nos marcadores do PDF indicado no Drive. A contagem é a página física do PDF, começando em 1; ela difere do número impresso. Gabarito e tabelas finais ficam fora dos capítulos.

`biblioteca/catalogo-drive.json` contém o inventário dos 30 PDFs localizados na pasta compartilhada. É um inventário de fontes, não uma importação concluída. O PDF de Matemática básica foi extraído em `../tmp/pdfs/matematica-extraida.json`, ainda sem revisão. Os outros 29 PDFs precisam passar pela mesma preparação antes de aparecerem no aplicativo.

```powershell
python -m pip install pypdf
python php_appest/tools/extrair_livro.py caminho/livro.pdf biblioteca/matematica-basica.manifesto.json livro-extraido.json
```

Abra o JSON e confira todos os capítulos contra o PDF, especialmente sinais, raízes, frações, tabelas e exercícios. Corrija o texto extraído e só então marque `revisado: true` em cada capítulo conferido. O extrator sempre gera rascunhos. PDFs sem texto exigem OCR antes deste passo. Capítulos acima de 180 KB de texto JSON devem ser divididos em partes explicitamente nomeadas; a API não corta o conteúdo silenciosamente.

```powershell
cd php_appest
php tools/importar_livro.php caminho/livro-extraido.json
```

A importação é transacional, rejeita páginas repetidas e é acessível apenas por linha de comando. Reimportar exatamente o mesmo JSON é rejeitado como duplicata; editar conteúdo gera outra versão. Guarde PDFs e JSONs extraídos fora da pasta pública do servidor.

## Administração futura

As tabelas `livro_didatico`, `capitulo_livro` e `estudo_gerado` separam catálogo, conteúdo revisado e cache. Para retirar uma edição, desative-a sem apagar seu histórico:

```sql
UPDATE livro_didatico SET ativo=0 WHERE id_livro=ID_DA_EDICAO;
```

Importe a edição nova, revise e publique seus capítulos. Não edite diretamente o texto de uma versão publicada: uma nova versão garante outro cache. Uma futura tela administrativa poderá reutilizar essas regras, acrescentando perfil de administrador, upload privado, revisão e ativação/desativação. Não foram abertos endpoints de upload a alunos ou professores.

As orientações livres do professor não entram nesta geração para evitar que desviem a fonte escolhida. A dificuldade continua disponível. O ranking e o registro de respostas mantêm o comportamento anterior; sua validação de notas no servidor é uma melhoria separada.

## Validação

```powershell
php php_appest/tests/estudo_validacao_test.php
cd tcc_conectado
.\gradlew.bat :app:assembleDebug
```

Teste integrado após configurar o servidor: login de aluno, matéria vazia, capítulo revisado, geração e cache, resumo, cinco respostas, resultado, sessão expirada, indisponibilidade da IA e desativação do livro. A ausência de chave, texto insuficiente, JSON inválido ou fonte inexistente gera erro; nunca questões genéricas.

Validação atualizada: PHP e XMLs aprovados; testes do validador de estudos e testes HTTP dos três perfis passaram. A compilação `assembleDebug` terminou com sucesso e o APK foi instalado no emulador. O erro anterior de socket foi resolvido para a execução deste ambiente configurando `jdk.net.unixdomain.tmpdir`. A integração real com Gemini ainda depende da chave e de capítulos revisados.

Referência técnica: [saídas estruturadas do Gemini](https://ai.google.dev/gemini-api/docs/generate-content/structured-output). O provedor ressalta que a validade do formato não substitui a validação dos valores.
