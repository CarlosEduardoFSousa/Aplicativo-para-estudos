<?php
// Executado pelo instalador local; aditivo e repetível.
function adicionarColunaMenu($conn, $tabela, $nome, $definicao) {
    $r=$conn->query("SHOW COLUMNS FROM `$tabela` LIKE '$nome'");
    if ($r->num_rows===0) $conn->query("ALTER TABLE `$tabela` ADD COLUMN `$nome` $definicao");
}
adicionarColunaMenu($conn,'turma','serie',"VARCHAR(80) NOT NULL DEFAULT ''");
adicionarColunaMenu($conn,'estudo_gerado','id_destinatario','INT NULL');
// Só reconhece série quando o próprio nome da turma já é explicitamente um ano escolar.
$conn->query("UPDATE turma SET serie=nome_turma WHERE serie='' AND nome_turma REGEXP '^[1-9].{0,2} [Aa][Nn][Oo]$'");
$conn->query("UPDATE turma SET serie='3º Ano' WHERE serie='' AND nome_turma='Turma de desenvolvimento' AND id_professor IN (SELECT id_usuario FROM usuario WHERE email='professor.local@academia.test')");
$conn->query("CREATE TABLE IF NOT EXISTS quiz_estudo (
 id_quiz INT AUTO_INCREMENT PRIMARY KEY,
 id_aluno INT NOT NULL,
 id_turma INT NULL,
 id_estudo INT NOT NULL,
 tentativa CHAR(36) NOT NULL,
 acertos INT NOT NULL,
 total INT NOT NULL,
 criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY tentativa_aluno (id_aluno,tentativa),
 FOREIGN KEY (id_aluno) REFERENCES usuario(id_usuario),
 FOREIGN KEY (id_turma) REFERENCES turma(id_turma),
 FOREIGN KEY (id_estudo) REFERENCES estudo_gerado(id_estudo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
$conn->query("CREATE TABLE IF NOT EXISTS resposta_estudo (
 id_resposta INT AUTO_INCREMENT PRIMARY KEY,
 id_quiz INT NOT NULL,
 ordem INT NOT NULL,
 assunto VARCHAR(255) NOT NULL,
 acertou TINYINT NOT NULL,
 UNIQUE KEY quiz_ordem (id_quiz,ordem),
 FOREIGN KEY (id_quiz) REFERENCES quiz_estudo(id_quiz)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
