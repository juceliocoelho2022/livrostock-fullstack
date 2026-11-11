import { useEffect, useState, type FormEvent } from 'react';
import { BookOpen, Download, ExternalLink, FilePlus2, FileText, RefreshCw, UploadCloud } from 'lucide-react';
import { api, type AcervoDigitalItem, type ExemplarDigital, type Livro } from './api';

type Props = { livros: Livro[]; onLogout?:()=>void };
const tamanho = (bytes:number) => new Intl.NumberFormat('pt-BR',{maximumFractionDigits:1}).format(bytes/1024)+' KB';
const data = (value:string) => new Date(value).toLocaleString('pt-BR');
const comoExemplar = (item:AcervoDigitalItem):ExemplarDigital => ({id:item.exemplarId,livroId:item.livroId,nomeOriginal:item.nomeOriginal,tipoMime:item.tipoMime,tamanhoBytes:item.tamanhoBytes,sha256:item.sha256,criadoEm:item.criadoEm});

export default function BibliotecaDigital({livros,onLogout}:Props){
  const [livroId,setLivroId]=useState<number|null>(null);
  const [exemplares,setExemplares]=useState<ExemplarDigital[]>([]);
  const [acervoDigital,setAcervoDigital]=useState<AcervoDigitalItem[]>([]);
  const [acervoTotal,setAcervoTotal]=useState(0);
  const [acervoPaginas,setAcervoPaginas]=useState(1);
  const [acervoPagina,setAcervoPagina]=useState(0);
  const [buscaDigitada,setBuscaDigitada]=useState('');
  const [buscaAplicada,setBuscaAplicada]=useState('');
  const [categoriaAcervo,setCategoriaAcervo]=useState('');
  const [carregandoAcervo,setCarregandoAcervo]=useState(false);
  const [erroAcervo,setErroAcervo]=useState('');
  const [revisaoAcervo,setRevisaoAcervo]=useState(0);
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

  // Uma unica requisicao paginada substitui as N requisicoes por livro da v0.7.
  useEffect(()=>{
    if(!modoVerificado||falhaModo||(seguro&&!autenticado)){
      setAcervoDigital([]);setAcervoTotal(0);setCarregandoAcervo(false);return;
    }
    let ativo=true;
    setCarregandoAcervo(true);setErroAcervo('');
    const consulta=seguro
      ? api.acervoDigital(acervoPagina,10,buscaAplicada,categoriaAcervo)
      : Promise.all(livros.filter(l=>l.somenteDigital).map(async livro=>({
          livro,arquivos:await api.exemplares(livro.id)
        }))).then(grupos=>{
          // Compatibilidade: perfil default local mantém a consulta legada sem JWT.
          const termo=buscaAplicada.toLocaleLowerCase('pt-BR');
          const categoria=categoriaAcervo.toLocaleLowerCase('pt-BR');
          const todos:AcervoDigitalItem[]=grupos.flatMap(({livro,arquivos})=>
            arquivos.map(ex=>({
              livroId:livro.id,titulo:livro.titulo,autor:livro.autor,categoria:livro.categoria,
              exemplarId:ex.id,nomeOriginal:ex.nomeOriginal,tipoMime:ex.tipoMime,
              tamanhoBytes:ex.tamanhoBytes,sha256:ex.sha256,criadoEm:ex.criadoEm
            }))).filter(item=>
              (!termo||`${item.titulo} ${item.autor}`.toLocaleLowerCase('pt-BR').includes(termo))
              &&(!categoria||item.categoria.toLocaleLowerCase('pt-BR')===categoria))
            .sort((a,b)=>b.criadoEm.localeCompare(a.criadoEm));
          return {content:todos.slice(acervoPagina*10,(acervoPagina+1)*10),
            totalElements:todos.length,totalPages:Math.ceil(todos.length/10)};
        });
    consulta.then(p=>{
        if(!ativo)return;
        setAcervoDigital(p.content);
        setAcervoTotal(p.totalElements);
        setAcervoPaginas(Math.max(1,p.totalPages));
      }).catch(e=>{if(ativo){setAcervoDigital([]);setErroAcervo((e as Error).message);}})
      .finally(()=>{if(ativo)setCarregandoAcervo(false);});
    return ()=>{ativo=false;};
  },[livros,modoVerificado,falhaModo,seguro,autenticado,revisaoAcervo,acervoPagina,buscaAplicada,categoriaAcervo]);

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
      setRevisaoAcervo(n=>n+1);
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

  const categorias=Array.from(new Set(livros.filter(l=>l.somenteDigital).map(l=>l.categoria))).sort((a,b)=>a.localeCompare(b,'pt-BR'));
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
        <div className="digital-list-header">
          <div><span className="panel-mini">ACERVO IMPORTADO</span><h3>Meus livros digitais</h3></div>
          <button type="button" className="secondary-btn"
             onClick={()=>setRevisaoAcervo(n=>n+1)} disabled={carregandoAcervo}>
            <RefreshCw size={15}/> Atualizar
          </button>
        </div>
        <p className="form-help">Pesquise todo o acervo por titulo ou autor. Cada pagina consulta a API segura.</p>
        <form className="table-tools" style={{padding:'15px 0'}} onSubmit={e=>{e.preventDefault();setAcervoPagina(0);setBuscaAplicada(buscaDigitada.trim());}}>
          <div className="search-field" style={{minWidth:180}}>
            <input type="search" value={buscaDigitada} aria-label="Pesquisar no acervo digital"
               placeholder="Buscar titulo ou autor" onChange={e=>setBuscaDigitada(e.target.value)}/>
          </div>
          <select className="digital-select" aria-label="Filtrar por categoria" value={categoriaAcervo}
             style={{maxWidth:225}}
             onChange={e=>{setCategoriaAcervo(e.target.value);setAcervoPagina(0);}}>
            <option value="">Todas as categorias</option>
            {categorias.map(c=><option value={c} key={c}>{c}</option>)}
          </select>
          <button type="submit" className="secondary-btn">Pesquisar</button>
        </form>
        <p className="form-help">{acervoTotal} arquivo(s) encontrado(s).</p>
        {erroAcervo&&<p className="digital-error" role="alert">{erroAcervo}</p>}
        {carregandoAcervo?<div className="loading-state"><RefreshCw className="spin" size={17}/> Carregando acervo...</div>:
         acervoDigital.length===0?<div className="digital-empty">Nenhum PDF digital encontrado no acervo.</div>:
         <div className="digital-list">
           {acervoDigital.map(item=><div className="digital-file" key={item.exemplarId}>
             <div className="digital-file-icon"><BookOpen size={23}/></div>
             <div className="digital-file-details">
               <strong>{item.titulo}</strong>
               <small>{item.autor} · {item.categoria} · {item.nomeOriginal} · {tamanho(item.tamanhoBytes)}</small>
             </div>
             <div className="digital-actions">
               <button type="button" className="secondary-btn"
                 onClick={()=>void abrir(comoExemplar(item))} aria-label={`Ler ${item.titulo}`}>
                 <BookOpen size={15}/> Ler
               </button>
               <button type="button" className="secondary-btn" disabled={baixando!==null}
                 onClick={()=>void baixar(comoExemplar(item))} aria-label={`Baixar ${item.titulo}`}>
                 <Download size={15}/>{baixando===item.exemplarId?'Baixando...':'Baixar'}
               </button>
             </div>
           </div>)}
         </div>}
        <div className="table-footer">
          <span>Pagina {acervoPagina+1} de {acervoPaginas}</span>
          <div className="pagination">
            <button type="button" aria-label="Pagina anterior" disabled={acervoPagina===0||carregandoAcervo}
              onClick={()=>setAcervoPagina(p=>p-1)}>‹</button>
            <button type="button" aria-label="Proxima pagina" disabled={acervoPagina+1>=acervoPaginas||carregandoAcervo}
              onClick={()=>setAcervoPagina(p=>p+1)}>›</button>
          </div>
        </div>
      </div>
      <div className="panel digital-box">
        <label className="digital-label" htmlFor="digital-livro">Livro associado</label>
        <select id="digital-livro" className="digital-select" value={livroId??''} disabled={livros.length===0}
          onChange={e=>{setLivroId(Number(e.target.value));setArquivo(null);setSucesso('');}}>
          {livros.length===0?<option value="">Nenhum livro cadastrado</option>:
           livros.map(l=><option key={l.id} value={l.id}>{l.titulo} — {l.autor}</option>)}
        </select>
        {selecionado&&<p className="digital-book-meta">{selecionado.somenteDigital?'Livro exclusivamente digital · ':`ISBN: ${selecionado.isbn??'não informado'} · `}{selecionado.categoria}</p>}
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
        <div className="digital-list-header"><div><span className="panel-mini">DOCUMENTOS VINCULADOS</span><h3>Exemplares do livro selecionado</h3></div>
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
