import { useCallback, useEffect, useState } from 'react';
import { Activity, ChevronLeft, ChevronRight, RefreshCw, ShieldCheck } from 'lucide-react';
import { api, type AuditoriaEvento, type Page } from './api';

const rotulos: Record<string,string> = {
  LIVRO_CRIAR:'Livro cadastrado',
  LIVRO_EDITAR:'Livro atualizado',
  LIVRO_DESATIVAR:'Livro desativado',
  ESTOQUE_MOVIMENTAR:'Movimentação de estoque',
  PDF_UPLOAD:'PDF enviado',
  PDF_LEITURA:'Leitura de PDF',
  PDF_DOWNLOAD:'Download de PDF'
};

export default function AuditoriaPanel(){
  const [pagina,setPagina]=useState(0);
  const [dados,setDados]=useState<Page<AuditoriaEvento>|null>(null);
  const [carregando,setCarregando]=useState(true);
  const [erro,setErro]=useState('');
  const carregar=useCallback(async()=>{
    setCarregando(true);setErro('');
    try{setDados(await api.auditoria(pagina,20));}
    catch(e){setErro((e as Error).message);}
    finally{setCarregando(false);}
  },[pagina]);
  useEffect(()=>{void carregar();},[carregar]);
  return <section className="panel audit-panel">
    <div className="audit-header"><div className="audit-title"><ShieldCheck size={22}/><div>
      <span className="panel-mini">CONTROLE E RASTREABILIDADE</span>
      <h3>Auditoria de operações</h3>
      <p>Registros de operações autenticadas concluídas no modo seguro.</p>
    </div></div>
    <button className="secondary-btn" type="button" onClick={()=>void carregar()} disabled={carregando}>
      <RefreshCw size={15}/> Atualizar</button></div>
    {erro&&<p role="alert" className="digital-error">{erro}</p>}
    {carregando?<div className="loading-state"><RefreshCw className="spin" size={17}/> Carregando auditoria...</div>:
      !dados?.content.length?<div className="digital-empty"><Activity size={19}/> Nenhuma operação registrada nesta página.</div>:
      <div className="table-scroll"><table>
        <thead><tr><th>DATA/HORA</th><th>USUÁRIO</th><th>OPERAÇÃO</th><th>RECURSO</th><th>STATUS</th></tr></thead>
        <tbody>{dados.content.map(registro=><tr key={registro.id}>
          <td>{new Date(registro.criadoEm).toLocaleString('pt-BR')}</td>
          <td>{registro.usuario}</td>
          <td><strong>{rotulos[registro.acao]??registro.acao}</strong></td>
          <td><code>{registro.rota}</code></td>
          <td>{registro.httpStatus}</td>
        </tr>)}</tbody>
      </table></div>}
    <div className="table-footer"><span>{dados?.totalElements??0} registros</span>
      <div className="pagination">
        <button aria-label="Página anterior" type="button" disabled={pagina===0||carregando} onClick={()=>setPagina(p=>p-1)}><ChevronLeft size={17}/></button>
        <span>{pagina+1} / {Math.max(1,dados?.totalPages??1)}</span>
        <button aria-label="Próxima página" type="button" disabled={carregando||!dados||dados.last} onClick={()=>setPagina(p=>p+1)}><ChevronRight size={17}/></button>
      </div></div>
  </section>;
}
