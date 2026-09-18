<?php
require_once __DIR__.'/auth.php';
$u=exigirUsuarioLogado($conn); $perfil=strtolower($u['tipo_perfil']);
$serie=trim($_POST['serie'] ?? ''); $mes=trim($_POST['mes_referencia'] ?? '');
if ($serie==='' || mb_strlen($serie)>40 || !preg_match('/^\d{4}-(0[1-9]|1[0-2])$/',$mes)) responderErro('Série ou mês inválido.',422);
if ($perfil==='aluno') {
    $s=$conn->prepare("SELECT 1 FROM matricula m JOIN turma t ON t.id_turma=m.id_turma WHERE m.id_aluno=? AND t.serie=? LIMIT 1");
    $s->bind_param('is',$u['id_usuario'],$serie); $s->execute();
    if ($s->get_result()->num_rows!==1) responderErro('Série não vinculada ao aluno.',403);
} elseif ($perfil!=='professor') responderErro('Perfil sem acesso ao ranking.',403);
$s=$conn->prepare("SELECT u.nome,SUM(r.pontos) AS pontos FROM ranking r JOIN usuario u ON u.id_usuario=r.id_aluno JOIN turma t ON t.id_turma=r.id_turma WHERE t.serie=? AND r.mes_referencia=? GROUP BY u.id_usuario,u.nome ORDER BY pontos DESC,u.nome");
$s->bind_param('ss',$serie,$mes); $s->execute(); $lista=[];
while($x=$s->get_result()->fetch_assoc()) { $x['pontos']=(int)$x['pontos']; $lista[]=$x; }
responderJson(['status'=>'sucesso','serie'=>$serie,'ranking'=>$lista]);
