# Executar no emulador

Abra **a pasta `tcc_conectado` desta cópia do projeto** no Android Studio, selecione um emulador e clique em **Run**.

O build de desenvolvimento executa `iniciar-local.ps1`, que:

1. Reutiliza o MySQL do XAMPP em `C:\xampp` ou inicia-o se estiver parado.
2. Executa o instalador idempotente: cria tabelas ausentes, habilita Coordenação e preserva os usuários atuais.
3. Inicia a API PHP diretamente nesta pasta em `127.0.0.1:8088`. Não precisa copiar arquivos para `htdocs` nem ligar o Apache.
4. O emulador acessa essa API por `http://10.0.2.2:8088/php_appest/`.

Se você fechar o servidor e abrir apenas o APK já instalado, execute `iniciar-local.ps1` novamente. O APK não executa PHP/MySQL dentro do Android.

## Entrar

Acima do e-mail e senha há uma caixa com **Aluno**, **Professor** e **Coordenação**. Se o perfil selecionado não corresponder à conta, o servidor recusa o login.

As contas anteriores continuam válidas. O instalador também cria uma conta local por perfil, uma única vez, com senhas aleatórias. Consulte **`.runtime/acessos-locais.json`**, nesta pasta. O arquivo é privado, ignorado pelo Git e não é servido pela API. A conta `admin.local@academia.test` entra em **Coordenação**.

- Aluno: botão separado **Criar conta de aluno**.
- Professor: **Criar conta de professor**, com convite emitido pela coordenação.
- Coordenação: login administrativo, painel de contagens e emissão de convites. Não há cadastro público de administradores.

## IA e livros

SQL, autenticação e servidor ficam automáticos. Para geração real é necessário preencher `GEMINI_API_KEY` e `GEMINI_MODEL` em **`config.local.php`**, que fica fora de `php_appest` e não vai para o Git/APK. Use um modelo habilitado na sua conta e compatível com o endpoint e saída estruturada.

Os 30 PDFs do Drive estão catalogados. Eles **não foram todos importados/revisados**. O rascunho de Matemática básica contém nove capítulos e precisa de revisão de símbolos antes de publicação. Sem capítulos revisados, a matéria mostra uma mensagem de conteúdo indisponível, em vez de inventar um quiz. Veja `BIBLIOTECA.md` para essa preparação.

## Ambiente

- SDK desta máquina: `C:\Android\Sdk`, já configurado no `local.properties`.
- XAMPP: `C:\xampp`, PHP 8 com mysqli, mbstring e cURL.
- O banco `appest` existente foi preservado. Backup anterior à migração: `.runtime/appest-antes-login.sql`.
- O Apache e o conteúdo antigo em `C:\xampp\htdocs\php_appest` não foram alterados. O app usa a API nova na porta 8088.
- Para outra API: configure `API_BASE_URL` no `tcc_conectado/local.properties`.
- A compilação não inicia serviços. Para a API local, execute `iniciar-local.ps1` uma vez ou rode a tarefa Gradle `prepararAmbienteLocal`.
- Para hospedar a API e o banco sem XAMPP, siga `HOSPEDAGEM.md`.
- Em CI: use `-PskipLocalApi=true` para compilar sem iniciar serviços. O início automático é somente do build debug.
- Para encerrar apenas a API iniciada por este projeto: `parar-api-local.ps1`.

## Verificação

```powershell
.\iniciar-local.ps1
C:\xampp\php\php.exe php_appest\tests\login_http_test.php
C:\xampp\php\php.exe php_appest\tests\estudo_validacao_test.php
```

O teste de login verifica os três perfis, seis combinações incorretas, senha inválida, ausência de perfil, autorização da coordenação e aluno com várias matrículas. Usa somente as contas de teste geradas pelo instalador, encerra os tokens e remove a turma temporária ao terminar.

Nesta máquina: banco instalado, API respondendo, testes HTTP aprovados, `assembleDebug` compilado com sucesso e APK instalado no emulador. A integração com Gemini não foi testada por falta de credencial.
