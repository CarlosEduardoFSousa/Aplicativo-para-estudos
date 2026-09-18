<?php
require_once __DIR__.'/../estudo_validacao.php';
$paginas=[['pagina'=>2,'texto'=>'Esta é uma evidência literal do livro para os testes de referência.']];
$ref=['pagina'=>2,'trecho'=>'Esta é uma evidência literal do livro'];
$dados=['resumo'=>[array_merge(['texto'=>'Resumo de teste'],$ref)],'questoes'=>[]];
for($i=0;$i<5;$i++) $dados['questoes'][]=array_merge(['enunciado'=>'Pergunta '.$i,'explicacao'=>'Explicação','alternativas'=>[['texto'=>'A','ehCorreta'=>true],['texto'=>'B','ehCorreta'=>false],['texto'=>'C','ehCorreta'=>false],['texto'=>'D','ehCorreta'=>false]]],$ref);
validarEstudo($dados,$paginas);
$casos=[];
$x=$dados; $x['questoes'][0]['pagina']=99; $casos[]=$x;
$x=$dados; $x['resumo'][0]['trecho']='Trecho inventado inexistente no livro'; $casos[]=$x;
$x=$dados; $x['questoes'][0]['alternativas'][1]['ehCorreta']=true; $casos[]=$x;
$x=$dados; array_pop($x['questoes']); $casos[]=$x;
$x=$dados; $x['questoes'][0]['alternativas'][0]['ehCorreta']='true'; $casos[]=$x;
$x=$dados; $x['questoes'][1]=$x['questoes'][0]; $casos[]=$x;
$x=$dados; $x['questoes'][0]['alternativas'][1]['texto']='A'; $casos[]=$x;
foreach($casos as $i=>$caso) {
    try { validarEstudo($caso,$paginas); } catch (Throwable $e) { continue; }
    fwrite(STDERR,"Falha: caso $i aceito\n"); exit(1);
}
echo "OK: estudo válido e 7 rejeições de conteúdo inválido.\n";
