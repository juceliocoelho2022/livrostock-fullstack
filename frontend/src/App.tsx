import UsuariosPanel from './UsuariosPanel';
import AuditoriaPanel from './AuditoriaPanel';
import BibliotecaDigital from './BibliotecaDigital';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Activity, ArrowDownLeft, ArrowLeft, ArrowRight, ArrowUpRight, BookOpen, Boxes, Check, ChevronLeft, ChevronRight, CircleAlert, Clock3, Edit3, Filter, LayoutDashboard, LibraryBig, Menu, MoreHorizontal, FileText, Plus, RefreshCw, Search, ShieldCheck, Trash2, TrendingUp, Wallet, X } from 'lucide-react';
import {api,type Livro,type LivroPayload,type Movimento} from './api';
type Tema = 'light'|'dark'|'moderado';
const temas:ReadonlyArray<Tema>=['light','moderado','dark'];

type View='dashboard'|'livros'|'movimentacoes'|'alertas'|'digital'|'auditoria'|'usuarios';
type Modal={type:'novo'}|{type:'editar';livro:Livro}|{type:'movimentar';livro:Livro;modo:'ENTRADA'|'SAIDA'}|{type:'historico';livro:Livro}|{type:'excluir';livro:Livro}|null;
const currency=(n:number)=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(n);
const date=(s:string)=>new Date(s).toLocaleString('pt-BR',{dateStyle:'short',timeStyle:'short'});
const initials=(s:string)=>s.trim().slice(0,1).toUpperCase();
const palettes=['#dae6df','#eadfd3','#e0dfed','#dce6ec','#ebe6d5','#f0dddd'];
function Cover({livro,large=false}:{livro:Livro;large?:boolean}){const color=palettes[(livro.id||0)%palettes.length];return <div className={'book-cover '+(large?'large':'')} style={{background:color}}><div className="cover-spine"/><BookOpen size={large?30:22} strokeWidth={1.5}/><span>{initials(livro.titulo)}</span></div>}
function Pill({low,digital}:{low:boolean;digital:boolean}){return <span className={'pill '+(digital?'digital':low?'low':'ok')}><span className="pill-dot"/>{digital?'Livro digital':low?'Estoque baixo':'Em estoque'}</span>}
function IconButton({title,onClick,children}:{title:string;onClick:()=>void;children:ReactNode}){return <button type="button" className="icon-btn" title={title} aria-label={title} onClick={onClick}>{children}</button>}
function Blank({title,description,children}:{title:string;description:string;children?:ReactNode}){return <div className="blank"><div className="blank-icon"><BookOpen size={26}/></div><h3>{title}</h3><p>{description}</p>{children}</div>}
function App(){
 const [view,setView]=useState<View>('dashboard'); const [livros,setLivros]=useState<Livro[]>([]);const [total,setTotal]=useState(0);const [apiOnline,setApiOnline]=useState<boolean|null>(null);const [loading,setLoading]=useState(true);const [error,setError]=useState('');const [toast,setToast]=useState('');const [modal,setModal]=useState<Modal>(null);const [busca,setBusca]=useState('');const [page,setPage]=useState(0);const [menuOpen,setMenuOpen]=useState(false);
 const [historico,setHistorico]=useState<Movimento[]>([]);const [movLoading,setMovLoading]=useState(false);
 const [modoSeguro,setModoSeguro]=useState<boolean|null>(null);
 const [erroModo,setErroModo]=useState('');
 const [revisaoModo,setRevisaoModo]=useState(0);
 const [autenticado,setAutenticado]=useState(api.pdfAutenticado());
 const [perfis,setPerfis]=useState<string[]>(api.pdfPerfis());
 const [usuarioAtual,setUsuarioAtual]=useState('');
 const [loginUsuario,setLoginUsuario]=useState('');
 const [loginSenha,setLoginSenha]=useState('');
 const [loginBusy,setLoginBusy]=useState(false);
 const [loginErro,setLoginErro]=useState('');
 const [expiraEm,setExpiraEm]=useState<number|null>(null);
 useEffect(()=>{
   let ativo=true;setModoSeguro(null);setErroModo('');
   api.pdfSecurityMode().then(modo=>{if(ativo)setModoSeguro(modo);})
     .catch(e=>{if(ativo)setErroModo((e as Error).message);});
   return ()=>{ativo=false;};
 },[revisaoModo]);
 const login=async()=>{
   setLoginBusy(true);setLoginErro('');
   try{
     const resposta=await api.pdfLogin({usuario:loginUsuario,senha:loginSenha});
     setPerfis(resposta.roles);setUsuarioAtual(loginUsuario);setLoginSenha('');setAutenticado(true);
     setExpiraEm(Date.now()+resposta.expiresIn*1000);
   }catch(e){setLoginErro((e as Error).message);}
   finally{setLoginBusy(false);}
 };
 const sair=()=>{
   api.pdfSair();setAutenticado(false);setPerfis([]);setUsuarioAtual('');setExpiraEm(null);
   setLivros([]);setTotal(0);setModal(null);setError('');setMenuOpen(false);
 };
 const podeAdministrar=modoSeguro!==true||perfis.includes('BIBLIOTECARIO');
 useEffect(()=>{
   if(modoSeguro!==true||expiraEm===null)return;
   const timer=window.setTimeout(()=>sair(),Math.max(0,expiraEm-Date.now()));
   return ()=>window.clearTimeout(timer);
 },[modoSeguro,expiraEm]);
 useEffect(()=>{
   const expirou=()=>sair();
   window.addEventListener('livrostock:auth-expired',expirou);
   return ()=>window.removeEventListener('livrostock:auth-expired',expirou);
 },[]);


 const [tema,setTema]=useState<Tema>(()=>{
   try {const salvo=window.localStorage.getItem('livrostock.tema');return temas.includes(salvo as Tema)?salvo as Tema:'light';}
   catch{return 'light';}
 });
 useEffect(()=>{
   document.documentElement.dataset.theme=tema;
   document.documentElement.style.colorScheme=tema==='dark'?'dark':'light';
   try{window.localStorage.setItem('livrostock.tema',tema);}catch{/* armazenamento indisponível */}
 },[tema]);

 const load=useCallback(async()=>{setLoading(true);setError('');try{const p=await api.livros(0,1000);setLivros(p.content);setTotal(p.totalElements);setApiOnline(true);}catch(e){setApiOnline(false);setError((e as Error).message);}finally{setLoading(false)}},[]);
 useEffect(()=>{if(modoSeguro!==null&&(!modoSeguro||autenticado))void load();},[load,modoSeguro,autenticado]);
 const notify=(msg:string)=>{setToast(msg);window.setTimeout(()=>setToast(''),4500)};
 const openHistory=async(livro:Livro)=>{setModal({type:'historico',livro});setMovLoading(true);setHistorico([]);try{const data=await api.historico(livro.id);setHistorico(data.content);}catch(e){notify((e as Error).message)}finally{setMovLoading(false)}};
 const filtered=useMemo(()=>livros.filter(l=>(`${l.titulo} ${l.autor} ${l.isbn} ${l.categoria}`).toLocaleLowerCase('pt-BR').includes(busca.toLocaleLowerCase('pt-BR'))),[livros,busca]);
 const low=livros.filter(l=>l.estoqueBaixo);const qty=livros.reduce((a,l)=>a+l.quantidade,0);const value=livros.reduce((a,l)=>a+l.quantidade*l.preco,0);
 const rows=view==='alertas'?filtered.filter(l=>l.estoqueBaixo):filtered;
 const pageSize=8;const paged=rows.slice(page*pageSize,(page+1)*pageSize);const pages=Math.max(1,Math.ceil(rows.length/pageSize));
 const switchView=(next:View)=>{setView(next);setPage(0);setBusca('');setMenuOpen(false)};
 const save=async(payload:LivroPayload)=>{if(modal?.type==='editar'){await api.atualizar(modal.livro.id,payload);notify('Livro atualizado com sucesso.')}else{await api.criar(payload);notify('Livro cadastrado com sucesso.')}setModal(null);await load()};
 const move=async(tipo:'ENTRADA'|'SAIDA',quantidade:number,observacao:string)=>{if(modal?.type!=='movimentar')return;await api.movimentar(modal.livro.id,{tipo,quantidade,observacao});notify(`${tipo==='ENTRADA'?'Entrada':'Saída'} registrada com sucesso.`);setModal(null);await load()};
 const disable=async()=>{if(modal?.type!=='excluir')return;await api.desativar(modal.livro.id);setModal(null);notify('Livro desativado com sucesso.');await load()};
 const nav=[{id:'dashboard' as View,icon:LayoutDashboard,label:'Visão geral'},{id:'livros' as View,icon:LibraryBig,label:'Catálogo de livros'},{id:'movimentacoes' as View,icon:ArrowLeft,label:'Movimentações'},{id:'digital' as View,icon:FileText,label:'Biblioteca Digital'},...(modoSeguro&&podeAdministrar?[{id:'auditoria' as View,icon:ShieldCheck,label:'Auditoria'},{id:'usuarios' as View,icon:ShieldCheck,label:'Usuários'}]:[]),{id:'alertas' as View,icon:CircleAlert,label:'Estoque baixo',count:low.length}];

 if(modoSeguro===null|| (modoSeguro&&!autenticado)){
   return <div className="auth-screen"><div className="auth-card">
     <div className="auth-brand"><BookOpen size={30}/><div><h1>livrostock.</h1><p>Gestão de Acervo · Acesso seguro</p></div></div>
     <div className="auth-theme"><label htmlFor="tema-login">Tema</label><select id="tema-login" value={tema} onChange={e=>setTema(e.target.value as Tema)}>
       <option value="light">Light</option><option value="moderado">Moderado</option><option value="dark">Dark</option>
     </select></div>
     {modoSeguro===null?<div className="loading-state"><RefreshCw size={18} className="spin"/> {erroModo||'Verificando acesso à API...'}</div>:
       <form className="auth-form" onSubmit={e=>{e.preventDefault();void login();}}>
         <h2>Entrar no LivroStock</h2><p>Use o usuário e a senha configurados no backend.</p>
         <label>Usuário<input required autoComplete="username" value={loginUsuario} onChange={e=>setLoginUsuario(e.target.value)}/></label>
         <label>Senha<input required type="password" autoComplete="current-password" value={loginSenha} onChange={e=>setLoginSenha(e.target.value)}/></label>
         {loginErro&&<p className="digital-error" role="alert">{loginErro}</p>}
         <button type="submit" className="primary-btn" disabled={loginBusy}>{loginBusy?'Autenticando...':'Acessar sistema'}</button>
       </form>}
     {erroModo&&<button className="secondary-btn" type="button" onClick={()=>setRevisaoModo(n=>n+1)}>Tentar reconectar</button>}
   </div></div>;
 }
 return <div className="app-shell">
 <aside className={'sidebar '+(menuOpen?'show':'')}><div className="brand"><div className="brand-icon"><BookOpen size={24}/></div><div><strong>livro<span>stock.</span></strong><small>GESTÃO DE ACERVO</small></div></div><div className="nav-label">WORKSPACE</div><nav>{nav.map(n=><button key={n.id} className={'nav-link '+(view===n.id?'active':'')} onClick={()=>switchView(n.id)}><n.icon size={18}/><span>{n.label}</span>{n.count!==undefined&&n.count>0&&<em>{n.count}</em>}</button>)}</nav><div className="side-bottom"><div className="side-tip"><div className="tip-icon"><ShieldCheck size={18}/></div><strong>Estoque sob controle</strong><span>Organize seu acervo e acompanhe cada movimentação.</span></div><div className="side-footer"><div className="avatar">LS</div><div><b>{modoSeguro?usuarioAtual:'LivroStock'}</b><span>{podeAdministrar?'Bibliotecário':'Leitor'}</span></div><MoreHorizontal size={18}/></div></div></aside>
 {menuOpen&&<button className="mobile-overlay" onClick={()=>setMenuOpen(false)} aria-label="Fechar menu"/>}
 <div className="main-wrap"><header className="topbar"><div className="topbar-left"><button className="mobile-menu" onClick={()=>setMenuOpen(true)} aria-label="Abrir menu"><Menu size={22}/></button><span className="breadcrumb">Workspace</span><ChevronRight size={14}/><strong>{view==='dashboard'?'Visão geral':view==='livros'?'Catálogo':view==='alertas'?'Estoque baixo':view==='digital'?'Biblioteca Digital':view==='auditoria'?'Auditoria':view==='usuarios'?'Usuários':'Movimentações'}</strong></div><div className="topbar-actions">{modoSeguro&&<button className="topbar-logout" type="button" onClick={sair} title="Encerrar sessão">Sair</button>}<div className="theme-control"><label htmlFor="livrostock-tema">Tema</label><select id="livrostock-tema" aria-label="Tema visual" value={tema} onChange={e=>setTema(e.target.value as Tema)}><option value="light">Light ☀️</option><option value="moderado">Moderado ◐</option><option value="dark">Dark 🌙</option></select></div><div className={'api-status '+(apiOnline?'online':'offline')}><span className="status-dot"/>{apiOnline===null?'Conectando':apiOnline?'API conectada':'API offline'}</div><button className="top-icon" onClick={()=>void load()} title="Atualizar dados"><RefreshCw size={17}/></button><div className="avatar small">LS</div></div></header>
 <main className="content"><div className="heading-row"><div><div className="eyebrow"><span className="eyebrow-line"/> CONTROLE DE INVENTÁRIO</div><h1>{view==='dashboard'?'Visão geral':view==='livros'?'Catálogo de livros':view==='alertas'?'Estoque baixo':view==='digital'?'Biblioteca Digital':view==='auditoria'?'Auditoria':view==='usuarios'?'Usuários':'Movimentações'}</h1><p className="subtitle">{view==='dashboard'?'Tudo o que você precisa saber sobre seu acervo, em um só lugar.':view==='livros'?'Gerencie títulos, informações e disponibilidade do seu catálogo.':view==='alertas'?'Livros que atingiram o limite mínimo de estoque.':view==='digital'?'Associe, visualize e baixe exemplares PDF dos livros cadastrados.':view==='auditoria'?'Consulte operações autenticadas e seus registros de rastreabilidade.':view==='usuarios'?'Administre perfis, senhas e acesso ao acervo.':'Registre entradas e saídas e consulte o histórico de cada livro.'}</p></div>{podeAdministrar&&view!=='auditoria'&&view!=='usuarios'&&<button className="primary-btn" onClick={()=>setModal({type:'novo'})}><Plus size={17}/> Novo livro</button>}</div>
 {error&&<div className="error-alert"><CircleAlert size={19}/><span>{error}</span><button onClick={()=>void load()}>Tentar novamente</button></div>}
 {view==='digital'&&<BibliotecaDigital livros={livros} onLogout={sair}/>}
 {view==='auditoria'&&podeAdministrar&&modoSeguro&&<AuditoriaPanel/>}
 {view==='usuarios'&&podeAdministrar&&modoSeguro&&<UsuariosPanel/>}
 {view==='dashboard'&&<><section className="metric-grid"><Metric icon={<LibraryBig size={20}/>} label="Títulos cadastrados" value={total.toLocaleString('pt-BR')} sub="Livros ativos no catálogo" variant="blue"/><Metric icon={<Boxes size={20}/>} label="Unidades em estoque" value={qty.toLocaleString('pt-BR')} sub="Exemplares disponíveis" variant="orange"/><Metric icon={<Wallet size={20}/>} label="Valor de estoque" value={currency(value)} sub="Baseado no preço cadastrado" variant="green"/><Metric icon={<CircleAlert size={20}/>} label="Estoque baixo" value={low.length.toString()} sub={low.length===0?'Nenhum alerta no momento':'Títulos exigem atenção'} variant="red"/></section><section className="dashboard-grid"><div className="panel welcome-panel"><div className="welcome-copy"><span className="welcome-kicker"><span/> SEU ACERVO EM DIA</span><h2>Uma estante organizada,<br/><em>um negócio mais eficiente.</em></h2><p>Cadastre novos títulos, acompanhe quantidades e mantenha o controle das entradas e saídas.</p><button className="light-btn" onClick={()=>switchView('livros')}>Explorar catálogo <ArrowRight size={17}/></button></div><div className="welcome-art"><div className="art-circle one"/><div className="art-circle two"/><div className="art-book a"/><div className="art-book b"/><div className="art-book c"/><div className="art-book d"/><div className="art-shelf"/></div></div><div className="panel attention-panel"><div className="panel-title"><div><span className="panel-mini">ATENÇÃO AO ESTOQUE</span><h3>Precisam de reposição</h3></div><div className="alert-icon"><CircleAlert size={20}/></div></div>{low.length===0?<div className="all-good"><div><Check size={24}/></div><b>Tudo em ordem!</b><p>Nenhum título com estoque baixo.</p></div>:<div className="attention-items">{low.slice(0,3).map(l=><button className="attention-item" key={l.id} onClick={()=>setModal({type:'movimentar',livro:l,modo:'ENTRADA'})}><Cover livro={l}/><div><strong>{l.titulo}</strong><span>{l.quantidade} un. · mín. {l.estoqueMinimo}</span></div><ArrowUpRight size={18}/></button>)}<button className="link-btn" onClick={()=>switchView('alertas')}>Ver todos os alertas <ArrowRight size={15}/></button></div>}</div></section></>}
 {view==='movimentacoes'&&<div className="movement-instruction"><div className="movement-icon"><Activity size={22}/></div><div><strong>Movimentações por livro</strong><p>Escolha um título na lista e clique em <b>Histórico</b> para consultar seus registros, ou use Entrada/Saída para atualizar o saldo.</p></div></div>}
 {(view==='dashboard'||view==='livros'||view==='alertas'||view==='movimentacoes')&&<section className="panel table-panel"><div className="table-top"><div><span className="panel-mini">{view==='dashboard'?'SEUS LIVROS':view==='alertas'?'REPOSIÇÃO':'GERENCIAMENTO'}</span><h3>{view==='dashboard'?'Catálogo recente':view==='alertas'?'Itens com estoque baixo':view==='movimentacoes'?'Selecionar livro':'Todos os livros'}</h3></div><button className="text-link" onClick={()=>switchView('livros')}>{view==='dashboard'?'Ver catálogo completo':''}{view==='dashboard'&&<ArrowRight size={16}/>}</button></div><div className="table-tools"><div className="search-field"><Search size={18}/><input value={busca} onChange={e=>{setBusca(e.target.value);setPage(0)}} placeholder="Buscar por título, autor, ISBN..." aria-label="Buscar livros"/></div><div className="count-pill"><Filter size={15}/>{rows.length} {rows.length===1?'título':'títulos'}</div></div><div className="table-scroll"><table><thead><tr><th>LIVRO</th><th>CATEGORIA</th><th>PREÇO</th><th>ESTOQUE</th><th>STATUS</th><th className="action-th">AÇÕES</th></tr></thead><tbody>{paged.map(l=><tr key={l.id}><td><div className="book-cell"><Cover livro={l}/><div><strong>{l.titulo}</strong><small>{l.autor}</small></div></div></td><td><span className="category-pill">{l.categoria}</span></td><td className="price-cell">{l.somenteDigital?'—':currency(l.preco)}</td><td><span className="stock-num">{l.somenteDigital?'—':l.quantidade}</span> {!l.somenteDigital&&<span className="stock-unit">un.</span>}</td><td><Pill low={l.estoqueBaixo} digital={l.somenteDigital}/></td><td><div className="row-actions">{podeAdministrar&&!l.somenteDigital&&<><IconButton title="Entrada de estoque" onClick={()=>setModal({type:'movimentar',livro:l,modo:'ENTRADA'})}><ArrowDownLeft size={17}/></IconButton><IconButton title="Saída de estoque" onClick={()=>setModal({type:'movimentar',livro:l,modo:'SAIDA'})}><ArrowUpRight size={17}/></IconButton></>}<IconButton title="Histórico de movimentações" onClick={()=>void openHistory(l)}><Clock3 size={17}/></IconButton>{podeAdministrar&&!l.somenteDigital&&<><IconButton title="Editar livro" onClick={()=>setModal({type:'editar',livro:l})}><Edit3 size={16}/></IconButton></>}{podeAdministrar&&<IconButton title="Desativar livro" onClick={()=>setModal({type:'excluir',livro:l})}><Trash2 size={16}/></IconButton>}</div></td></tr>)}</tbody></table></div>{!loading&&rows.length===0&&<Blank title={busca?'Nenhum resultado encontrado':view==='alertas'?'Nenhum estoque baixo':'Seu catálogo está vazio'} description={busca?'Experimente buscar outro título ou autor.':view==='alertas'?'Os títulos estão acima do limite mínimo.':'Cadastre seu primeiro livro para começar a gerenciar o estoque.'}>{podeAdministrar&&!busca&&view!=='alertas'&&<button className="primary-btn" onClick={()=>setModal({type:'novo'})}><Plus size={16}/> Cadastrar livro</button>}</Blank>}{loading&&<div className="loading-state"><RefreshCw size={19} className="spin"/> Carregando catálogo...</div>}<div className="table-footer"><span>Mostrando <b>{rows.length===0?0:page*pageSize+1}–{Math.min((page+1)*pageSize,rows.length)}</b> de <b>{rows.length}</b> livros</span><div className="pagination"><button disabled={page===0} onClick={()=>setPage(p=>p-1)} aria-label="Página anterior"><ChevronLeft size={17}/></button><span>{page+1} / {pages}</span><button disabled={page>=pages-1} onClick={()=>setPage(p=>p+1)} aria-label="Próxima página"><ChevronRight size={17}/></button></div></div></section>}
 <footer className="app-footer"><span>© 2026 LivroStock. Gestão simples, acervo organizado.</span><span>Frontend v0.7 • Spring Boot API</span></footer>
 </main></div>
 {toast&&<div className="toast"><Check size={18}/>{toast}<button onClick={()=>setToast('')} aria-label="Fechar"><X size={16}/></button></div>}
 {modal&&<ModalView modal={modal} onClose={()=>setModal(null)} onSave={save} onMove={move} onDisable={disable} movimentos={historico} movLoading={movLoading}/>}
 </div>
}
function Metric({icon,label,value,sub,variant}:{icon:ReactNode;label:string;value:string;sub:string;variant:string}){return <div className="metric-card"><div className={'metric-icon '+variant}>{icon}</div><div className="metric-label">{label}</div><div className="metric-value">{value}</div><div className="metric-sub"><TrendingUp size={13}/>{sub}</div></div>}
const blankPayload:LivroPayload={titulo:'',autor:'',isbn:'',categoria:'',preco:0,estoqueMinimo:5};
function ModalView({modal,onClose,onSave,onMove,onDisable,movimentos,movLoading}:{modal:NonNullable<Modal>;onClose:()=>void;onSave:(payload:LivroPayload)=>Promise<void>;onMove:(tipo:'ENTRADA'|'SAIDA',quantidade:number,observacao:string)=>Promise<void>;onDisable:()=>Promise<void>;movimentos:Movimento[];movLoading:boolean}){
 const [form,setForm]=useState<LivroPayload>(modal.type==='editar'?{titulo:modal.livro.titulo,autor:modal.livro.autor,isbn:modal.livro.isbn??'',categoria:modal.livro.categoria,preco:modal.livro.preco,estoqueMinimo:modal.livro.estoqueMinimo}:blankPayload);const [quantidade,setQuantidade]=useState(1);const [observacao,setObservacao]=useState('');const [busy,setBusy]=useState(false);const [err,setErr]=useState('');
 const run=async(task:()=>Promise<void>)=>{setBusy(true);setErr('');try{await task()}catch(e){setErr((e as Error).message)}finally{setBusy(false)}};
 const title=modal.type==='novo'?'Cadastrar novo livro':modal.type==='editar'?'Editar informações':modal.type==='movimentar'?modal.modo==='ENTRADA'?'Entrada de estoque':'Saída de estoque':modal.type==='historico'?'Histórico de movimentações':'Desativar livro';
 return <div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)onClose()}}><div className="modal" role="dialog" aria-modal="true" aria-label={title}><div className="modal-header"><div><span className="panel-mini">LIVROSTOCK · GESTÃO</span><h2>{title}</h2></div><IconButton title="Fechar" onClick={onClose}><X size={20}/></IconButton></div>
 {(modal.type==='novo'||modal.type==='editar')&&<form onSubmit={e=>{e.preventDefault();void run(()=>onSave(form))}}><div className="form-body"><p className="form-help">Preencha os dados bibliográficos e comerciais do título.</p><label>Título do livro<input required maxLength={200} value={form.titulo} onChange={e=>setForm({...form,titulo:e.target.value})} placeholder="Ex.: Clean Code"/></label><label>Autor<input required maxLength={160} value={form.autor} onChange={e=>setForm({...form,autor:e.target.value})} placeholder="Nome do autor"/></label><div className="form-row"><label>ISBN<input required pattern="[0-9Xx-]{10,20}" title="10 a 20 caracteres: dígitos, hífens ou X" disabled={modal.type==='editar'} value={form.isbn??''} onChange={e=>setForm({...form,isbn:e.target.value})} placeholder="9780132350884"/></label><label>Categoria<input required maxLength={100} value={form.categoria} onChange={e=>setForm({...form,categoria:e.target.value})} placeholder="Tecnologia"/></label></div><div className="form-row"><label>Preço (R$)<input required min={0} step="0.01" type="number" value={form.preco} onChange={e=>setForm({...form,preco:Number(e.target.value)})}/></label><label>Estoque mínimo<input required min={0} step={1} type="number" value={form.estoqueMinimo} onChange={e=>setForm({...form,estoqueMinimo:Number(e.target.value)})}/></label></div>{modal.type==='editar'&&<div className="form-note"><CircleAlert size={15}/> O ISBN é imutável após o cadastro. Para ajustar quantidades, use as movimentações.</div>}</div>{err&&<p className="modal-error">{err}</p>}<div className="modal-footer"><button type="button" className="secondary-btn" onClick={onClose}>Cancelar</button><button disabled={busy} className="primary-btn" type="submit">{busy?'Salvando...':modal.type==='novo'?'Cadastrar livro':'Salvar alterações'} <ArrowRight size={16}/></button></div></form>}
 {modal.type==='movimentar'&&<form onSubmit={e=>{e.preventDefault();if(!Number.isInteger(quantidade)||quantidade<1){setErr('Informe uma quantidade inteira positiva.');return}void run(()=>onMove(modal.modo,quantidade,observacao))}}><div className="form-body"><div className="selected-book"><Cover livro={modal.livro}/><div><strong>{modal.livro.titulo}</strong><span>Saldo atual: {modal.livro.quantidade} unidades</span></div></div><div className={'move-banner '+(modal.modo==='ENTRADA'?'entrance':'exit')}>{modal.modo==='ENTRADA'?<ArrowDownLeft size={19}/>:<ArrowUpRight size={19}/>} {modal.modo==='ENTRADA'?'Adicionar exemplares ao estoque':'Retirar exemplares do estoque'}</div><label>Quantidade<input required min={1} step={1} type="number" value={quantidade} onChange={e=>setQuantidade(Number(e.target.value))}/></label><label>Observação<textarea rows={3} maxLength={250} placeholder="Ex.: Compra de fornecedor ou venda de balcão" value={observacao} onChange={e=>setObservacao(e.target.value)}/></label><div className="balance-preview">Saldo após a operação <strong>{modal.livro.quantidade+(modal.modo==='ENTRADA'?quantidade:-quantidade)} un.</strong></div>{modal.modo==='SAIDA'&&quantidade>modal.livro.quantidade&&<p className="modal-error">Estoque insuficiente para esta saída.</p>}</div>{err&&<p className="modal-error">{err}</p>}<div className="modal-footer"><button type="button" className="secondary-btn" onClick={onClose}>Cancelar</button><button className="primary-btn" disabled={busy||(modal.modo==='SAIDA'&&quantidade>modal.livro.quantidade)} type="submit">{busy?'Registrando...':'Confirmar movimentação'} <Check size={16}/></button></div></form>}
 {modal.type==='historico'&&<><div className="form-body"><div className="selected-book"><Cover livro={modal.livro}/><div><strong>{modal.livro.titulo}</strong><span>Saldo atual: {modal.livro.quantidade} unidades</span></div></div>{movLoading?<div className="loading-state"><RefreshCw className="spin" size={17}/> Carregando...</div>:movimentos.length?<div className="history-list">{movimentos.map(m=><div className="history-item" key={m.id}><div className={'history-icon '+(m.tipo==='ENTRADA'?'in':'out')}>{m.tipo==='ENTRADA'?<ArrowDownLeft size={18}/>:<ArrowUpRight size={18}/>}</div><div><b>{m.tipo==='ENTRADA'?'Entrada':'Saída'} de {m.quantidade} un.</b><p>{m.observacao||'Sem observação'}</p><small>{date(m.criadoEm)} · Saldo após: {m.saldoApos}</small></div></div>)}</div>:<Blank title="Nenhuma movimentação" description="O histórico deste livro está vazio."/>}</div><div className="modal-footer"><button className="secondary-btn" onClick={onClose}>Fechar</button></div></>}
 {modal.type==='excluir'&&<><div className="form-body"><div className="delete-icon"><Trash2 size={24}/></div><p>Deseja desativar <b>{modal.livro.titulo}</b>? O título deixará de aparecer no catálogo ativo. Seu histórico permanecerá no banco de dados.</p><p className="form-note">Esta operação não remove permanentemente os dados.</p></div>{err&&<p className="modal-error">{err}</p>}<div className="modal-footer"><button className="secondary-btn" onClick={onClose}>Cancelar</button><button className="danger-btn" disabled={busy} onClick={()=>void run(onDisable)}>{busy?'Desativando...':'Desativar livro'}</button></div></>}
 </div></div>
}
export default App;
