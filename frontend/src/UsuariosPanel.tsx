import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ChevronLeft, ChevronRight, LockKeyhole, RefreshCw, ShieldCheck, UserPlus } from 'lucide-react';
import { api, type Page, type PerfilUsuario, type UsuarioResumo } from './api';

const descricaoPerfil = (perfil:PerfilUsuario) =>
  perfil==='BIBLIOTECARIO'?'Bibliotecário':'Leitor';

export default function UsuariosPanel(){
  const [pagina,setPagina]=useState(0);
  const [dados,setDados]=useState<Page<UsuarioResumo>|null>(null);
  const [nome,setNome]=useState('');
  const [senha,setSenha]=useState('');
  const [perfil,setPerfil]=useState<PerfilUsuario>('LEITOR');
  const [alterandoSenha,setAlterandoSenha]=useState<number|null>(null);
  const [novaSenha,setNovaSenha]=useState('');
  const [ocupado,setOcupado]=useState(false);
  const [carregando,setCarregando]=useState(true);
  const [erro,setErro]=useState('');
  const [sucesso,setSucesso]=useState('');

  const carregar=useCallback(async()=>{
    setCarregando(true);setErro('');
    try {setDados(await api.usuarios(pagina));}
    catch(e){setErro((e as Error).message);}
    finally{setCarregando(false);}
  },[pagina]);
  useEffect(()=>{void carregar();},[carregar]);

  const executar=async(acao:()=>Promise<unknown>,mensagem:string)=>{
    setOcupado(true);setErro('');setSucesso('');
    try {await acao();setSucesso(mensagem);await carregar();return true;}
    catch(e){setErro((e as Error).message);return false;}
    finally{setOcupado(false);}
  };

  const cadastrar=async(e:FormEvent<HTMLFormElement>)=>{
    e.preventDefault();
    const ok=await executar(()=>api.cadastrarUsuario({nomeUsuario:nome,senha,perfil}), 'Conta criada com sucesso.');
    if(ok){setNome('');setSenha('');setPerfil('LEITOR');setPagina(0);}
  };

  const mudarPerfil=async(u:UsuarioResumo,novo:PerfilUsuario)=>{
    if(novo===u.perfil)return;
    if(!window.confirm(`Alterar o perfil de ${u.nomeUsuario} para ${descricaoPerfil(novo)}? Os tokens existentes serão invalidados.`))return;
    await executar(()=>api.alterarPerfilUsuario(u.id,novo),'Perfil atualizado.');
  };

  const mudarStatus=async(u:UsuarioResumo)=>{
    if(!window.confirm(`${u.ativo?'Desativar':'Ativar'} a conta ${u.nomeUsuario}?`))return;
    await executar(()=>api.alterarStatusUsuario(u.id,!u.ativo),u.ativo?'Conta desativada.':'Conta ativada.');
  };

  const mudarSenha=async(e:FormEvent<HTMLFormElement>,id:number)=>{
    e.preventDefault();
    const ok=await executar(()=>api.alterarSenhaUsuario(id,novaSenha),'Senha atualizada. Tokens anteriores invalidados.');
    if(ok){setAlterandoSenha(null);setNovaSenha('');}
  };

  return <section className="users-layout">
    <div className="panel users-panel">
      <div className="audit-header"><div className="audit-title"><UserPlus size={22}/>
        <div><span className="panel-mini">ADMINISTRAÇÃO · SOMENTE BIBLIOTECÁRIO</span>
          <h3>Cadastrar usuário</h3><p>Senhas são armazenadas como hash BCrypt.</p></div>
      </div></div>
      <form className="users-form" onSubmit={e=>void cadastrar(e)}>
        <label>Usuário<input required minLength={3} maxLength={80} pattern="[a-zA-Z0-9._-]+" autoComplete="off"
          value={nome} onChange={e=>setNome(e.target.value)} placeholder="ex.: maria.silva"/></label>
        <label>Senha inicial<input required type="password" minLength={12} maxLength={200} autoComplete="new-password"
          value={senha} onChange={e=>setSenha(e.target.value)} placeholder="Mínimo de 12 caracteres"/></label>
        <label>Perfil<select value={perfil} onChange={e=>setPerfil(e.target.value as PerfilUsuario)}>
          <option value="LEITOR">Leitor</option><option value="BIBLIOTECARIO">Bibliotecário</option>
        </select></label>
        <button className="primary-btn" disabled={ocupado} type="submit"><UserPlus size={16}/>
          {ocupado?'Salvando...':'Criar usuário'}</button>
      </form>
    </div>
    <div className="panel users-panel">
      <div className="audit-header"><div className="audit-title"><ShieldCheck size={22}/>
        <div><span className="panel-mini">CONTROLE DE ACESSO</span><h3>Contas cadastradas</h3>
          <p>Alterações de senha, perfil e status invalidam os JWTs anteriores.</p></div></div>
        <button className="secondary-btn" disabled={carregando||ocupado} type="button" onClick={()=>void carregar()}>
          <RefreshCw size={15}/> Atualizar</button>
      </div>
      {erro&&<p className="digital-error users-message" role="alert">{erro}</p>}
      {sucesso&&<p className="digital-success users-message" role="status">{sucesso}</p>}
      {carregando?<div className="loading-state"><RefreshCw size={16} className="spin"/> Consultando usuários...</div>:
        !dados?.content.length?<div className="digital-empty">Nenhum usuário encontrado.</div>:
        <div className="table-scroll"><table><thead><tr>
          <th>USUÁRIO</th><th>PERFIL</th><th>STATUS</th><th>AÇÕES</th></tr></thead>
          <tbody>{dados.content.map(usuario=><tr key={usuario.id}>
            <td><strong>{usuario.nomeUsuario}</strong></td>
            <td><select className="users-inline-select" aria-label={`Perfil de ${usuario.nomeUsuario}`}
              disabled={ocupado} value={usuario.perfil}
              onChange={e=>void mudarPerfil(usuario,e.target.value as PerfilUsuario)}>
              <option value="LEITOR">Leitor</option><option value="BIBLIOTECARIO">Bibliotecário</option>
            </select></td>
            <td><span className={usuario.ativo?'pill ok':'pill low'}>{usuario.ativo?'Ativo':'Inativo'}</span></td>
            <td><div className="users-actions">
              <button type="button" className="secondary-btn" disabled={ocupado}
                onClick={()=>{setAlterandoSenha(usuario.id);setNovaSenha('');}}>
                <LockKeyhole size={14}/> Senha</button>
              <button type="button" className="secondary-btn" disabled={ocupado}
                onClick={()=>void mudarStatus(usuario)}>{usuario.ativo?'Desativar':'Ativar'}</button>
            </div></td>
          </tr>)}</tbody></table></div>}
      {alterandoSenha!==null&&<form className="users-reset" onSubmit={e=>void mudarSenha(e,alterandoSenha)}>
        <label>Nova senha (mínimo 12 caracteres)<input type="password" required minLength={12} maxLength={200}
          autoComplete="new-password" value={novaSenha} onChange={e=>setNovaSenha(e.target.value)}/></label>
        <button className="primary-btn" disabled={ocupado}>Salvar nova senha</button>
        <button className="secondary-btn" type="button" onClick={()=>{setAlterandoSenha(null);setNovaSenha('');}}>Cancelar</button>
      </form>}
      <div className="table-footer"><span>{dados?.totalElements??0} contas</span>
        <div className="pagination"><button type="button" disabled={pagina===0||carregando} onClick={()=>setPagina(p=>p-1)}
          aria-label="Página anterior"><ChevronLeft size={17}/></button>
          <span>{pagina+1} / {Math.max(1,dados?.totalPages??1)}</span>
          <button type="button" disabled={carregando||!dados||dados.last} onClick={()=>setPagina(p=>p+1)}
            aria-label="Próxima página"><ChevronRight size={17}/></button></div>
      </div>
    </div>
  </section>;
}
