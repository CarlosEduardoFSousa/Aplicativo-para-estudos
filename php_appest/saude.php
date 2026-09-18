<?php
require_once __DIR__.'/conexao.php';
header('Content-Type: application/json; charset=utf-8');
echo json_encode(['status'=>'sucesso','servico'=>'academia-genios-api','versao'=>2]);
