import { useEffect, useState, type FormEvent } from 'react';
import { BookOpen, Download, ExternalLink, FilePlus2, FileText, RefreshCw, UploadCloud } from 'lucide-react';
import { api, type ExemplarDigital, type Livro } from './api';

type Props = { livros: Livro[]; onLogout?:()=>void };
const tamanho = (bytes:number) => new Intl.NumberFormat('pt-BR',{maximumFractionDigits:1}).format(bytes/1024)+' KB';
const data = (value:string) => new Date(value).toLocaleString('pt-BR');

export default function BibliotecaDigital({livros,onLogout}:Props){
  const [livroId,setLivroId]=useState<number|null>(null);
  const [exemplares,setExemplares]=useState<ExemplarDigital[]>([]);
  const [arquivo,setArquivo]=useState<File|null>(null);
  const [carregando,setCarregando]=useState(false);
  const [enviando,setEnviando]=useState(false);
  const [erro,setErro]=useState('');
  const [sucesso,setSucesso]=useState('');
  const [leitura,setLeitura]=useState<ExemplarDigital|null>(null);
  const [readerUrl,setReaderUrl]=useState<string|null>(null);
  const [lendo,setLendo]=useState(false);
  const [baixando,setBaixando]=useState<number|null>(null);
  const [modoVerificado,setModoVerificado]=useState(false);
  const [falhaModo,setFalhaModo]=useState(false);
  const [seguro,setSeguro]=useState(false);
  const [autenticado,setAutenticado]=useState(api.pdfAutenticado());
  const [perfis,setPerfis]=useState<string[]>(api.pdfPerfis());
  const [usuario,setUsuario]=useState('');
  const [senha,setSenha]=useState('');
  const [loginOcupado,setLoginOcupado]=useState(false);

  useEffect(()=>{
    let ativo=true;
    api.pdfSecurityMode().then(enabled=>{
      if(ativo){setSeguro(enabled);setModoVerificado(true);}
    }).catch(e=>{
      if(ativo){setFalhaModo(true);setErro((e as Error).message);setModoVerificado(true);}
    });
    return ()=>{ativo=false;};
  },[]);

  useEffect(()=>{
    if(livros.length===0){setLivroId(null);return;}
    setLivroId(current=>current!==null&&livros.some(l=>l.id===current)?current:livros[0].id);
  },[livros]);

  useEffect(()=>{
    if(livroId===null||!modoVerificado||falhaModo||(seguro&&!autenticado)){
      setExemplares([]);
      return;
    }
    let ativo=true;
    setCarregando(true);setErro('');setLeitura(null);setReaderUrl(null);
    api.exemplares(livroId)
      .then(items=>{if(ativo)setExemplares(items);})
      .catch(e=>{if(ativo)setErro((e as Error).message);})
      .finally(()=>{if(ativo)setCarregando(false);});
    return ()=>{ativo=false;};
  },[livroId,modoVerificado,falhaModo,seguro,autenticado]);

  // Blob URLs are local to the browser and allow inline PDF reading without placing JWTs in URLs.
  useEffect(()=>()=>{if(readerUrl)URL.revokeObjectURL(readerUrl);},[readerUrl]);

  async function autenticar(event:FormEvent<HTMLFormElement>){
    event.preventDefault();
    setLoginOcupado(true);setErro('');
    try{
      const result=await api.pdfLogin({usuario,senha});
      setSenha('');
      setPerfis(result.roles);
      setAutenticado(true);
      setSucesso('Acesso autorizado à Biblioteca Digital.');
    }catch(e){setErro((e as Error).message);}
    finally{setLoginOcupado(false);}
  }

  function sair(){
    api.pdfSair();
    setAutenticado(false);setPerfis([]);setExemplares([]);setLeitura(null);setReaderUrl(null);
    setArquivo(null);setSucesso('');setErro('');
    onLogout?.();
  }

  async function atualizar(){
    if(livroId===null)return;
    setCarregando(true);setErro('');
    try{setExemplares(await api.exemplares(livroId));}
    catch(e){setErro((e as Error).message);}
    finally{setCarregando(false);}
  }

  async function enviar(event:FormEvent<HTMLFormElement>){
    event.preventDefault();
    if(livroId===null||!arquivo)return;
    if(seguro&&!perfis.includes('BIBLIOTECARIO')){setErro('Seu perfil possui acesso somente para leitura.');return;}
    if(arquivo.size===0){setErro('Selecione um PDF não vazio.');return;}
    if(!arquivo.name.toLowerCase().endsWith('.pdf')){setErro('Selecione um arquivo .pdf.');return;}
    if(arquivo.size>20*1024*1024){setErro('O limite máximo é de 20 MiB.');return;}
    setErro('');setSucesso('');setEnviando(true);
    try{
      await api.enviarExemplar(livroId,arquivo);
      setArquivo(null);
      const input=document.getElementById('livrostock-pdf-upload') as HTMLInputElement|null;
      if(input)input.value='';
      setSucesso('PDF associado ao livro com sucesso.');
      await atualizar();
    }catch(e){setErro((e as Error).message);}
    finally{setEnviando(false);}
  }

  async function abrir(ex:ExemplarDigital){
    setLeitura(ex);setReaderUrl(null);setLendo(true);setErro('');
    try{
      const blob=await api.pdfArquivo(ex.livroId,ex.id);
      setReaderUrl(URL.createObjectURL(blob));
    }catch(e){setErro((e as Error).message);setLeitura(null);}
    finally{setLendo(false);}
  }

  async function baixar(ex:ExemplarDigital){
    setBaixando(ex.id);setErro('');
    try{
      const blob=await api.pdfArquivo(ex.livroId,ex.id,true);
      const objectUrl=URL.createObjectURL(blob);
      const link=document.createElement('a');
      link.href=objectUrl;link.download=ex.nomeOriginal;document.body.appendChild(link);link.click();link.remove();
      window.setTimeout(()=>URL.revokeObjectURL(objectUrl),60000);
    }catch(e){setErro((e as Error).message);}
    finally{setBaixando(null);}
  }

  const selecionado=livros.find(l=>l.id===livroId);
  const somenteLeitura=seguro&&!perfis.includes('BIBLIOTECARIO');
  return <section className="digital-library">
    <div className="panel digital-summary">
      <div className="digital-heading">
        <div className="digital-logo"><BookOpen size={22}/></div>
        <div><span className="panel-mini">ACERVO DIGITAL</span><h3>Biblioteca de exemplares PDF</h3>
          <p>Documentos autorizados associados ao catálogo, sem alterar o estoque físico.</p></div>
      </div>
      <div className="digital-notice">{seguro
        ?'Modo seguro habilitado: login JWT e permissões por perfil para acessar os PDFs.'
        :'Ambiente de desenvolvimento: os PDFs não exigem autenticação. Não exponha esta API publicamente.'}</div>
      {seguro&&autenticado&&<div className="digital-session"><span>Sessão ativa · {perfis.join(', ')||'LEITOR'} · token apenas em memória</span>
        <button type="button" className="secondary-btn" onClick={sair}>Sair</button></div>}
    </div>

    {!modoVerificado?<div className="loading-state"><RefreshCw size={17} className="spin"/> Verificando segurança...</div>:
    falhaModo?<p className="digital-error" role="alert">{erro||'Não foi possível verificar a segurança.'}</p>:
    seguro&&!autenticado?<div className="panel digital-box"><h3>Acessar Biblioteca Digital</h3>
      <p className="form-help">Entre com as credenciais de bibliotecário ou leitor configuradas no backend.</p>
      <form className="digital-login" onSubmit={e=>void autenticar(e)}>
        <label>Usuário<input autoComplete="username" required value={usuario} onChange={e=>setUsuario(e.target.value)}/></label>
        <label>Senha<input type="password" autoComplete="current-password" required value={senha} onChange={e=>setSenha(e.target.value)}/></label>
        <button className="primary-btn" disabled={loginOcupado} type="submit">{loginOcupado?'Autenticando...':'Entrar'}</button>
      </form>
      {erro&&<p className="digital-error" role="alert">{erro}</p>}
    </div>:
    <>
      <div className="panel digital-box">
        <label className="digital-label" htmlFor="digital-livro">Livro associado</label>
        <select id="digital-livro" className="digital-select" value={livroId??''} disabled={livros.length===0}
          onChange={e=>{setLivroId(Number(e.target.value));setArquivo(null);setSucesso('');}}>
          {livros.length===0?<option value="">Nenhum livro cadastrado</option>:
           livros.map(l=><option key={l.id} value={l.id}>{l.titulo} — {l.autor}</option>)}
        </select>
        {selecionado&&<p className="digital-book-meta">ISBN: {selecionado.isbn} · {selecionado.categoria}</p>}
        {!somenteLeitura&&<form onSubmit={e=>void enviar(e)} className="digital-upload">
          <label htmlFor="livrostock-pdf-upload"><FilePlus2 size={19}/> Selecionar arquivo PDF</label>
          <input id="livrostock-pdf-upload" type="file" accept=".pdf,application/pdf" disabled={livroId===null||enviando}
             onChange={e=>{setArquivo(e.target.files?.[0]??null);setSucesso('');}}/>
          <span className="digital-file-name">{arquivo?arquivo.name:'Nenhum arquivo selecionado'} · até 20 MiB</span>
          <button className="primary-btn" type="submit" disabled={!arquivo||livroId===null||enviando}>
            <UploadCloud size={17}/>{enviando?'Enviando...':'Adicionar PDF ao livro'}</button>
        </form>}
        {somenteLeitura&&<p className="form-help">Seu perfil permite ler e baixar PDFs, mas não enviar arquivos.</p>}
        {erro&&<p className="digital-error" role="alert">{erro}</p>}
        {sucesso&&<p className="digital-success" role="status">{sucesso}</p>}
      </div>
      <div className="panel digital-box">
        <div className="digital-list-header"><div><span className="panel-mini">DOCUMENTOS VINCULADOS</span><h3>Exemplares digitais</h3></div>
          <button type="button" className="secondary-btn" onClick={()=>void atualizar()} disabled={livroId===null||carregando}>
            <RefreshCw size={15}/> Atualizar</button></div>
        {carregando?<div className="loading-state"><RefreshCw className="spin" size={17}/> Carregando PDFs...</div>:
          exemplares.length===0?<div className="digital-empty">Nenhum PDF cadastrado para este livro.</div>:
          <div className="digital-list">{exemplares.map(ex=><div className="digital-file" key={ex.id}>
            <div className="digital-file-icon"><FileText size={23}/></div>
            <div className="digital-file-details"><strong>{ex.nomeOriginal}</strong>
              <small>{tamanho(ex.tamanhoBytes)} · enviado em {data(ex.criadoEm)} · SHA-256: {ex.sha256.slice(0,12)}…</small></div>
            <div className="digital-actions">
              <button type="button" className="secondary-btn" onClick={()=>void abrir(ex)}><BookOpen size={15}/> Ler</button>
              <button type="button" className="secondary-btn" disabled={baixando!==null} onClick={()=>void baixar(ex)}>
                <Download size={15}/>{baixando===ex.id?'Baixando...':'Baixar'}</button>
            </div>
          </div>)}</div>}
      </div>
    </>}

    {leitura&&<div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget){setLeitura(null);setReaderUrl(null);}}}>
      <div className="digital-reader" role="dialog" aria-modal="true" aria-label={'Leitor PDF: '+leitura.nomeOriginal}>
        <div className="digital-reader-top"><div><strong>{leitura.nomeOriginal}</strong><small>Visualização autorizada · PDF</small></div>
          <div className="digital-reader-actions">
            {readerUrl&&<a href={readerUrl} target="_blank" rel="noopener noreferrer" className="secondary-btn">
              <ExternalLink size={15}/> Nova aba</a>}
            <button type="button" className="secondary-btn" onClick={()=>{setLeitura(null);setReaderUrl(null);}}>Fechar</button>
          </div></div>
        {lendo?<div className="loading-state"><RefreshCw className="spin" size={17}/> Carregando arquivo...</div>:
          readerUrl?<iframe title={'Documento: '+leitura.nomeOriginal} src={readerUrl} className="digital-frame"/>:
          <p className="digital-error">Documento indisponível.</p>}
      </div>
    </div>}
  </section>;
}
