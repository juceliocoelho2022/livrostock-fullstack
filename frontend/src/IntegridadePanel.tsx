import { useState } from 'react';
import { RefreshCw, ShieldCheck } from 'lucide-react';
import { api, type IntegridadePdf, type Page } from './api';

/** Auditoria administrativa sob demanda: não lê todos os PDFs automaticamente. */
export default function IntegridadePanel(){
  const [pagina, setPagina] = useState(0);
  const [resultado, setResultado] = useState<Page<IntegridadePdf>|null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState('');

  async function verificar(proxima:number){
    setOcupado(true);
    setErro('');
    try {
      const dados = await api.integridadeDigital(proxima, 10);
      setResultado(dados);
      setPagina(proxima);
    } catch (e) {
      setErro((e as Error).message);
      setResultado(null);
    } finally {
      setOcupado(false);
    }
  }

  const estados = resultado?.content ?? [];
  const integros = estados.filter(item=>item.estado==='OK').length;
  const problemas = estados.length - integros;

  return <section className="panel digital-box" aria-label="Auditoria de integridade do acervo">
    <div className="digital-list-header">
      <div>
        <span className="panel-mini">ADMINISTRAÇÃO · v1.0</span>
        <h3><ShieldCheck size={19} style={{verticalAlign:'middle',marginRight:8}}/>Integridade dos PDFs</h3>
        <p className="form-help">Verificação manual por tamanho e SHA-256, sem modificar os arquivos.</p>
      </div>
      <button type="button" className="secondary-btn" disabled={ocupado}
        onClick={()=>void verificar(0)} aria-label="Verificar integridade dos PDFs">
        <RefreshCw size={15} className={ocupado?'spin':undefined}/>
        {ocupado?'Verificando...':'Verificar agora'}
      </button>
    </div>

    {erro&&<p className="digital-error" role="alert">{erro}</p>}
    {!resultado&&!erro&&<div className="digital-empty">
      Clique em <strong>Verificar agora</strong> para conferir o acervo. O processo lê apenas a página solicitada.
    </div>}
    {resultado&&<>
      <p className="form-help" role="status">
        {resultado.totalElements} PDF(s) no acervo digital · nesta página: {integros} íntegro(s) e {problemas} com problema(s).
        {resultado.totalPages>1?' Verifique as demais páginas para auditar todo o acervo.':''}
      </p>
      {estados.length===0
        ? <div className="digital-empty">Nenhum PDF nesta página.</div>
        : <div className="digital-list">{estados.map(item=>
            <div className="digital-file" key={item.exemplarId}>
              <div className="digital-file-icon"><ShieldCheck size={21}/></div>
              <div className="digital-file-details">
                <strong>{item.titulo}</strong>
                <small>{item.nomeOriginal} · {Math.round(item.tamanhoEsperadoBytes/1024)} KB esperados</small>
              </div>
              <div className="digital-actions">
                <span className={item.estado==='OK'?'digital-success':'digital-error'} aria-label={`Estado do PDF: ${item.estado}`}>
                  <strong>{item.estado}</strong>
                </span>
              </div>
            </div>
          )}</div>}
      <div className="table-footer">
        <span>Página {pagina+1} de {Math.max(1,resultado.totalPages)}</span>
        <div className="pagination">
          <button type="button" disabled={ocupado||pagina===0} aria-label="Página anterior da auditoria"
            onClick={()=>void verificar(pagina-1)}>‹</button>
          <button type="button" disabled={ocupado||pagina+1>=resultado.totalPages} aria-label="Próxima página da auditoria"
            onClick={()=>void verificar(pagina+1)}>›</button>
        </div>
      </div>
    </>}
  </section>;
}
