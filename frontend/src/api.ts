export type Livro = {id:number;titulo:string;autor:string;isbn:string;categoria:string;preco:number;quantidade:number;estoqueMinimo:number;estoqueBaixo:boolean;ativo:boolean};
export type LivroPayload = Pick<Livro,'titulo'|'autor'|'isbn'|'categoria'|'preco'|'estoqueMinimo'>;
export type Movimento = {id:number;livroId:number;tipo:'ENTRADA'|'SAIDA';quantidade:number;saldoApos:number;observacao:string|null;criadoEm:string};
export type Page<T>={content:T[];totalElements:number;totalPages:number;number:number;size:number;first:boolean;last:boolean};
const BASE=(import.meta.env.VITE_API_URL as string|undefined)?.replace(/\/$/,'')??'';
async function request<T>(path:string,opts:RequestInit={}):Promise<T>{
  let response:Response;
  try {response=await fetch(BASE+path,{...opts,headers:{'Content-Type':'application/json',...opts.headers}});}catch {throw new Error('Não foi possível conectar à API. Verifique se o Spring Boot está ativo na porta configurada (padrão 8081).');}
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
  health:()=>request<{status:string}>('/actuator/health')
};
