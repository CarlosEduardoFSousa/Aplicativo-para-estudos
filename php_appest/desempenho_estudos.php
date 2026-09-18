<?php
require_once __DIR__.'/auth.php';
$usuario = exigirUsuarioLogado($conn);
$perfil = strtolower($usuario['tipo_perfil']);
$idTurma = inteiroDoPost('id_turma');
$materia = trim($_POST['materia'] ?? '');

if ($perfil === 'aluno') {
    $idAluno = $usuario['id_usuario'];
    $sql = "SELECT h.materia, c.titulo AS topico, COUNT(*) AS respostas, SUM(h.acertou) AS acertos
            FROM historico h JOIN capitulo_livro c ON c.id_capitulo=h.id_capitulo
            WHERE h.id_aluno=? GROUP BY h.materia,c.id_capitulo,c.titulo ORDER BY h.materia,c.ordem";
    $stmt=$conn->prepare($sql); $stmt->bind_param('i',$idAluno);
} elseif ($perfil === 'professor') {
    if ($idTurma===null) responderErro('Turma inválida.',422);
    exigirTurmaDoProfessor($conn,$idTurma,$usuario['id_usuario']);
    $sql = "SELECT h.materia, c.titulo AS topico, COUNT(*) AS respostas, SUM(h.acertou) AS acertos
            FROM historico h JOIN capitulo_livro c ON c.id_capitulo=h.id_capitulo
            JOIN matricula mt ON mt.id_aluno=h.id_aluno AND mt.id_turma=?
            WHERE (?='' OR h.materia=?) GROUP BY h.materia,c.id_capitulo,c.titulo ORDER BY h.materia,c.ordem";
    $stmt=$conn->prepare($sql); $stmt->bind_param('iss',$idTurma,$materia,$materia);
} else responderErro('Perfil sem acesso a este relatório.',403);
$stmt->execute(); $r=$stmt->get_result(); $dados=[];
while($row=$r->fetch_assoc()) {
    $total=(int)$row['respostas']; $acertos=(int)$row['acertos'];
    $dados[]=['materia'=>$row['materia'],'topico'=>$row['topico'],'respostas'=>$total,'acertos'=>$acertos,'percentual'=>$total?round($acertos*100/$total,1):0];
}
responderJson(['status'=>'sucesso','topicos'=>$dados]);
