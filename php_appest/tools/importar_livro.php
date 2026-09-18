<?php
// CLI somente. Nunca expor importação ou revisão aos alunos.
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
require_once __DIR__ . '/../conexao.php';
try {
    if ($argc!==2) throw new RuntimeException('Uso: php tools/importar_livro.php livro.json');
    $raw=file_get_contents($argv[1]);
    $livro=json_decode($raw,true,512,JSON_THROW_ON_ERROR);
    foreach (['titulo','materia','fonte_url','sha256_pdf'] as $k) if (!is_string($livro[$k] ?? null) || trim($livro[$k])==='') throw new RuntimeException('Campo ausente: '.$k);
    if (strlen($livro['titulo'])>255 || strlen($livro['materia'])>80 || strlen($livro['fonte_url'])>500 || !preg_match('~^https://drive\.google\.com/file/d/[a-zA-Z0-9_-]+~',$livro['fonte_url'])) throw new RuntimeException('Metadados inválidos.');
    if (empty($livro['capitulos'])) throw new RuntimeException('Sem capítulos.');
    $conn->set_charset('utf8mb4');
    // Inclui texto e revisão: alterações produzem nova versão, sem reutilizar cache.
    $versao=hash('sha256',json_encode($livro,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR));
    $conn->begin_transaction();
    $s=$conn->prepare('INSERT INTO livro_didatico (titulo,materia,fonte_url,versao) VALUES (?,?,?,?)');
    $s->bind_param('ssss',$livro['titulo'],$livro['materia'],$livro['fonte_url'],$versao); $s->execute();
    $id=$conn->insert_id; $ocupadas=[];
    foreach ($livro['capitulos'] as $i=>$cap) {
        if (!is_string($cap['titulo'] ?? null) || trim($cap['titulo'])==='' || strlen($cap['titulo'])>255 || empty($cap['paginas'])) throw new RuntimeException('Capítulo inválido.');
        $revisado=($cap['revisado'] ?? false)===true ? 1 : 0;
        foreach ($cap['paginas'] as $pagina) {
            $n=$pagina['pagina'] ?? 0;
            if (!is_int($n) || $n<1 || isset($ocupadas[$n]) || !is_string($pagina['texto'] ?? null)) throw new RuntimeException('Página inválida ou repetida.');
            $ocupadas[$n]=true;
        }
        $json=json_encode($cap['paginas'],JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR);
        if ($revisado && (strlen($json)>180000 || strlen(implode('',array_column($cap['paginas'],'texto')))<500)) throw new RuntimeException('Texto insuficiente ou extenso demais para publicação.');
        $ordem=$i+1;
        $s=$conn->prepare('INSERT INTO capitulo_livro (id_livro,ordem,titulo,paginas_json,revisado) VALUES (?,?,?,?,?)');
        $s->bind_param('iissi',$id,$ordem,$cap['titulo'],$json,$revisado); $s->execute();
    }
    $conn->commit();
    echo "Livro importado: $id. Somente capítulos revisados aparecem no app.\n";
} catch (Throwable $e) {
    $conn->rollback();
    fwrite(STDERR,'Importação cancelada: '.$e->getMessage()."\n"); exit(1);
}
