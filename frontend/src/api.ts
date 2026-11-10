export type Livro = {id:number;titulo:string;autor:string;isbn:string;categoria:string;preco:number;quantidade:number;estoqueMinimo:number;estoqueBaixo:boolean;ativo:boolean};
export type LivroPayload = Pick<Livro,'titulo'|'autor'|'isbn'|'categoria'|'preco'|'estoqueMinimo'>;
export type Movimento = {id:number;livroId:number;tipo:'ENTRADA'|'SAIDA';quantidade:number;saldoApos:number;observacao:string|null;criadoEm:string};
export type ExemplarDigital = {id:number;livroId:number;nomeOriginal:string;tipoMime:string;tamanhoBytes:number;sha256:string;criadoEm:string};
export type Page<T>={content:T[];totalElements:number;totalPages:number;number:number;size:number;first:boolean;last:boolean};
const BASE=(import.meta.env.VITE_API_URL as string|undefined)?.replace(/\/$/,'')??'';
let tokenPdf:string|null=null;
let perfisPdf:string[]=[];
const headersPdf=()=>tokenPdf?{'Authorization':`Bearer ${tokenPdf}`}:{};
export type LoginPdf={usuario:string;senha:string};
export type TokenPdf={accessToken:string;tokenType:'Bearer';expiresIn:number;roles:string[]};

async function request<T>(path:string,opts:RequestInit={}):Promise<T>{
  let response:Response;
  try {response=await fetch(BASE+path,{...opts,headers:{'Content-Type':'application/json',...headersPdf(),...opts.headers}});}catch {throw new Error('Não foi possível conectar à API. Verifique se o Spring Boot está ativo na porta configurada (padrão 8081).');}
  if(!response.ok){let error:unknown;try{error=await response.json();}catch{};const message=error && typeof error==='object'&&'erro' in error?String(error.erro):`Erro HTTP ${response.status}`;throw new Error(message);}
  if(response.status===204)return undefined as T;
  return response.json() as Promise<T>;
}
export const api={
  livros:(page=0,size=100,busca='')=>request<Page<Livro>>(`/api/v1/livros?page=${page}&size=${size}&sort=titulo,asc${busca?`&busca=${encodeURIComponent(busca)}`:''}`),
  baixos:(page=0,size=100)=>request<Page<Livro>>(`/api/v1/livros/estoque-baixo?page=${page}&size=${size}`),
  livro:(id:number)=>request<Livro>(`/api/v1/livros/${id}`),
  criar:(body:LivroPayload)=>request<Livro>('/api/v1/livros',{method:'POST',body:JSON.stringify(body)}),
  atualizar:(id:number,body:LivroPayload)=>request<Livro>(`/api/v1/livros/${id}`,{method:'PUT',body:JSON.stringify(body)}),
  desativar:(id:number)=>request<void>(`/api/v1/livros/${id}`,{method:'DELETE'}),
  movimentar:(id:number,body:{tipo:'ENTRADA'|'SAIDA';quantidade:number;observacao:string})=>request<Movimento>(`/api/v1/livros/${id}/movimentacoes`,{method:'POST',body:JSON.stringify(body)}),
  historico:(id:number,page=0)=>request<Page<Movimento>>(`/api/v1/livros/${id}/movimentacoes?page=${page}&size=100`),
  exemplares:(livroId:number)=>request<ExemplarDigital[]>(`/api/v1/livros/${livroId}/exemplares-digitais`),
  enviarExemplar:async(livroId:number,arquivo:File):Promise<ExemplarDigital>=>{
    const body=new FormData();
    body.append('arquivo',arquivo);
    let response:Response;
    try {response=await fetch(BASE+`/api/v1/livros/${livroId}/exemplares-digitais`,{method:'POST',body,headers:headersPdf()});}
    catch {throw new Error('Falha na conexão com a API durante o upload.');}
    if(!response.ok){
      let message=`Erro HTTP ${response.status}`;
      try {const error=await response.json() as {erro?:string};if(error.erro)message=error.erro;}catch{}
      throw new Error(message);
    }
    return response.json() as Promise<ExemplarDigital>;
  },
  pdfSecurityMode:async():Promise<boolean>=>{
    let response:Response;
    try {response=await fetch(BASE+'/api/v1/auth/mode',{cache:'no-store'});}
    catch {throw new Error('Não foi possível verificar o modo de acesso da biblioteca digital.');}
    if(response.status===404)return false;
    if(!response.ok)throw new Error(`Falha ao verificar autenticação: HTTP ${response.status}`);
    const data=await response.json() as {enabled?:boolean};
    return data.enabled===true;
  },
  pdfAutenticado:()=>tokenPdf!==null,
  pdfPerfis:()=>perfisPdf,
  pdfSair:()=>{tokenPdf=null;perfisPdf=[];},
  pdfLogin:async(credenciais:LoginPdf):Promise<TokenPdf>=>{
    const resposta=await request<TokenPdf>('/api/v1/auth/login',{method:'POST',body:JSON.stringify(credenciais)});
    tokenPdf=resposta.accessToken;
    perfisPdf=resposta.roles;
    return resposta;
  },
  pdfArquivo:async(livroId:number,exemplarId:number,download=false):Promise<Blob>=>{
    let response:Response;
    try {
      response=await fetch(BASE+`/api/v1/livros/${livroId}/exemplares-digitais/${exemplarId}/arquivo`+(download?'?download=true':''),
         {headers:headersPdf(),cache:'no-store'});
    }catch {throw new Error('Não foi possível carregar o PDF.');}
    if(!response.ok)throw new Error(response.status===401?'Sessão expirada ou acesso não autorizado.':response.status===403?'Você não tem permissão para este documento.':`Erro HTTP ${response.status} ao carregar PDF.`);
    return response.blob();
  },
  health:()=>request<{status:string}>('/actuator/health')
};
