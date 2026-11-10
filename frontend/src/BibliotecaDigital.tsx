import { useEffect, useState, type FormEvent } from 'react';
import { BookOpen, Download, ExternalLink, FilePlus2, FileText, RefreshCw, UploadCloud } from 'lucide-react';
import { api, type ExemplarDigital, type Livro } from './api';

type Props = { livros: Livro[] };
const tamanho = (bytes:number) => new Intl.NumberFormat('pt-BR', {maximumFractionDigits:1}).format(bytes/1024) + ' KB';
const data = (value:string) => new Date(value).toLocaleString('pt-BR');

export default function BibliotecaDigital({livros}:Props) {
  const [livroId,setLivroId] = useState<number | null>(null);
  const [exemplares,setExemplares] = useState<ExemplarDigital[]>([]);
  const [arquivo,setArquivo] = useState<File | null>(null);
  const [carregando,setCarregando] = useState(false);
  const [enviando,setEnviando] = useState(false);
  const [erro,setErro] = useState('');
  const [sucesso,setSucesso] = useState('');
  const [leitura,setLeitura] = useState<ExemplarDigital | null>(null);

  useEffect(()=>{
    if(livros.length===0){setLivroId(null);return;}
    setLivroId(current=>current!==null && livros.some(l=>l.id===current)?current:livros[0].id);
  },[livros]);

  useEffect(()=>{
    if(livroId===null){setExemplares([]);return;}
    let ativo=true;
    setCarregando(true);
    setErro('');
    setLeitura(null);
    api.exemplares(livroId)
      .then(items=>{if(ativo)setExemplares(items);})
      .catch(e=>{if(ativo)setErro((e as Error).message);})
      .finally(()=>{if(ativo)setCarregando(false);});
    return ()=>{ativo=false;};
  },[livroId]);

  async function atualizar(){
    if(livroId===null)return;
    setCarregando(true);setErro('');
    try {setExemplares(await api.exemplares(livroId));}
    catch(e){setErro((e as Error).message);}
    finally{setCarregando(false);}
  }

  async function enviar(event:FormEvent<HTMLFormElement>){
    event.preventDefault();
    if(livroId===null || !arquivo)return;
    if(arquivo.size===0){setErro('Selecione um arquivo PDF não vazio.');return;}
    if(!arquivo.name.toLowerCase().endsWith('.pdf')){setErro('Selecione um arquivo com extensão PDF.');return;}
    if(arquivo.size>20*1024*1024){setErro('Limite máximo de 20 MiB por PDF.');return;}
    setErro('');setSucesso('');setEnviando(true);
    try{
      await api.enviarExemplar(livroId,arquivo);
      setArquivo(null);
      const input=document.getElementById('livrostock-pdf-upload') as HTMLInputElement | null;
      if(input)input.value='';
      setSucesso('PDF associado ao livro com sucesso.');
      await atualizar();
    }catch(e){setErro((e as Error).message);}
    finally{setEnviando(false);}
  }

  const livroSelecionado=livros.find(l=>l.id===livroId);
  return <section className="digital-library">
    <div className="panel digital-summary">
      <div className="digital-heading"><div className="digital-logo"><BookOpen size={22}/></div>
        <div><span className="panel-mini">ACERVO DIGITAL</span><h3>Biblioteca de exemplares PDF</h3>
        <p>Vincule documentos autorizados aos títulos cadastrados. O estoque físico permanece independente.</p></div></div>
      <div className="digital-notice">Ambiente de desenvolvimento: os arquivos ainda não têm controle de acesso. Não envie documentos sigilosos ou protegidos sem autorização.</div>
    </div>
    <div className="panel digital-box">
      <label className="digital-label" htmlFor="digital-livro">Livro associado</label>
      <select id="digital-livro" className="digital-select" value={livroId ?? ''} disabled={livros.length===0} onChange={e=>{setLivroId(Number(e.target.value));setArquivo(null);setSucesso('');}}>
        {livros.length===0?<option value="">Nenhum livro cadastrado</option>:livros.map(l=><option key={l.id} value={l.id}>{l.titulo} — {l.autor}</option>)}
      </select>
      {livroSelecionado&&<p className="digital-book-meta">ISBN: {livroSelecionado.isbn} · {livroSelecionado.categoria}</p>}
      <form onSubmit={e=>void enviar(e)} className="digital-upload">
        <label htmlFor="livrostock-pdf-upload"><FilePlus2 size={19}/> Selecionar arquivo PDF</label>
        <input id="livrostock-pdf-upload" type="file" accept=".pdf,application/pdf" disabled={livroId===null||enviando} onChange={e=>{setArquivo(e.target.files?.[0]??null);setSucesso('');}}/>
        <span className="digital-file-name">{arquivo?arquivo.name:'Nenhum arquivo selecionado'} · máximo 20 MiB</span>
        <button className="primary-btn" type="submit" disabled={!arquivo||livroId===null||enviando}><UploadCloud size={17}/>{enviando?'Enviando...':'Adicionar PDF ao livro'}</button>
      </form>
      {erro&&<p className="digital-error" role="alert">{erro}</p>}
      {sucesso&&<p className="digital-success" role="status">{sucesso}</p>}
    </div>
    <div className="panel digital-box">
      <div className="digital-list-header"><div><span className="panel-mini">DOCUMENTOS VINCULADOS</span><h3>Exemplares digitais</h3></div><button type="button" className="secondary-btn" onClick={()=>void atualizar()} disabled={livroId===null||carregando}><RefreshCw size={15}/> Atualizar</button></div>
      {carregando?<div className="loading-state"><RefreshCw className="spin" size={17}/> Carregando PDFs...</div>:exemplares.length===0?<div className="digital-empty">Nenhum PDF cadastrado para este livro.</div>:
      <div className="digital-list">{exemplares.map(ex=><div className="digital-file" key={ex.id}>
        <div className="digital-file-icon"><FileText size={23}/></div>
        <div className="digital-file-details"><strong>{ex.nomeOriginal}</strong><small>{tamanho(ex.tamanhoBytes)} · enviado em {data(ex.criadoEm)} · SHA-256: {ex.sha256.slice(0,12)}…</small></div>
        <div className="digital-actions">
          <button type="button" className="secondary-btn" onClick={()=>setLeitura(ex)}><BookOpen size={15}/> Ler</button>
          <a className="secondary-btn" href={api.urlExemplar(ex.livroId,ex.id,true)}><Download size={15}/> Baixar</a>
        </div>
      </div>)}</div>}
    </div>
    {leitura&&<div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)setLeitura(null);}}>
      <div className="digital-reader" role="dialog" aria-modal="true" aria-label={'Leitor PDF: '+leitura.nomeOriginal}>
        <div className="digital-reader-top"><div><strong>{leitura.nomeOriginal}</strong><small>Visualização do arquivo PDF</small></div><div className="digital-reader-actions">
          <a href={api.urlExemplar(leitura.livroId,leitura.id)} target="_blank" rel="noopener noreferrer" className="secondary-btn"><ExternalLink size={15}/> Nova aba</a>
          <button type="button" className="secondary-btn" onClick={()=>setLeitura(null)}>Fechar</button>
        </div></div>
        <iframe title={'Documento: '+leitura.nomeOriginal} src={api.urlExemplar(leitura.livroId,leitura.id)} className="digital-frame"/>
      </div>
    </div>}
  </section>;
}
