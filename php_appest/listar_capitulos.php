<?php
require_once __DIR__ . '/auth.php';
exigirUsuarioLogado($conn);
$materia = trim((string)($_POST['materia'] ?? ''));
try {
    $stmt = $conn->prepare('SELECT c.id_capitulo, c.titulo, c.ordem, l.titulo AS livro, l.fonte_url FROM capitulo_livro c JOIN livro_didatico l ON l.id_livro=c.id_livro WHERE l.ativo=1 AND c.revisado=1 AND l.materia=? ORDER BY l.titulo,c.ordem');
    $stmt->bind_param('s', $materia);
    $stmt->execute();
    responderJson(['status'=>'sucesso', 'capitulos'=>$stmt->get_result()->fetch_all(MYSQLI_ASSOC)]);
} catch (Throwable $e) {
    error_log('Biblioteca: ' . $e->getMessage());
    responderErro('Biblioteca indisponível. Verifique a instalação no servidor.', 503);
}
